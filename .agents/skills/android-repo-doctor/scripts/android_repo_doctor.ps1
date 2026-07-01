param(
    [string]$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../../../..")).Path,
    [string[]]$Repos = @("."),
    [ValidateSet("text", "json")]
    [string]$Format = "text"
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string]$Cwd,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "git"
    $psi.WorkingDirectory = $Cwd
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $safePath = $Cwd -replace "\\", "/"
    $gitArguments = @("-c", "safe.directory=$safePath") + $Arguments
    $psi.Arguments = Join-ProcessArguments $gitArguments

    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    [pscustomobject]@{
        ExitCode = $process.ExitCode
        StdoutLines = Split-OutputLines $stdout
        StderrLines = Split-OutputLines $stderr
    }
}

function Join-ProcessArguments {
    param([string[]]$Arguments)

    $quoted = @()
    foreach ($arg in $Arguments) {
        if ($arg -match '[\s"]') {
            $quoted += '"' + ($arg -replace '"', '\"') + '"'
        }
        else {
            $quoted += $arg
        }
    }
    return ($quoted -join " ")
}

function Split-OutputLines {
    param([string]$Text)

    if ([string]::IsNullOrEmpty($Text)) {
        return @()
    }
    return @($Text -split "\r?\n" | Where-Object { $_ -ne "" })
}

function Normalize-RepoPath {
    param([string]$Path)
    return ($Path -replace "\\", "/")
}

function Test-AnyPathExists {
    param(
        [string]$RepoPath,
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        if (Test-Path -LiteralPath (Join-Path $RepoPath $candidate)) {
            return $true
        }
    }
    return $false
}

function Test-TrackedPollution {
    param([string]$RepoRelativePath)

    $path = Normalize-RepoPath $RepoRelativePath

    if ($path -match '(^|/)(\.gradle|build|\.kotlin|captures|\.externalNativeBuild|\.cxx)(/|$)') {
        return "generated/cache path"
    }
    if ($path -match '(^|/)local\.properties$') {
        return "local Android SDK properties"
    }
    if ($path -match '(^|/)\.idea/(workspace\.xml|usage\.statistics\.xml|shelf/|caches/|libraries/|modules\.xml|assetWizardSettings\.xml|navEditor\.xml)') {
        return "local Android Studio metadata"
    }
    if ($path -match '\.(apk|aab|ap_|idsig)$') {
        return "built Android artifact"
    }
    if ($path -match '\.(jks|keystore|p12|pem)$') {
        return "possible signing secret"
    }
    if ($path -match '(^|/)(key|signing|release|keystore)\.properties$') {
        return "possible signing properties"
    }

    return $null
}

function Test-VisibleStatusPollution {
    param([string]$StatusLine)

    if ($StatusLine.Length -lt 4) {
        return $null
    }

    $path = $StatusLine.Substring(3).Trim()
    $path = $path -replace '^"', ''
    $path = $path -replace '"$', ''
    $reason = Test-TrackedPollution $path

    if ($null -ne $reason) {
        return "$path ($reason) is visible in normal git status; it should be ignored or removed from tracking."
    }

    return $null
}

function Add-Unique {
    param(
        [System.Collections.ArrayList]$List,
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return
    }
    if (-not $List.Contains($Value)) {
        [void]$List.Add($Value)
    }
}

function Test-GitignoreContains {
    param(
        [string[]]$Lines,
        [string[]]$AcceptedPatterns
    )

    foreach ($line in $Lines) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#") -or $trimmed.Length -eq 0) {
            continue
        }
        foreach ($pattern in $AcceptedPatterns) {
            if ($trimmed -eq $pattern) {
                return $true
            }
        }
    }
    return $false
}

