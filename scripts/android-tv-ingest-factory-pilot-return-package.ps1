param(
    [Parameter(Mandatory = $true)]
    [string]$ReturnPath,
    [string]$OutputRoot = "",
    [switch]$AllowPending
)

$ErrorActionPreference = "Stop"

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function Get-SummaryMap {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $index = $line.IndexOf("=")
        if ($index -gt 0) {
            $key = $line.Substring(0, $index).Trim()
            $value = $line.Substring($index + 1).Trim()
            $map[$key] = $value
        }
    }
    return $map
}

function Invoke-ChildScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$LogPath
    )
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    Write-TextFile -Path $LogPath -Content $text
    return [pscustomobject]@{
        exitCode = $exitCode
        output = $text
    }
}

function Select-FeedbackFile {
    param(
        [System.IO.FileInfo[]]$Candidates,
        [string]$LeafName
    )
    if ($Candidates.Count -eq 0) {
        return $null
    }
    $preferred = $Candidates |
        Where-Object { $_.FullName -match "[\\/]+feedback[\\/]+$([regex]::Escape($LeafName))$" } |
        Sort-Object FullName |
        Select-Object -First 1
    if ($preferred) {
        return $preferred
    }
    return $Candidates | Sort-Object FullName | Select-Object -First 1
}

function Format-CandidateList {
    param([System.IO.FileInfo[]]$Candidates)
    return (@($Candidates | Sort-Object FullName | ForEach-Object { $_.FullName }) -join ",")
}

function Test-SafeZipEntryName {
    param([string]$Name)

    $normalized = ([string]$Name).Replace("\", "/")
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return $false
    }
    if ($normalized.StartsWith("/") -or $normalized -match "^[a-zA-Z]:") {
        return $false
    }
    $segments = @($normalized -split "/" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    return -not ($segments | Where-Object { $_ -eq ".." })
}

function Test-ZipEntrySafety {
    param([string]$ZipPath)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $entryCount = 0
    $unsafeEntries = @()
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        foreach ($entry in $archive.Entries) {
            $entryCount += 1
            $rawName = [string]$entry.FullName
            if (-not (Test-SafeZipEntryName -Name $rawName)) {
                $unsafeEntries += $rawName
            }
        }
    } finally {
        $archive.Dispose()
    }

    return [pscustomobject]@{
        entryCount = $entryCount
        unsafeEntries = @($unsafeEntries)
    }
}

function Test-SkipEvidencePathValue {
    param([string]$Value)
    $normalized = ([string]$Value).Trim().ToLowerInvariant()
    return [string]::IsNullOrWhiteSpace($normalized) -or
        @("not_tested", "untested", "n/a", "na", "none", "unknown") -contains $normalized
}

function Split-EvidencePathValue {
    param([string]$Value)
    return @(([string]$Value -split "[;`r`n]+") |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not (Test-SkipEvidencePathValue -Value $_) })
}

function Get-JsonField {
    param(
        [object]$Object,
        [string]$Name
    )
    if ($Object -and $Object.PSObject.Properties.Name -contains $Name -and $null -ne $Object.$Name) {
        return ([string]$Object.$Name).Trim()
    }
    return ""
}

