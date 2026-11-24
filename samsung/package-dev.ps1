$ErrorActionPreference = "Stop"
if (-not $env:TIZEN_PROFILE) { throw "Set TIZEN_PROFILE to your Samsung certificate profile" }
$stage = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
New-Item -ItemType Directory $stage | Out-Null
try {
  foreach ($f in @("config.xml","index.html","receiver.js")) { Copy-Item (Join-Path $PSScriptRoot $f) $stage }
  & tizen package -t wgt -s $env:TIZEN_PROFILE -- $stage
  if ($LASTEXITCODE -ne 0) { throw "Tizen packaging failed" }
  $dist = Join-Path $PSScriptRoot "dist"
  New-Item -Force -ItemType Directory $dist | Out-Null
  $packages = @(Get-ChildItem $stage -Filter *.wgt)
  if ($packages.Count -eq 0) { throw "No WGT generated" }
  $packages | Copy-Item -Destination $dist
} finally { Remove-Item -Recurse -Force $stage }
