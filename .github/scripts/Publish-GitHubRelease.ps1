[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Repository,
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$ExpectedCommit,
    [Parameter(Mandatory = $true)][string]$Title,
    [Parameter(Mandatory = $true)][string]$NotesPath,
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$ChecksumPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Normalize-Text([string]$Value) {
    return ($Value -replace "`r`n", "`n").TrimEnd()
}

function Assert-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing release input: $Path"
    }
}

function Assert-DownloadedAsset(
    [string]$AssetName,
    [string]$ExpectedPath,
    [string]$ReleaseTag
) {
    $downloadDirectory = Join-Path $env:RUNNER_TEMP ("github-release-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $downloadDirectory | Out-Null
    try {
        & gh release download $ReleaseTag --repo $Repository --pattern $AssetName --dir $downloadDirectory
        if ($LASTEXITCODE -ne 0) { throw "Unable to download GitHub asset $AssetName." }
        $downloadedPath = Join-Path $downloadDirectory $AssetName
        $expectedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ExpectedPath).Hash
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $downloadedPath).Hash
        if ($actualHash -ne $expectedHash) {
            throw "GitHub asset $AssetName conflicts with the built artifact."
        }
    } finally {
        Remove-Item -LiteralPath $downloadDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

foreach ($path in @($NotesPath, $ApkPath, $ChecksumPath)) {
    Assert-File $path
}
if ([string]::IsNullOrWhiteSpace($env:GH_TOKEN)) { throw "GH_TOKEN is required." }

$releaseJson = & gh release view $Tag --repo $Repository --json tagName,name,body,isDraft,isPrerelease,assets 2>$null
$releaseExists = $LASTEXITCODE -eq 0

if (-not $releaseExists) {
    & gh release create $Tag $ApkPath $ChecksumPath `
        --repo $Repository `
        --title $Title `
        --notes-file $NotesPath `
        --verify-tag
    if ($LASTEXITCODE -ne 0) { throw "GitHub Release creation failed for $Tag." }
} else {
    $release = $releaseJson | ConvertFrom-Json
    if ($release.tagName -ne $Tag) { throw "GitHub Release tag mismatch." }
    if ($release.name -ne $Title) { throw "GitHub Release title mismatch." }
    if ($release.isDraft -or $release.isPrerelease) { throw "Existing GitHub Release is not a normal release." }
    $expectedNotes = Normalize-Text (Get-Content -Raw -LiteralPath $NotesPath)
    if ((Normalize-Text ([string]$release.body)) -ne $expectedNotes) {
        throw "Existing GitHub Release notes conflict with the generated notes."
    }

    $assetNames = @($release.assets | ForEach-Object { $_.name })
    foreach ($path in @($ApkPath, $ChecksumPath)) {
        $name = Split-Path -Leaf $path
        if ($assetNames -contains $name) {
            Assert-DownloadedAsset $name $path $Tag
        } else {
            & gh release upload $Tag $path --repo $Repository
            if ($LASTEXITCODE -ne 0) { throw "Unable to complete missing GitHub asset $name." }
        }
    }
}

$tagCommit = & git rev-list -n 1 $Tag
if ($LASTEXITCODE -ne 0 -or $tagCommit.Trim() -ne $ExpectedCommit) {
    throw "GitHub tag $Tag no longer resolves to the expected commit."
}

foreach ($path in @($ApkPath, $ChecksumPath)) {
    Assert-DownloadedAsset (Split-Path -Leaf $path) $path $Tag
}

Write-Output "Verified GitHub Release $Tag with immutable matching assets."
