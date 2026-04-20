$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$stateFile = Join-Path $repoRoot '.storage/codex-bridge-processes.json'

if (-not (Test-Path $stateFile)) {
  Write-Output 'codex bridge state file not found'
  exit 0
}

$state = Get-Content $stateFile -Raw | ConvertFrom-Json

foreach ($pid in @($state.bridgePid, $state.tunnelPid)) {
  if ($pid) {
    try {
      Stop-Process -Id $pid -Force -ErrorAction Stop
    } catch {
      Write-Output "process $pid already stopped"
    }
  }
}

Remove-Item -Force $stateFile
Write-Output 'codex bridge stopped'