function Test-Repo {
    param(
        [string]$RootPath,
        [string]$RepoName
    )

    $repoPath = if ([string]::IsNullOrWhiteSpace($RepoName) -or $RepoName -eq ".") {
        $RootPath
    }
    else {
        Join-Path $RootPath $RepoName
    }
    $displayName = if ([string]::IsNullOrWhiteSpace($RepoName) -or $RepoName -eq ".") {
        Split-Path -Leaf $repoPath
    }
    else {
        $RepoName
    }
    $failures = New-Object System.Collections.ArrayList
    $warnings = New-Object System.Collections.ArrayList
    $info = New-Object System.Collections.ArrayList

    if (-not (Test-Path -LiteralPath $repoPath)) {
        Add-Unique $failures "Repository directory is missing: $repoPath"
        return [pscustomobject]@{
            name = $displayName
            path = $repoPath
            status = "FAIL"
            failures = @($failures)
            warnings = @($warnings)
            info = @($info)
        }
    }

    if (-not (Test-Path -LiteralPath (Join-Path $repoPath ".git"))) {
        Add-Unique $failures "Repository is not an independent Git repo."
        return [pscustomobject]@{
            name = $displayName
            path = $repoPath
            status = "FAIL"
            failures = @($failures)
            warnings = @($warnings)
            info = @($info)
        }
    }

    $statusResult = Invoke-Git $repoPath @("status", "--short")
    foreach ($line in $statusResult.StderrLines) {
        if ($line -match '^warning:') {
            Add-Unique $warnings "Git environment warning: $line"
        }
        elseif ($line -match '^fatal:') {
            Add-Unique $failures "git status failed: $line"
        }
        else {
            Add-Unique $warnings "Git status stderr: $line"
        }
    }
    foreach ($line in $statusResult.StdoutLines) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        Add-Unique $info "Working tree change: $line"
        $visiblePollution = Test-VisibleStatusPollution $line
        if ($null -ne $visiblePollution) {
            Add-Unique $failures $visiblePollution
        }
    }
    if ($statusResult.ExitCode -ne 0) {
        Add-Unique $failures "git status failed with exit code $($statusResult.ExitCode)."
    }

    $trackedResult = Invoke-Git $repoPath @("ls-files")
    foreach ($line in $trackedResult.StderrLines) {
        if ($line -match '^warning:') {
            Add-Unique $warnings "Git environment warning: $line"
        }
        elseif ($line -match '^fatal:') {
            Add-Unique $failures "git ls-files failed: $line"
        }
        else {
            Add-Unique $warnings "Git ls-files stderr: $line"
        }
    }
    foreach ($line in $trackedResult.StdoutLines) {

        $reason = Test-TrackedPollution $line
        if ($null -ne $reason) {
            Add-Unique $failures "Tracked pollution: $line ($reason)."
        }
    }
    if ($trackedResult.ExitCode -ne 0) {
        Add-Unique $failures "git ls-files failed with exit code $($trackedResult.ExitCode)."
    }

    $requiredGroups = @(
        @{ Label = "settings.gradle(.kts)"; Candidates = @("settings.gradle.kts", "settings.gradle") },
        @{ Label = "root build.gradle(.kts)"; Candidates = @("build.gradle.kts", "build.gradle") },
        @{ Label = "gradle.properties"; Candidates = @("gradle.properties") },
        @{ Label = "gradlew"; Candidates = @("gradlew") },
        @{ Label = "gradlew.bat"; Candidates = @("gradlew.bat") },
        @{ Label = "gradle-wrapper.jar"; Candidates = @("gradle/wrapper/gradle-wrapper.jar") },
        @{ Label = "gradle-wrapper.properties"; Candidates = @("gradle/wrapper/gradle-wrapper.properties") },
        @{ Label = "app build.gradle(.kts)"; Candidates = @("app/build.gradle.kts", "app/build.gradle") },
        @{ Label = "main AndroidManifest.xml"; Candidates = @("app/src/main/AndroidManifest.xml") },
        @{ Label = "README.md"; Candidates = @("README.md") }
    )

    foreach ($group in $requiredGroups) {
        if (-not (Test-AnyPathExists $repoPath $group.Candidates)) {
            Add-Unique $failures "Required project input is missing: $($group.Label)."
            continue
        }

        foreach ($candidate in $group.Candidates) {
            if (-not (Test-Path -LiteralPath (Join-Path $repoPath $candidate))) {
                continue
            }
            $ignoreResult = Invoke-Git $repoPath @("check-ignore", "-v", "--", $candidate)
            foreach ($line in $ignoreResult.StderrLines) {
                if ($line -match '^warning:') {
                    Add-Unique $warnings "Git environment warning: $line"
                }
                elseif ($line -match '^fatal:') {
                    Add-Unique $failures "git check-ignore failed for ${candidate}: $line"
                }
                else {
                    Add-Unique $warnings "Git check-ignore stderr for ${candidate}: $line"
                }
            }
            if ($ignoreResult.ExitCode -eq 0) {
                Add-Unique $failures "Required project input is ignored by .gitignore: $candidate"
            }
        }
    }

    $rootGitignore = Join-Path $repoPath ".gitignore"
    if (-not (Test-Path -LiteralPath $rootGitignore)) {
        Add-Unique $warnings "Root .gitignore is missing."
    }
    else {
        $rootLines = Get-Content -LiteralPath $rootGitignore
        if (Test-GitignoreContains $rootLines @("/.idea/", ".idea/", "/.idea", ".idea")) {
            Add-Unique $warnings "Root .gitignore ignores the entire .idea directory; prefer excluding only local Android Studio state."
        }
        if (-not (Test-GitignoreContains $rootLines @("*.iml"))) {
            Add-Unique $warnings "Root .gitignore should ignore generated IntelliJ module files (*.iml)."
        }
        if (-not (Test-GitignoreContains $rootLines @(".gradle", ".gradle/"))) {
            Add-Unique $warnings "Root .gitignore should ignore Gradle project cache (.gradle/)."
        }
        if (-not (Test-GitignoreContains $rootLines @("/local.properties", "local.properties"))) {
            Add-Unique $warnings "Root .gitignore should ignore local.properties."
        }
        if (-not (Test-GitignoreContains $rootLines @("/build", "/build/", "build/", "build"))) {
            Add-Unique $warnings "Root .gitignore should ignore root build output (/build/)."
        }
        if (-not (Test-GitignoreContains $rootLines @("/captures", "/captures/"))) {
            Add-Unique $warnings "Root .gitignore should ignore Android Studio captures (/captures/)."
        }
        if (-not (Test-GitignoreContains $rootLines @(".externalNativeBuild", ".externalNativeBuild/"))) {
            Add-Unique $warnings "Root .gitignore should ignore .externalNativeBuild/."
        }
        if (-not (Test-GitignoreContains $rootLines @(".cxx", ".cxx/"))) {
            Add-Unique $warnings "Root .gitignore should ignore .cxx/."
        }
        if (-not (Test-GitignoreContains $rootLines @("/.idea/workspace.xml", ".idea/workspace.xml"))) {
            Add-Unique $warnings "Root .gitignore should ignore .idea/workspace.xml."
        }
    }

    $appGitignore = Join-Path $repoPath "app/.gitignore"
    if (-not (Test-Path -LiteralPath $appGitignore)) {
        Add-Unique $warnings "app/.gitignore is missing; module build output should be ignored with /build/."
    }
    else {
        $appLines = Get-Content -LiteralPath $appGitignore
        if (-not (Test-GitignoreContains $appLines @("/build", "/build/", "build/", "build"))) {
            Add-Unique $warnings "app/.gitignore should ignore module build output (/build/)."
        }
    }

    $repoStatus = "PASS"
    if ($failures.Count -gt 0) {
        $repoStatus = "FAIL"
    }
    elseif ($warnings.Count -gt 0) {
        $repoStatus = "WARN"
    }

    [pscustomobject]@{
        name = $displayName
        path = $repoPath
        status = $repoStatus
        failures = @($failures)
        warnings = @($warnings)
        info = @($info)
    }
}

$resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
$repoResults = @()
foreach ($repo in $Repos) {
    $repoResults += Test-Repo $resolvedRoot $repo
}

$overallStatus = "PASS"
if (@($repoResults | Where-Object { $_.status -eq "FAIL" }).Count -gt 0) {
    $overallStatus = "FAIL"
}
elseif (@($repoResults | Where-Object { $_.status -eq "WARN" }).Count -gt 0) {
    $overallStatus = "WARN"
}

$result = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    root = $resolvedRoot
    overallStatus = $overallStatus
    repos = $repoResults
}

if ($Format -eq "json") {
    $result | ConvertTo-Json -Depth 8
}
else {
    Write-Output "Android Repo Doctor"
    Write-Output "Root: $($result.root)"
    Write-Output "Overall: $($result.overallStatus)"
    Write-Output ""
    foreach ($repo in $result.repos) {
        Write-Output "$($repo.name): $($repo.status)"
        foreach ($failure in $repo.failures) {
            Write-Output "  FAIL: $failure"
        }
        foreach ($warning in $repo.warnings) {
            Write-Output "  WARN: $warning"
        }
        foreach ($item in $repo.info) {
            Write-Output "  INFO: $item"
        }
        Write-Output ""
    }
}

if ($overallStatus -eq "FAIL") {
    exit 1
}

exit 0
