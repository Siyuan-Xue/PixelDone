[CmdletBinding()]
param(
    [string]$WorkspaceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../../../..")).Path,
    [string[]]$Projects = @("."),
    [string]$DeviceName = "",
    [string]$TargetFolder = "Download",
    [ValidateSet("MissingOrStale", "Never", "Always")]
    [string]$BuildMode = "MissingOrStale",
    [ValidateSet("Auto", "Install", "Copy")]
    [string]$DeliveryMode = "Auto",
    [switch]$PreferAdb = $true,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Test-IsWindowsHost {
    $isWindowsVariable = Get-Variable -Name IsWindows -Scope Global -ErrorAction SilentlyContinue
    if ($isWindowsVariable) {
        return [bool]$isWindowsVariable.Value
    }
    return $env:OS -eq "Windows_NT"
}

function New-DeploymentResult {
    param(
        [string]$Project,
        [string]$SourceApk,
        [Nullable[Int64]]$LocalBytes,
        [string]$Device,
        [string]$TargetFolder,
        [string]$Method,
        [string]$Status,
        [object]$Verified
    )

    [pscustomobject]@{
        Project = $Project
        SourceApk = $SourceApk
        LocalBytes = $LocalBytes
        Device = $Device
        TargetFolder = $TargetFolder
        Method = $Method
        Status = $Status
        Verified = $Verified
    }
}

function Test-AndroidProject {
    param([System.IO.DirectoryInfo]$Directory)

    $settingsKts = Join-Path $Directory.FullName "settings.gradle.kts"
    $settingsGroovy = Join-Path $Directory.FullName "settings.gradle"
    $appGradleKts = Join-Path $Directory.FullName "app/build.gradle.kts"
    $appGradleGroovy = Join-Path $Directory.FullName "app/build.gradle"

    return ((Test-Path -LiteralPath $settingsKts) -or (Test-Path -LiteralPath $settingsGroovy)) -and
        ((Test-Path -LiteralPath $appGradleKts) -or (Test-Path -LiteralPath $appGradleGroovy))
}

function Get-TargetProjects {
    param(
        [string]$Root,
        [string[]]$Names
    )

    if (-not (Test-Path -LiteralPath $Root)) {
        throw "Repository root not found: $Root"
    }

    if ($Names -and $Names.Count -gt 0) {
        foreach ($name in $Names) {
            $path = Join-Path $Root $name
            if (-not (Test-Path -LiteralPath $path)) {
                throw "Project directory not found: $path"
            }
            $dir = Get-Item -LiteralPath $path
            if (-not (Test-AndroidProject -Directory $dir)) {
                throw "Directory is not a direct Android app project: $path"
            }
            $dir
        }
        return
    }

    Get-ChildItem -LiteralPath $Root -Directory |
        Where-Object { Test-AndroidProject -Directory $_ } |
        Sort-Object Name
}

function Get-VersionName {
    param([string]$ProjectPath)

    $gradleFiles = @(
        (Join-Path $ProjectPath "app/build.gradle.kts"),
        (Join-Path $ProjectPath "app/build.gradle")
    )

    foreach ($file in $gradleFiles) {
        if (-not (Test-Path -LiteralPath $file)) {
            continue
        }

        $content = Get-Content -LiteralPath $file -Raw
        $match = [regex]::Match($content, 'versionName\s*(?:=|\s)\s*["'']([^"'']+)["'']')
        if ($match.Success) {
            return $match.Groups[1].Value
        }
    }

    return $null
}

function Get-ProjectApkState {
    param([System.IO.DirectoryInfo]$ProjectDir)

    $projectName = $ProjectDir.Name
    $debugDir = Join-Path $ProjectDir.FullName "app/build/outputs/apk/debug"
    $appDebug = Join-Path $debugDir "app-debug.apk"
    $versionName = Get-VersionName -ProjectPath $ProjectDir.FullName
    $expectedApk = if ($versionName) { Join-Path $debugDir "$projectName-$versionName-debug.apk" } else { $null }
    $apk = $null

    if ($expectedApk -and (Test-Path -LiteralPath $expectedApk)) {
        $apk = Get-Item -LiteralPath $expectedApk
    }

    if (-not $apk -and -not $versionName -and (Test-Path -LiteralPath $debugDir)) {
        $apk = Get-ChildItem -LiteralPath $debugDir -File -Filter "$projectName-*-debug.apk" |
            Where-Object { $_.Name -notlike "*androidTest*" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
    }

    $appDebugItem = if (Test-Path -LiteralPath $appDebug) { Get-Item -LiteralPath $appDebug } else { $null }
    $isMissing = -not $apk
    $isStale = $false
    if ($apk -and $appDebugItem -and $apk.LastWriteTime -lt $appDebugItem.LastWriteTime) {
        $isStale = $true
    }

    [pscustomobject]@{
        ProjectName = $projectName
        ProjectPath = $ProjectDir.FullName
        DebugDir = $debugDir
        VersionName = $versionName
        ExpectedApk = $expectedApk
        Apk = $apk
        AppDebug = $appDebugItem
        IsMissing = $isMissing
        IsStale = $isStale
    }
}

function Invoke-AssembleDebug {
    param([string]$ProjectPath)

    $gradleWrapperName = if (Test-IsWindowsHost) { "gradlew.bat" } else { "gradlew" }
    $gradlew = Join-Path $ProjectPath $gradleWrapperName
    if (-not (Test-Path -LiteralPath $gradlew)) {
        throw "Gradle wrapper not found: $gradlew"
    }

    Push-Location $ProjectPath
    try {
        & $gradlew assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "assembleDebug failed in $ProjectPath with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Get-AdbTarget {
    param([string]$RequestedDeviceName)

    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        return $null
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $lines = @(& adb devices -l 2>&1) | Where-Object {
            $_ -notmatch '^\* daemon ' -and
            $_ -notmatch '^daemon '
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($LASTEXITCODE -ne 0 -or -not $lines) {
        return $null
    }

    $devices = foreach ($line in $lines) {
        if ($line -match '^\s*([^\s]+)\s+device\s+(.+)$') {
            $serial = $matches[1]
            $detail = $matches[2]
            if ($serial -notlike "emulator-*") {
                [pscustomobject]@{
                    Serial = $serial
                    Detail = $detail
                    Line = $line
                }
            }
        }
    }

    if (-not $devices) {
        return $null
    }

    if (-not [string]::IsNullOrWhiteSpace($RequestedDeviceName)) {
        $needle = ($RequestedDeviceName -replace '\s+', '[_ ]?')
        $matched = $devices | Where-Object { $_.Line -match $needle } | Select-Object -First 1
        if ($matched) {
            return $matched
        }
    }

    if (@($devices).Count -eq 1) {
        return @($devices)[0]
    }

    return $null
}

function Copy-WithAdb {
    param(
        [string]$Serial,
        [System.IO.FileInfo]$Apk,
        [string]$Folder
    )

    $remotePath = "/sdcard/$Folder/$($Apk.Name)"
    & adb -s $Serial push $Apk.FullName $remotePath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "adb push failed for $($Apk.Name)"
    }

    $remoteListing = (& adb -s $Serial shell "ls -ln '$remotePath'" 2>$null | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or -not ($remoteListing -match '^\S+\s+\d+\s+\S+\s+\S+\s+(\d+)\s+')) {
        return $false
    }

    return ([int64]$matches[1] -eq [int64]$Apk.Length)
}

function Install-WithAdb {
    param(
        [string]$Serial,
        [System.IO.FileInfo]$Apk
    )

    $output = & adb -s $Serial install -r -d $Apk.FullName 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("adb install failed for {0}: {1}" -f $Apk.Name, (($output | Out-String).Trim()))
    }

    return $true
}

function Convert-ShellSizeToBytes {
    param([string]$SizeText)

    if ([string]::IsNullOrWhiteSpace($SizeText)) {
        return $null
    }

    $normalized = $SizeText.Trim() -replace ',', '.'
    if ($normalized -match '^([0-9]+(?:\.[0-9]+)?)\s*(B|bytes?|KB|MB|GB)$') {
        $value = [double]$matches[1]
        $unit = $matches[2].ToUpperInvariant()
        switch ($unit) {
            "GB" { return [int64]($value * 1GB) }
            "MB" { return [int64]($value * 1MB) }
            "KB" { return [int64]($value * 1KB) }
            default { return [int64]$value }
        }
    }

    return $null
}

function Test-ApproximateBytes {
    param(
        [Int64]$LocalBytes,
        [Nullable[Int64]]$TargetBytes
    )

    if ($null -eq $TargetBytes) {
        return $false
    }

    $delta = [Math]::Abs($LocalBytes - [Int64]$TargetBytes)
    $tolerance = [Math]::Max(1048576, [Int64]($LocalBytes * 0.05))
    return $delta -le $tolerance
}

function Get-MtpTargetFolder {
    param(
        [string]$RequestedDeviceName,
        [string]$FolderName
    )

    if (-not (Test-IsWindowsHost) -or [string]::IsNullOrWhiteSpace($RequestedDeviceName)) {
        return $null
    }

    $shell = New-Object -ComObject Shell.Application
    $root = $shell.Namespace(17)
    if (-not $root) {
        return $null
    }

    $devices = @($root.Items())
    $device = $devices | Where-Object { $_.Name -eq $RequestedDeviceName } | Select-Object -First 1
    if (-not $device) {
        $device = $devices | Where-Object { $_.Name -like "*$RequestedDeviceName*" } | Select-Object -First 1
    }
    if (-not $device) {
        return $null
    }

    $storage = @($device.GetFolder.Items()) |
        Where-Object { $_.Name -eq "Internal shared storage" -or $_.Name -eq "Internal storage" } |
        Select-Object -First 1
    if (-not $storage) {
        return $null
    }

    $target = @($storage.GetFolder.Items()) |
        Where-Object { $_.Name -eq $FolderName } |
        Select-Object -First 1
    if (-not $target) {
        return $null
    }

    [pscustomobject]@{
        Device = $device
        Storage = $storage
        Folder = $target
        FolderObject = $target.GetFolder
    }
}

function Copy-WithMtp {
    param(
        [object]$Target,
        [System.IO.FileInfo]$Apk
    )

    $folder = $Target.FolderObject
    $flags = 4 + 16 + 1024
    $folder.CopyHere($Apk.FullName, $flags)

    $deadline = (Get-Date).AddSeconds(120)
    do {
        Start-Sleep -Milliseconds 750
        $item = $folder.ParseName($Apk.Name)
        if ($item) {
            $type = $folder.GetDetailsOf($item, 1)
            $sizeText = $folder.GetDetailsOf($item, 2)
            $targetBytes = Convert-ShellSizeToBytes -SizeText $sizeText
            if ((Test-ApproximateBytes -LocalBytes $Apk.Length -TargetBytes $targetBytes) -and (($type -match 'APK') -or [string]::IsNullOrWhiteSpace($type))) {
                return [pscustomobject]@{
                    Verified = $true
                    Type = $type
                    SizeText = $sizeText
                    TargetBytes = $targetBytes
                }
            }
        }
    } while ((Get-Date) -lt $deadline)

    return [pscustomobject]@{
        Verified = $false
        Type = $null
        SizeText = $null
        TargetBytes = $null
    }
}

$projectDirs = @(Get-TargetProjects -Root $WorkspaceRoot -Names $Projects)
if ($projectDirs.Count -eq 0) {
    throw "No direct Android app projects found under $WorkspaceRoot"
}

$adbTarget = $null
if ($PreferAdb) {
    $adbTarget = Get-AdbTarget -RequestedDeviceName $DeviceName
}

$mtpTarget = $null
if (-not $adbTarget -or $DeliveryMode -eq "Copy") {
    $mtpTarget = Get-MtpTargetFolder -RequestedDeviceName $DeviceName -FolderName $TargetFolder
}

$method = if ($adbTarget -and $DeliveryMode -ne "Copy") {
    "adb-install"
}
elseif ($adbTarget -and $DeliveryMode -eq "Copy") {
    "adb-push"
}
elseif ($mtpTarget -and $DeliveryMode -ne "Install") {
    "mtp-copy"
}
else {
    "none"
}
$deviceLabel = if ($adbTarget) { $adbTarget.Serial } elseif ($mtpTarget) { $mtpTarget.Device.Name } elseif (-not [string]::IsNullOrWhiteSpace($DeviceName)) { $DeviceName } else { "unspecified" }

$results = New-Object System.Collections.Generic.List[object]

foreach ($projectDir in $projectDirs) {
    $state = Get-ProjectApkState -ProjectDir $projectDir
    $needsBuild = $BuildMode -eq "Always" -or ($BuildMode -eq "MissingOrStale" -and ($state.IsMissing -or $state.IsStale))

    if ($needsBuild) {
        if ($DryRun) {
            $source = if ($state.Apk) { $state.Apk.FullName } elseif ($state.ExpectedApk) { $state.ExpectedApk } else { "" }
            $bytes = if ($state.Apk) { [int64]$state.Apk.Length } else { $null }
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $source -LocalBytes $bytes -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "WouldBuild" -Verified "DryRun"))
            continue
        }

        Invoke-AssembleDebug -ProjectPath $state.ProjectPath
        $state = Get-ProjectApkState -ProjectDir $projectDir
        if (-not $state.AppDebug) {
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk "" -LocalBytes $null -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "MissingAppDebugAfterBuild" -Verified $false))
            continue
        }
        if (-not $state.Apk) {
            $missingSource = if ($state.ExpectedApk) { $state.ExpectedApk } else { "" }
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $missingSource -LocalBytes $null -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "MissingVersionedApkAfterBuild" -Verified $false))
            continue
        }
    }

    if ($BuildMode -eq "Never" -and ($state.IsMissing -or $state.IsStale)) {
        $status = if ($state.IsMissing) { "MissingApk" } else { "StaleApk" }
        $source = if ($state.Apk) { $state.Apk.FullName } elseif ($state.ExpectedApk) { $state.ExpectedApk } else { "" }
        $bytes = if ($state.Apk) { [int64]$state.Apk.Length } else { $null }
        $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $source -LocalBytes $bytes -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status $status -Verified $false))
        continue
    }

    if (-not $state.Apk) {
        $missingSource = if ($state.ExpectedApk) { $state.ExpectedApk } else { "" }
        $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $missingSource -LocalBytes $null -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "MissingApk" -Verified $false))
        continue
    }

    if ($method -eq "none") {
        $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "NoDevice" -Verified $false))
        continue
    }

    if ($DryRun) {
        $dryRunStatus = if ($method -eq "adb-install") { "WouldInstall" } else { "WouldDeploy" }
        $dryRunTarget = if ($method -eq "adb-install") { "InstalledApp" } else { $TargetFolder }
        $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder $dryRunTarget -Method $method -Status $dryRunStatus -Verified "DryRun"))
        continue
    }

    try {
        if ($method -eq "adb-install") {
            $verified = Install-WithAdb -Serial $adbTarget.Serial -Apk $state.Apk
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder "InstalledApp" -Method $method -Status "Installed" -Verified $verified))
        }
        elseif ($method -eq "adb-push") {
            $verified = Copy-WithAdb -Serial $adbTarget.Serial -Apk $state.Apk -Folder $TargetFolder
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "Copied" -Verified $verified))
        }
        else {
            $verification = Copy-WithMtp -Target $mtpTarget -Apk $state.Apk
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status "Copied" -Verified $verification.Verified))
        }
    }
    catch {
        $primaryError = $_.Exception.Message
        $fallbackComplete = $false

        if ($method -eq "adb-install" -and $DeliveryMode -eq "Auto" -and $adbTarget) {
            try {
                $verified = Copy-WithAdb -Serial $adbTarget.Serial -Apk $state.Apk -Folder $TargetFolder
                $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder $TargetFolder -Method "adb-push" -Status ("CopiedAfterInstallFailed: " + $primaryError) -Verified $verified))
                $fallbackComplete = $true
            }
            catch {
                $primaryError = $primaryError + " | adb-push fallback failed: " + $_.Exception.Message
            }
        }

        if (-not $fallbackComplete -and $DeliveryMode -eq "Auto") {
            try {
                if (-not $mtpTarget) {
                    $mtpTarget = Get-MtpTargetFolder -RequestedDeviceName $DeviceName -FolderName $TargetFolder
                }
                if ($mtpTarget) {
                    $verification = Copy-WithMtp -Target $mtpTarget -Apk $state.Apk
                    $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $mtpTarget.Device.Name -TargetFolder $TargetFolder -Method "mtp-copy" -Status ("CopiedAfterFallback: " + $primaryError) -Verified $verification.Verified))
                    $fallbackComplete = $true
                }
            }
            catch {
                $primaryError = $primaryError + " | mtp fallback failed: " + $_.Exception.Message
            }
        }

        if (-not $fallbackComplete) {
            $results.Add((New-DeploymentResult -Project $state.ProjectName -SourceApk $state.Apk.FullName -LocalBytes ([int64]$state.Apk.Length) -Device $deviceLabel -TargetFolder $TargetFolder -Method $method -Status ("Failed: " + $primaryError) -Verified $false))
        }
    }
}

$results
