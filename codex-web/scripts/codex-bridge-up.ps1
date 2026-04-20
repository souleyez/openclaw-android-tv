param(
  [string]$ServerHost = 'root@1.12.246.48',
  [int]$LocalPort = 33980,
  [int]$RemotePort = 33990,
  [string]$TokenFile = '.storage/codex-bridge-token.txt'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$storageDir = Join-Path $repoRoot '.storage'
$stateFile = Join-Path $storageDir 'codex-bridge-processes.json'
$tokenPath = Join-Path $repoRoot $TokenFile
$bridgeLog = Join-Path $storageDir 'codex-bridge-server.log'
$bridgeErr = Join-Path $storageDir 'codex-bridge-server.err.log'
$tunnelLog = Join-Path $storageDir 'codex-bridge-tunnel.log'
$tunnelErr = Join-Path $storageDir 'codex-bridge-tunnel.err.log'

New-Item -ItemType Directory -Force $storageDir | Out-Null

if (-not (Test-Path $tokenPath)) {
  $token = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
  Set-Content -Path $tokenPath -Value $token -Encoding utf8
}

$token = (Get-Content $tokenPath -Raw).Trim()

foreach ($file in @($bridgeLog, $bridgeErr, $tunnelLog, $tunnelErr)) {
  if (Test-Path $file) {
    Remove-Item -Force $file
  }
}

$bridgeCommand = "set CODEX_BRIDGE_TOKEN=$token && set CODEX_BRIDGE_PORT=$LocalPort && cd /d `"$repoRoot`" && node scripts\\codex-bridge-server.mjs"
$bridgeProc = Start-Process -FilePath cmd.exe `
  -ArgumentList '/c', $bridgeCommand `
  -WorkingDirectory $repoRoot `
  -WindowStyle Hidden `
  -RedirectStandardOutput $bridgeLog `
  -RedirectStandardError $bridgeErr `
  -PassThru

Start-Sleep -Seconds 2

$tunnelCommand = "ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ProxyJump=none -R 127.0.0.1:$RemotePort`:127.0.0.1:$LocalPort $ServerHost"
$tunnelProc = Start-Process -FilePath cmd.exe `
  -ArgumentList '/c', $tunnelCommand `
  -WorkingDirectory $repoRoot `
  -WindowStyle Hidden `
  -RedirectStandardOutput $tunnelLog `
  -RedirectStandardError $tunnelErr `
  -PassThru

$state = @{
  token = $token
  serverHost = $ServerHost
  localPort = $LocalPort
  remotePort = $RemotePort
  bridgePid = $bridgeProc.Id
  tunnelPid = $tunnelProc.Id
  startedAt = (Get-Date).ToString('o')
}

$state | ConvertTo-Json | Set-Content -Path $stateFile -Encoding utf8
$state | ConvertTo-Json