function Test-RelativePackagePath {
    param(
        [string]$Root,
        [string]$PathValue
    )
    $raw = ([string]$PathValue).Trim()
    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    if ([System.IO.Path]::IsPathRooted($raw) -or $raw -match "^[a-zA-Z][a-zA-Z0-9+.-]*:") {
        return [pscustomobject]@{
            ok = $false
            resolvedPath = ""
            issue = "not a relative package path: $raw"
        }
    }

    $candidate = [System.IO.Path]::GetFullPath((Join-Path $Root $raw))
    $insideRoot = $candidate.Equals($rootFull, [System.StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($rootFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($rootFull + [System.IO.Path]::AltDirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
    if (-not $insideRoot) {
        return [pscustomobject]@{
            ok = $false
            resolvedPath = $candidate
            issue = "path escapes return package: $raw"
        }
    }
    if (-not (Test-Path -LiteralPath $candidate)) {
        return [pscustomobject]@{
            ok = $false
            resolvedPath = $candidate
            issue = "referenced evidence path not found: $raw"
        }
    }
    return [pscustomobject]@{
        ok = $true
        resolvedPath = $candidate
        issue = ""
    }
}

function Test-ReturnPackageEvidencePaths {
    param(
        [string]$Root,
        [System.IO.FileInfo]$FactoryFeedback,
        [System.IO.FileInfo]$VendorPermission
    )
    $checks = New-Object System.Collections.ArrayList
    $issues = New-Object System.Collections.ArrayList

    $targets = @()
    if ($FactoryFeedback) {
        $factoryJson = Get-Content -Raw -LiteralPath $FactoryFeedback.FullName | ConvertFrom-Json
        foreach ($field in @("screenshotOrVideoPath", "logsPath")) {
            $targets += [pscustomobject]@{
                source = "factory"
                field = $field
                value = Get-JsonField -Object $factoryJson -Name $field
            }
        }
    }
    if ($VendorPermission) {
        $vendorJson = Get-Content -Raw -LiteralPath $VendorPermission.FullName | ConvertFrom-Json
        $targets += [pscustomobject]@{
            source = "vendor"
            field = "evidencePath"
            value = Get-JsonField -Object $vendorJson -Name "evidencePath"
        }
    }

    foreach ($target in $targets) {
        foreach ($pathValue in (Split-EvidencePathValue -Value $target.value)) {
            $pathCheck = Test-RelativePackagePath -Root $Root -PathValue $pathValue
            $check = [pscustomobject]@{
                source = $target.source
                field = $target.field
                value = $pathValue
                ok = $pathCheck.ok
                resolvedPath = $pathCheck.resolvedPath
                issue = $pathCheck.issue
            }
            [void]$checks.Add($check)
            if (-not $pathCheck.ok) {
                [void]$issues.Add("$($target.source).$($target.field): $($pathCheck.issue)")
            }
        }
    }

    return [pscustomobject]@{
        checks = @($checks)
        issues = @($issues)
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-return-intake\return-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path
$returnItem = Resolve-Path -Path $ReturnPath

$inputDir = Join-Path $outputDir "return-package"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null

$returnItemInfo = Get-Item -LiteralPath $returnItem.Path
$preflightIssues = @()
$returnPackageEntryCount = 0
$unsafeReturnPackageEntries = @()
if ($returnItemInfo.PSIsContainer) {
    $returnPackageEntryCount = @(Get-ChildItem -LiteralPath $returnItem.Path -Recurse -Force).Count
    Get-ChildItem -LiteralPath $returnItem.Path -Force |
        Copy-Item -Destination $inputDir -Recurse -Force
} elseif ($returnItemInfo.Extension -ieq ".zip") {
    try {
        $zipSafety = Test-ZipEntrySafety -ZipPath $returnItem.Path
        $returnPackageEntryCount = $zipSafety.entryCount
        $unsafeReturnPackageEntries = @($zipSafety.unsafeEntries)
    } catch {
        $preflightIssues += "cannot inspect return package zip: $($_.Exception.Message)"
    }

    foreach ($unsafeEntry in $unsafeReturnPackageEntries) {
        $preflightIssues += "unsafe return package entry: $unsafeEntry"
    }

    if ($preflightIssues.Count -eq 0) {
        Expand-Archive -LiteralPath $returnItem.Path -DestinationPath $inputDir -Force
    }
} else {
    throw "ReturnPath must be a directory or .zip archive: $($returnItem.Path)"
}

$factoryCandidates = @()
$vendorCandidates = @()
if ($preflightIssues.Count -eq 0) {
    $factoryCandidates = @(Get-ChildItem -LiteralPath $inputDir -Recurse -File -Filter "android-tv-factory-feedback.json")
    $vendorCandidates = @(Get-ChildItem -LiteralPath $inputDir -Recurse -File -Filter "android-tv-vendor-system-permission.json")
}
$factoryFeedback = if ($factoryCandidates.Count -eq 1) { Select-FeedbackFile -Candidates $factoryCandidates -LeafName "android-tv-factory-feedback.json" } else { $null }
$vendorPermission = if ($vendorCandidates.Count -eq 1) { Select-FeedbackFile -Candidates $vendorCandidates -LeafName "android-tv-vendor-system-permission.json" } else { $null }

$issues = @($preflightIssues)
if (-not $factoryFeedback -and $preflightIssues.Count -eq 0) {
    if ($factoryCandidates.Count -gt 1) {
        $issues += "multiple android-tv-factory-feedback.json files found: $(Format-CandidateList -Candidates $factoryCandidates)"
    } else {
        $issues += "missing android-tv-factory-feedback.json"
    }
}
if (-not $vendorPermission -and $preflightIssues.Count -eq 0) {
    if ($vendorCandidates.Count -gt 1) {
        $issues += "multiple android-tv-vendor-system-permission.json files found: $(Format-CandidateList -Candidates $vendorCandidates)"
    } else {
        $issues += "missing android-tv-vendor-system-permission.json"
    }
}
$evidencePathChecks = @()
$evidencePathIssues = @()
if ($factoryFeedback -and $vendorPermission) {
    $evidencePathResult = Test-ReturnPackageEvidencePaths -Root $inputDir -FactoryFeedback $factoryFeedback -VendorPermission $vendorPermission
    $evidencePathChecks = @($evidencePathResult.checks)
    $evidencePathIssues = @($evidencePathResult.issues)
    foreach ($evidenceIssue in $evidencePathIssues) {
        $issues += $evidenceIssue
    }
}

$intakeOutputRoot = Join-Path $outputDir "factory-pilot-intake"
$intakeStatus = "NOT_RUN"
$factoryConclusion = ""
$vendorDecision = ""
$gateStatus = ""
$failedCount = ""
$pendingCount = ""
$childFailures = @()

if ($issues.Count -eq 0) {
    $intakeArgs = @(
        "-FactoryFeedbackPath", $factoryFeedback.FullName,
        "-VendorPermissionPath", $vendorPermission.FullName,
        "-OutputRoot", $intakeOutputRoot,
        "-EvidenceRoot", $inputDir
    )
    if ($AllowPending) {
        $intakeArgs += "-AllowPending"
    }
    $intakeCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-ingest-factory-pilot-feedback.ps1") `
        -Arguments $intakeArgs `
        -LogPath (Join-Path $outputDir "factory-pilot-intake.log")
    $intakeSummary = Get-SummaryMap -Path (Join-Path $intakeOutputRoot "summary.txt")
    $intakeStatus = if ($intakeSummary.ContainsKey("status")) { $intakeSummary["status"] } elseif ($intakeCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
    $factoryConclusion = if ($intakeSummary.ContainsKey("factoryConclusion")) { $intakeSummary["factoryConclusion"] } else { "" }
    $vendorDecision = if ($intakeSummary.ContainsKey("vendorDecision")) { $intakeSummary["vendorDecision"] } else { "" }
    $gateStatus = if ($intakeSummary.ContainsKey("gateStatus")) { $intakeSummary["gateStatus"] } else { "" }
    $failedCount = if ($intakeSummary.ContainsKey("failedCount")) { $intakeSummary["failedCount"] } else { "" }
    $pendingCount = if ($intakeSummary.ContainsKey("pendingCount")) { $intakeSummary["pendingCount"] } else { "" }
    if ($intakeCheck.exitCode -ne 0 -and -not ($intakeCheck.exitCode -eq 2 -and $intakeStatus -eq "PENDING")) {
        $childFailures += "factory-pilot-intake exit=$($intakeCheck.exitCode)"
    }
}

$status = if ($issues.Count -gt 0 -or $childFailures.Count -gt 0 -or $intakeStatus -eq "FAIL") {
    "FAIL"
} elseif ($intakeStatus -eq "PASS") {
    "PASS"
} else {
    "PENDING"
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    returnPath = $returnItem.Path
    copiedReturnPackageDir = $inputDir
    returnPackageEntryCount = $returnPackageEntryCount
    unsafeReturnPackageEntries = $unsafeReturnPackageEntries
    factoryFeedbackPath = if ($factoryFeedback) { $factoryFeedback.FullName } else { "" }
    vendorPermissionPath = if ($vendorPermission) { $vendorPermission.FullName } else { "" }
    factoryFeedbackCandidateCount = $factoryCandidates.Count
    vendorPermissionCandidateCount = $vendorCandidates.Count
    factoryFeedbackCandidates = @($factoryCandidates | Sort-Object FullName | ForEach-Object { $_.FullName })
    vendorPermissionCandidates = @($vendorCandidates | Sort-Object FullName | ForEach-Object { $_.FullName })
    intakeStatus = $intakeStatus
    factoryConclusion = $factoryConclusion
    vendorDecision = $vendorDecision
    gateStatus = $gateStatus
    failedCount = $failedCount
    pendingCount = $pendingCount
    evidencePathCheckCount = $evidencePathChecks.Count
    evidencePathIssueCount = $evidencePathIssues.Count
    evidencePathChecks = $evidencePathChecks
    issues = $issues
    childFailures = $childFailures
    evidence = [pscustomobject]@{
        factoryPilotIntake = $intakeOutputRoot
    }
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-return-package.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$outputDir
returnPath=$($returnItem.Path)
copiedReturnPackageDir=$inputDir
returnPackageEntryCount=$returnPackageEntryCount
unsafeReturnPackageEntries=$($unsafeReturnPackageEntries -join ",")
factoryFeedbackPath=$($result.factoryFeedbackPath)
vendorPermissionPath=$($result.vendorPermissionPath)
factoryFeedbackCandidateCount=$($factoryCandidates.Count)
vendorPermissionCandidateCount=$($vendorCandidates.Count)
intakeStatus=$intakeStatus
factoryConclusion=$factoryConclusion
vendorDecision=$vendorDecision
gateStatus=$gateStatus
failedCount=$failedCount
pendingCount=$pendingCount
evidencePathCheckCount=$($evidencePathChecks.Count)
evidencePathIssueCount=$($evidencePathIssues.Count)
issues=$($issues -join "; ")
childFailures=$($childFailures -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
if ($status -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
