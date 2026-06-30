param(
    [string]$LedgerPath = "",
    [string]$OutputRoot = "",
    [string]$HomeRepoRoot = "C:\Users\soulzyn\Desktop\codex\home",
    [string]$CodexAutomationRoot = "",
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

function Normalize-Cell {
    param([string]$Value)
    return ([string]$Value).Trim().Trim("`r", "`n")
}

function Split-MarkdownRow {
    param([string]$Line)
    $trimmed = $Line.Trim()
    if ($trimmed.StartsWith("|")) {
        $trimmed = $trimmed.Substring(1)
    }
    if ($trimmed.EndsWith("|")) {
        $trimmed = $trimmed.Substring(0, $trimmed.Length - 1)
    }
    return @($trimmed -split "\|" | ForEach-Object { Normalize-Cell -Value $_ })
}

function Test-PendingStatus {
    param([string]$Status)
    $normalized = ([string]$Status).ToLowerInvariant()
    return $normalized.Contains("pending") -or
        $normalized.Contains("unknown") -or
        $normalized.Contains("not started") -or
        $normalized.Contains("partial")
}

function Get-CodeSpanValues {
    param([string]$Value)

    $matches = [regex]::Matches([string]$Value, '`([^`]+)`')
    return @($matches | ForEach-Object { $_.Groups[1].Value.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Test-LocalEvidenceReference {
    param([string]$Reference)

    $value = ([string]$Reference).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $false
    }
    if ($value -match '^(https?://|GET\s+|POST\s+|PUT\s+|DELETE\s+|PATCH\s+|/api/|[0-9a-fA-F]{7,40}$)') {
        return $false
    }
    if ($value -match '^[A-Za-z]:[\\/]') {
        return $true
    }
    return $value -match '^(docs|scripts|apps|artifacts|home)[\\/]'
}

function Resolve-LocalEvidenceReference {
    param(
        [string]$Reference,
        [string]$RepoRoot,
        [string]$HomeRoot
    )

    $value = ([string]$Reference).Trim()
    if ($value -match '^[A-Za-z]:[\\/]') {
        return $value
    }
    $normalized = $value.Replace("/", [System.IO.Path]::DirectorySeparatorChar)
    if ($normalized -like "home$([System.IO.Path]::DirectorySeparatorChar)*") {
        $relative = $normalized.Substring(("home" + [System.IO.Path]::DirectorySeparatorChar).Length)
        return Join-Path $HomeRoot $relative
    }
    return Join-Path $RepoRoot $normalized
}

function Test-HomeCommitReference {
    param(
        [string]$Commit,
        [string]$HomeRoot
    )

    if ([string]::IsNullOrWhiteSpace($HomeRoot) -or -not (Test-Path -LiteralPath $HomeRoot)) {
        return [pscustomobject]@{
            commit = $Commit
            homeRoot = $HomeRoot
            exists = $false
            detail = "home repo root missing"
        }
    }

    $output = & git -C $HomeRoot cat-file -e "$Commit^{commit}" 2>&1
    $exitCode = $LASTEXITCODE
    return [pscustomobject]@{
        commit = $Commit
        homeRoot = $HomeRoot
        exists = $exitCode -eq 0
        detail = if ($exitCode -eq 0) { "commit exists" } else { (($output | Out-String).Trim()) }
    }
}

function Test-CodexAutomationReference {
    param(
        [string]$AutomationId,
        [string]$AutomationRoot
    )

    $automationPath = Join-Path $AutomationRoot "$AutomationId\automation.toml"
    $exists = Test-Path -LiteralPath $automationPath
    $status = ""
    if ($exists) {
        $statusLine = Get-Content -LiteralPath $automationPath |
            Where-Object { $_ -match '^status\s*=' } |
            Select-Object -First 1
        if ($statusLine) {
            $status = (($statusLine -replace '^status\s*=\s*', '').Trim().Trim('"'))
        }
    }

    return [pscustomobject]@{
        automationId = $AutomationId
        automationPath = $automationPath
        exists = $exists
        status = $status
        active = $exists -and $status -eq "ACTIVE"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($LedgerPath)) {
    $LedgerPath = Join-Path $repoRoot "docs\testing\2026-06-30-android-tv-production-readiness.md"
}
if ([string]::IsNullOrWhiteSpace($CodexAutomationRoot)) {
    $CodexAutomationRoot = Join-Path $HOME ".codex\automations"
}
$ledgerFile = Resolve-Path -Path $LedgerPath

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\readiness-ledger-checks\readiness-ledger-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$requiredRows = @(
    "Factory fresh install",
    "Default Home persistence",
    "Cold boot",
    "Restore factory behavior",
    "OTA one-device canary",
    "OTA expanded rollout",
    "Payment renewal",
    "Ad publish and render",
    "iPhone casting",
    "Xiaomi casting",
    "Low-memory soak",
    "No-ADB support evidence",
    "Server health and cert renewal",
    "Rollback drill"
)
$requiredColumns = @("Gate", "Status", "Evidence Path", "Owner", "Blocker", "Decision")

$lines = Get-Content -LiteralPath $ledgerFile.Path
$inRowsSection = $false
$tableLines = @()
foreach ($line in $lines) {
    if ($line -match "^##\s+Readiness Rows\s*$") {
        $inRowsSection = $true
        continue
    }
    if ($inRowsSection -and $line -match "^##\s+") {
        break
    }
    if ($inRowsSection -and $line.Trim().StartsWith("|")) {
        $tableLines += $line
    }
}

$header = @()
$rows = @{}
foreach ($line in $tableLines) {
    $cells = Split-MarkdownRow -Line $line
    if ($cells.Count -lt 2) {
        continue
    }
    $first = $cells[0]
    if ($first -eq "Gate") {
        $header = $cells
        continue
    }
    if ($first -match "^-+$") {
        continue
    }
    if ($header.Count -eq 0) {
        continue
    }
    $row = [ordered]@{}
    for ($i = 0; $i -lt $header.Count; $i++) {
        $value = if ($i -lt $cells.Count) { $cells[$i] } else { "" }
        $row[$header[$i]] = $value
    }
    if (-not [string]::IsNullOrWhiteSpace($row["Gate"])) {
        $rows[$row["Gate"]] = [pscustomobject]$row
    }
}

$missingColumns = @($requiredColumns | Where-Object { $header -notcontains $_ })
$missingRows = @($requiredRows | Where-Object { -not $rows.ContainsKey($_) })
$rowIssues = New-Object System.Collections.ArrayList
$pendingRows = New-Object System.Collections.ArrayList
$evidenceReferenceChecks = New-Object System.Collections.ArrayList
$homeCommitChecks = New-Object System.Collections.ArrayList
$automationChecks = New-Object System.Collections.ArrayList

foreach ($requiredRow in $requiredRows) {
    if (-not $rows.ContainsKey($requiredRow)) {
        continue
    }
    $row = $rows[$requiredRow]
    foreach ($column in $requiredColumns) {
        $value = if ($row.PSObject.Properties.Name -contains $column) { [string]$row.$column } else { "" }
        if ([string]::IsNullOrWhiteSpace($value)) {
            [void]$rowIssues.Add([pscustomobject]@{
                gate = $requiredRow
                issue = "empty $column"
            })
        }
    }
    if (Test-PendingStatus -Status $row.Status) {
        [void]$pendingRows.Add([pscustomobject]@{
            gate = $requiredRow
            status = $row.Status
            blocker = $row.Blocker
            decision = $row.Decision
        })
    }

    $evidencePathValue = [string]$row."Evidence Path"
    foreach ($reference in (Get-CodeSpanValues -Value $evidencePathValue)) {
        if (-not (Test-LocalEvidenceReference -Reference $reference)) {
            continue
        }
        $resolvedReference = Resolve-LocalEvidenceReference -Reference $reference -RepoRoot $repoRoot -HomeRoot $HomeRepoRoot
        $exists = Test-Path -LiteralPath $resolvedReference
        [void]$evidenceReferenceChecks.Add([pscustomobject]@{
            gate = $requiredRow
            reference = $reference
            resolvedPath = $resolvedReference
            exists = $exists
        })
        if (-not $exists) {
            [void]$rowIssues.Add([pscustomobject]@{
                gate = $requiredRow
                issue = "missing evidence reference: $reference -> $resolvedReference"
            })
        }
    }

    foreach ($match in [regex]::Matches($evidencePathValue, '`home`\s+commit\s+`([0-9a-fA-F]{7,40})`')) {
        $commitCheck = Test-HomeCommitReference -Commit $match.Groups[1].Value -HomeRoot $HomeRepoRoot
        [void]$homeCommitChecks.Add([pscustomobject]@{
            gate = $requiredRow
            commit = $commitCheck.commit
            homeRoot = $commitCheck.homeRoot
            exists = $commitCheck.exists
            detail = $commitCheck.detail
        })
        if (-not $commitCheck.exists) {
            [void]$rowIssues.Add([pscustomobject]@{
                gate = $requiredRow
                issue = "missing home commit reference: $($commitCheck.commit)"
            })
        }
    }

    foreach ($match in [regex]::Matches($evidencePathValue, 'Codex automation\s+`([^`]+)`')) {
        $automationCheck = Test-CodexAutomationReference -AutomationId $match.Groups[1].Value -AutomationRoot $CodexAutomationRoot
        [void]$automationChecks.Add([pscustomobject]@{
            gate = $requiredRow
            automationId = $automationCheck.automationId
            automationPath = $automationCheck.automationPath
            exists = $automationCheck.exists
            status = $automationCheck.status
            active = $automationCheck.active
        })
        if (-not $automationCheck.exists) {
            [void]$rowIssues.Add([pscustomobject]@{
                gate = $requiredRow
                issue = "missing Codex automation: $($automationCheck.automationId)"
            })
        } elseif (-not $automationCheck.active) {
            [void]$rowIssues.Add([pscustomobject]@{
                gate = $requiredRow
                issue = "Codex automation not ACTIVE: $($automationCheck.automationId) status=$($automationCheck.status)"
            })
        }
    }
}

$status = if ($missingColumns.Count -gt 0 -or $missingRows.Count -gt 0 -or $rowIssues.Count -gt 0) {
    "FAIL"
} elseif ($pendingRows.Count -gt 0) {
    "PENDING"
} else {
    "PASS"
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    ledgerPath = $ledgerFile.Path
    outputDir = $outputDir
    requiredRowCount = $requiredRows.Count
    foundRowCount = $rows.Count
    missingColumns = $missingColumns
    missingRows = $missingRows
    rowIssues = @($rowIssues)
    pendingRows = @($pendingRows)
    evidenceReferenceChecks = @($evidenceReferenceChecks)
    homeCommitChecks = @($homeCommitChecks)
    automationChecks = @($automationChecks)
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "production-readiness-ledger.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
ledgerPath=$($ledgerFile.Path)
outputDir=$outputDir
requiredRowCount=$($requiredRows.Count)
foundRowCount=$($rows.Count)
missingColumns=$($missingColumns -join ",")
missingRows=$($missingRows -join ",")
rowIssueCount=$($rowIssues.Count)
pendingRowCount=$($pendingRows.Count)
evidenceReferenceCheckCount=$($evidenceReferenceChecks.Count)
homeCommitCheckCount=$($homeCommitChecks.Count)
automationCheckCount=$($automationChecks.Count)
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
if ($status -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
