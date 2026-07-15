[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Owner,
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$ExpectedCommit,
    [Parameter(Mandatory = $true)][string]$Title,
    [Parameter(Mandatory = $true)][string]$NotesPath,
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$ChecksumPath,
    [int]$TagPollAttempts = 20,
    [int]$TagPollSeconds = 30
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$ApiBase = "https://gitee.com/api/v5/repos/$Owner/$Repository"

function Normalize-Text([string]$Value) {
    return ($Value -replace "`r`n", "`n").TrimEnd()
}

function Assert-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing release input: $Path"
    }
}

function Invoke-GiteeJson(
    [string]$Uri,
    [string]$Method = "Get",
    [hashtable]$Body = $null
) {
    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $script:Headers
    }
    if ($null -ne $Body) { $parameters.Body = $Body }
    return Invoke-RestMethod @parameters
}

function Find-GiteeTag {
    for ($page = 1; $page -le 10; $page++) {
        $response = Invoke-GiteeJson "$ApiBase/tags?per_page=100&page=$page"
        $tags = if ($response -is [System.Array]) {
            @($response.GetEnumerator())
        } else {
            @($response)
        }
        $match = $tags | Where-Object { $_.name -eq $Tag } | Select-Object -First 1
        if ($null -ne $match) { return $match }
        if ($tags.Count -lt 100) { break }
    }
    return $null
}

function Get-GiteeRelease {
    try {
        return Invoke-GiteeJson "$ApiBase/releases/tags/$Tag"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 404) { return $null }
        throw
    }
}

function Get-GiteeAttachments([long]$ReleaseId) {
    $response = Invoke-GiteeJson "$ApiBase/releases/$ReleaseId/attach_files"
    if ($response -is [System.Array]) {
        return $response.GetEnumerator()
    }
    return $response
}

function Assert-GiteeAttachment(
    [pscustomobject]$Attachment,
    [string]$ExpectedPath,
    [long]$ReleaseId
) {
    $downloadPath = Join-Path $env:RUNNER_TEMP ("gitee-" + [guid]::NewGuid() + "-" + $Attachment.name)
    try {
        Invoke-WebRequest `
            -Uri "$ApiBase/releases/$ReleaseId/attach_files/$($Attachment.id)/download" `
            -Headers $script:Headers `
            -OutFile $downloadPath
        $expectedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ExpectedPath).Hash
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $downloadPath).Hash
        if ($actualHash -ne $expectedHash) {
            throw "Gitee attachment $($Attachment.name) conflicts with the built artifact."
        }
    } finally {
        Remove-Item -LiteralPath $downloadPath -Force -ErrorAction SilentlyContinue
    }
}

foreach ($path in @($NotesPath, $ApkPath, $ChecksumPath)) {
    Assert-File $path
}
if ([string]::IsNullOrWhiteSpace($env:GITEE_ACCESS_TOKEN)) {
    throw "GITEE_ACCESS_TOKEN is required."
}

$script:Headers = @{
    Authorization = "token $($env:GITEE_ACCESS_TOKEN)"
    Accept = "application/json"
}

$giteeTag = $null
for ($attempt = 1; $attempt -le $TagPollAttempts; $attempt++) {
    $giteeTag = Find-GiteeTag
    if ($null -ne $giteeTag) { break }
    if ($attempt -lt $TagPollAttempts) {
        Write-Output "Waiting for Gitee mirror tag $Tag ($attempt/$TagPollAttempts)."
        Start-Sleep -Seconds $TagPollSeconds
    }
}
if ($null -eq $giteeTag) {
    throw "Gitee mirror did not expose tag $Tag within the polling window."
}
$giteeCommit = [string]$giteeTag.commit.sha
if ($giteeCommit -ne $ExpectedCommit) {
    throw "Gitee tag $Tag resolves to $giteeCommit instead of $ExpectedCommit."
}

$expectedNotes = Normalize-Text (Get-Content -Raw -LiteralPath $NotesPath)
$release = Get-GiteeRelease
if ($null -eq $release) {
    $release = Invoke-GiteeJson "$ApiBase/releases" "Post" @{
        tag_name = $Tag
        target_commitish = $ExpectedCommit
        name = $Title
        body = $expectedNotes
        prerelease = "false"
    }
}
if ($null -eq $release -or $null -eq $release.id) { throw "Gitee Release creation returned no release id." }
if ([string]$release.tag_name -ne $Tag) { throw "Gitee Release tag mismatch." }
if ([string]$release.name -ne $Title) { throw "Gitee Release title mismatch." }
if (([string]$release.prerelease).ToLowerInvariant() -eq "true") {
    throw "Existing Gitee Release is marked as a prerelease."
}
if ((Normalize-Text ([string]$release.body)) -ne $expectedNotes) {
    throw "Existing Gitee Release notes conflict with the generated notes."
}

$releaseId = [long]$release.id
$attachments = Get-GiteeAttachments $releaseId
foreach ($path in @($ApkPath, $ChecksumPath)) {
    $name = Split-Path -Leaf $path
    $matches = @($attachments | Where-Object { $_.name -eq $name })
    if ($matches.Count -gt 1) { throw "Gitee Release contains duplicate attachment $name." }
    if ($matches.Count -eq 1) {
        Assert-GiteeAttachment $matches[0] $path $releaseId
        continue
    }
    Invoke-RestMethod `
        -Uri "$ApiBase/releases/$releaseId/attach_files" `
        -Method Post `
        -Headers $script:Headers `
        -Form @{ file = Get-Item -LiteralPath $path } | Out-Null
    $attachments = Get-GiteeAttachments $releaseId
    $uploaded = @($attachments | Where-Object { $_.name -eq $name })
    if ($uploaded.Count -ne 1) { throw "Gitee did not expose exactly one uploaded attachment named $name." }
    Assert-GiteeAttachment $uploaded[0] $path $releaseId
}

Write-Output "Verified Gitee Release $Tag with the same commit and immutable matching assets."
