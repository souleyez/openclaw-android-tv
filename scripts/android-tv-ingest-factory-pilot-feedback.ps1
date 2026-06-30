param(
    [Parameter(Mandatory = $true)]
    [string]$FactoryFeedbackPath,
    [Parameter(Mandatory = $true)]
    [string]$VendorPermissionPath,
    [string]$OutputRoot = "",
    [string]$EvidenceRoot = "",
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

function Test-RelativeEvidencePath {
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
            issue = "not a relative evidence path: $raw"
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
            issue = "path escapes evidence root: $raw"
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

function Test-FeedbackEvidencePaths {
    param(
        [string]$Root,
        [string]$FactoryFeedbackPath,
        [string]$VendorPermissionPath
    )
    $checks = New-Object System.Collections.ArrayList
    $issues = New-Object System.Collections.ArrayList

    $factoryJson = Get-Content -Raw -LiteralPath $FactoryFeedbackPath | ConvertFrom-Json
    foreach ($field in @("screenshotOrVideoPath", "logsPath")) {
        foreach ($pathValue in (Split-EvidencePathValue -Value (Get-JsonField -Object $factoryJson -Name $field))) {
            $pathCheck = Test-RelativeEvidencePath -Root $Root -PathValue $pathValue
            $check = [pscustomobject]@{
                source = "factory"
                field = $field
                value = $pathValue
                ok = $pathCheck.ok
                resolvedPath = $pathCheck.resolvedPath
                issue = $pathCheck.issue
            }
            [void]$checks.Add($check)
            if (-not $pathCheck.ok) {
                [void]$issues.Add("factory.$field`: $($pathCheck.issue)")
            }
        }
    }

    $vendorJson = Get-Content -Raw -LiteralPath $VendorPermissionPath | ConvertFrom-Json
    foreach ($pathValue in (Split-EvidencePathValue -Value (Get-JsonField -Object $vendorJson -Name "evidencePath"))) {
        $pathCheck = Test-RelativeEvidencePath -Root $Root -PathValue $pathValue
        $check = [pscustomobject]@{
            source = "vendor"
            field = "evidencePath"
            value = $pathValue
            ok = $pathCheck.ok
            resolvedPath = $pathCheck.resolvedPath
            issue = $pathCheck.issue
        }
        [void]$checks.Add($check)
        if (-not $pathCheck.ok) {
            [void]$issues.Add("vendor.evidencePath: $($pathCheck.issue)")
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
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-intake\intake-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$factoryFeedback = Resolve-Path -Path $FactoryFeedbackPath
$vendorPermission = Resolve-Path -Path $VendorPermissionPath
$evidenceRootPath = ""
if (-not [string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $evidenceRootPath = (Resolve-Path -Path $EvidenceRoot).Path
}

$inputDir = Join-Path $outputDir "input"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -LiteralPath $factoryFeedback.Path -Destination (Join-Path $inputDir "android-tv-factory-feedback.json") -Force
Copy-Item -LiteralPath $vendorPermission.Path -Destination (Join-Path $inputDir "android-tv-vendor-system-permission.json") -Force

$evidencePathChecks = @()
$evidencePathIssues = @()
if ($evidenceRootPath) {
    $evidencePathResult = Test-FeedbackEvidencePaths -Root $evidenceRootPath -FactoryFeedbackPath $factoryFeedback.Path -VendorPermissionPath $vendorPermission.Path
    $evidencePathChecks = @($evidencePathResult.checks)
    $evidencePathIssues = @($evidencePathResult.issues)
}

$factoryOutputRoot = Join-Path $outputDir "factory-feedback"
$factoryCheck = [pscustomobject]@{ exitCode = 0; output = "" }

$vendorOutputRoot = Join-Path $outputDir "vendor-permission"
$vendorCheck = [pscustomobject]@{ exitCode = 0; output = "" }

$gateOutputRoot = Join-Path $outputDir "factory-pilot-gate"
$gateCheck = [pscustomobject]@{ exitCode = 0; output = "" }

if ($evidencePathIssues.Count -eq 0) {
    $factoryCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-classify-factory-feedback.ps1") `
        -Arguments @("-FeedbackPath", $factoryFeedback.Path, "-OutputRoot", $factoryOutputRoot) `
        -LogPath (Join-Path $outputDir "factory-feedback.log")

    $vendorCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-classify-vendor-permission.ps1") `
        -Arguments @("-FeedbackPath", $vendorPermission.Path, "-OutputRoot", $vendorOutputRoot) `
        -LogPath (Join-Path $outputDir "vendor-permission.log")

    $gateArgs = @(
        "-OutputRoot", $gateOutputRoot,
        "-FactoryFeedbackPath", $factoryFeedback.Path,
        "-VendorPermissionPath", $vendorPermission.Path
    )
    if ($AllowPending) {
        $gateArgs += "-AllowPending"
    }
    $gateCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-factory-pilot-gates.ps1") `
        -Arguments $gateArgs `
        -LogPath (Join-Path $outputDir "factory-pilot-gate.log")
}

$factorySummary = Get-SummaryMap -Path (Join-Path $factoryOutputRoot "summary.txt")
$vendorSummary = Get-SummaryMap -Path (Join-Path $vendorOutputRoot "summary.txt")
$gateSummary = Get-SummaryMap -Path (Join-Path $gateOutputRoot "summary.txt")

$factoryConclusion = if ($factorySummary.ContainsKey("recommendedConclusion")) { $factorySummary["recommendedConclusion"] } else { "" }
$factoryAction = if ($factorySummary.ContainsKey("requiredFactoryAction")) { $factorySummary["requiredFactoryAction"] } else { "" }
$vendorDecision = if ($vendorSummary.ContainsKey("recommendedDecision")) { $vendorSummary["recommendedDecision"] } else { "" }
$vendorAction = if ($vendorSummary.ContainsKey("requiredAction")) { $vendorSummary["requiredAction"] } else { "" }
$gateStatus = if ($gateSummary.ContainsKey("status")) { $gateSummary["status"] } elseif ($evidencePathIssues.Count -gt 0) { "NOT_RUN" } else { "FAIL" }
$failedCount = if ($gateSummary.ContainsKey("failedCount")) { $gateSummary["failedCount"] } else { "" }
$pendingCount = if ($gateSummary.ContainsKey("pendingCount")) { $gateSummary["pendingCount"] } else { "" }

$childFailures = @()
foreach ($evidencePathIssue in $evidencePathIssues) {
    $childFailures += $evidencePathIssue
}
if ($factoryCheck.exitCode -ne 0) {
    $childFailures += "factory-feedback exit=$($factoryCheck.exitCode)"
}
if ($vendorCheck.exitCode -ne 0) {
    $childFailures += "vendor-permission exit=$($vendorCheck.exitCode)"
}
if ($gateCheck.exitCode -ne 0 -and -not ($gateCheck.exitCode -eq 2 -and $gateStatus -eq "PENDING")) {
    $childFailures += "factory-pilot-gate exit=$($gateCheck.exitCode)"
}

$overallStatus = if ($childFailures.Count -gt 0) {
    "FAIL"
} elseif ($gateStatus -eq "FAIL") {
    "FAIL"
} elseif ($gateStatus -eq "PENDING") {
    "PENDING"
} elseif ($gateStatus -eq "PASS") {
    "PASS"
} else {
    "FAIL"
}

$result = [pscustomobject]@{
    status = $overallStatus
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    factoryFeedbackPath = $factoryFeedback.Path
    vendorPermissionPath = $vendorPermission.Path
    evidenceRootPath = $evidenceRootPath
    copiedInputDir = $inputDir
    factoryConclusion = $factoryConclusion
    factoryAction = $factoryAction
    vendorDecision = $vendorDecision
    vendorAction = $vendorAction
    gateStatus = $gateStatus
    failedCount = $failedCount
    pendingCount = $pendingCount
    evidencePathCheckCount = $evidencePathChecks.Count
    evidencePathIssueCount = $evidencePathIssues.Count
    evidencePathChecks = $evidencePathChecks
    childFailures = $childFailures
    evidence = [pscustomobject]@{
        factoryFeedback = $factoryOutputRoot
        vendorPermission = $vendorOutputRoot
        factoryPilotGate = $gateOutputRoot
    }
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-intake.json") -Encoding utf8

$summary = @"
status=$overallStatus
checkedAt=$($result.checkedAt)
outputDir=$outputDir
factoryFeedbackPath=$($factoryFeedback.Path)
vendorPermissionPath=$($vendorPermission.Path)
evidenceRootPath=$evidenceRootPath
factoryConclusion=$factoryConclusion
factoryAction=$factoryAction
vendorDecision=$vendorDecision
vendorAction=$vendorAction
gateStatus=$gateStatus
failedCount=$failedCount
pendingCount=$pendingCount
evidencePathCheckCount=$($evidencePathChecks.Count)
evidencePathIssueCount=$($evidencePathIssues.Count)
childFailures=$($childFailures -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($overallStatus -eq "FAIL") {
    exit 1
}
if ($overallStatus -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
