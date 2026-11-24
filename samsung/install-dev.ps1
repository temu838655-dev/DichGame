param([Parameter(Mandatory=$true)][string]$Serial,[Parameter(Mandatory=$true)][string]$Package)
$ErrorActionPreference = "Stop"
$file = Get-Item -LiteralPath $Package
if ($file.Extension -ne ".wgt") { throw "Provide an existing signed WGT" }
$devices = & sdb devices
if ($LASTEXITCODE -ne 0) { throw "sdb devices failed" }
if (-not ($devices | Where-Object { $_ -match ("^" + [regex]::Escape($Serial) + "\s+device\b") })) { throw "Target is not connected" }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($file.FullName)
try {
  foreach ($name in @("config.xml","author-signature.xml","signature1.xml")) {
    if (-not $zip.GetEntry($name)) { throw "Signed WGT required" }
  }
  $reader = [IO.StreamReader]::new($zip.GetEntry("config.xml").Open())
  try { [xml]$config = $reader.ReadToEnd() } finally { $reader.Dispose() }
  $app = $config.SelectSingleNode("//*[local-name()='application' and namespace-uri()='http://tizen.org/ns/widgets']")
  if (-not $app -or $app.GetAttribute("id") -ne "DichGameTV.Receiver") { throw "Wrong application" }
} finally { $zip.Dispose() }
& tizen install -s $Serial -n $file.Name -- $file.DirectoryName
if ($LASTEXITCODE -ne 0) { throw "Install failed; verify certificate and TV Developer Mode" }
& tizen run -s $Serial -p DichGameTV.Receiver
if ($LASTEXITCODE -ne 0) { throw "Launch failed" }
