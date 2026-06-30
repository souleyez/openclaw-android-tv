param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,
    [string]$EvidenceRoot = "",
    [string]$OutputRoot = "",
    [switch]$RequireEvidenceRoot,
    [switch]$FailOnIncomplete
)

$ErrorActionPreference = "Stop"

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function Write-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )
    $Value | ConvertTo-Json -Depth 8 | Out-File -FilePath $Path -Encoding utf8
}

function Get-Field {
    param(
        [object]$Object,
        [string]$Name,
        [string]$Fallback = ""
    )
    if ($Object -and $Object.PSObject.Properties.Name -contains $Name) {
        $value = $Object.$Name
        if ($null -ne $value) {
            return ([string]$value).Trim()
        }
    }
    return $Fallback
}

function Test-SkipPathValue {
    param([string]$Value)
    $normalized = ([string]$Value).Trim().ToLowerInvariant()
    return [string]::IsNullOrWhiteSpace($normalized) -or
        @("not_tested", "untested", "n/a", "na", "none", "unknown") -contains $normalized
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

$manifestFile = Resolve-Path -Path $ManifestPath
$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\no-adb-diagnostic-checks\check-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$evidenceRootPath = ""
if (-not [string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $evidenceRootPath = (Resolve-Path -Path $EvidenceRoot).Path
}

$raw = Get-Content -Raw -Path $manifestFile
$manifest = $raw | ConvertFrom-Json

$requiredFields = @(
    "schema",
    "collectedAt",
    "collector",
    "diagnosticSource",
    "deviceModel",
    "firmwareVersion",
    "packageName",
    "packageVersionCode",
    "installLogPath",
    "homeStatePath",
    "castingLogPath",
    "otaStatePath",
    "crashOrAnrLogPath",
    "processSnapshotPath",
    "memorySnapshotPath"
)
$requiredPathFields = @(
    "installLogPath",
    "homeStatePath",
    "castingLogPath",
    "otaStatePath",
    "crashOrAnrLogPath",
    "processSnapshotPath",
    "memorySnapshotPath"
)

$missingFields = @()
foreach ($field in $requiredFields) {
    if ([string]::IsNullOrWhiteSpace((Get-Field -Object $manifest -Name $field))) {
        $missingFields += $field
    }
}

$schema = Get-Field -Object $manifest -Name "schema"
$schemaIssues = @()
if ($schema -ne "openclaw.android-tv.no-adb-diagnostic.v1") {
    $schemaIssues += "schema must be openclaw.android-tv.no-adb-diagnostic.v1"
}

$pathChecks = New-Object System.Collections.ArrayList
$pathIssues = @()
if ($RequireEvidenceRoot -and [string]::IsNullOrWhiteSpace($evidenceRootPath)) {
    $pathIssues += "EvidenceRoot is required to validate no-ADB diagnostic file paths"
} elseif (-not [string]::IsNullOrWhiteSpace($evidenceRootPath)) {
    foreach ($field in $requiredPathFields) {
        $value = Get-Field -Object $manifest -Name $field
        if (Test-SkipPathValue -Value $value) {
            continue
        }
        $pathCheck = Test-RelativeEvidencePath -Root $evidenceRootPath -PathValue $value
        [void]$pathChecks.Add([pscustomobject]@{
            field = $field
            value = $value
            ok = $pathCheck.ok
            resolvedPath = $pathCheck.resolvedPath
            issue = $pathCheck.issue
        })
        if (-not $pathCheck.ok) {
            $pathIssues += "$field`: $($pathCheck.issue)"
        }
    }
}

$status = if ($missingFields.Count -eq 0 -and $schemaIssues.Count -eq 0 -and $pathIssues.Count -eq 0) {
    "PASS"
} else {
    "INCOMPLETE"
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    manifestPath = $manifestFile.Path
    evidenceRootPath = $evidenceRootPath
    missingFields = $missingFields
    schemaIssues = $schemaIssues
    pathIssueCount = $pathIssues.Count
    pathIssues = $pathIssues
    pathChecks = @($pathChecks)
}
Write-JsonFile -Path (Join-Path $outputDir "no-adb-diagnostic-check.json") -Value $result

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
manifestPath=$($manifestFile.Path)
evidenceRootPath=$evidenceRootPath
missingFields=$($missingFields -join ",")
schemaIssues=$($schemaIssues -join "; ")
pathIssueCount=$($pathIssues.Count)
pathIssues=$($pathIssues -join "; ")
outputDir=$outputDir
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary
Write-Host $summary.Trim()

if ($FailOnIncomplete -and $status -ne "PASS") {
    exit 2
}
