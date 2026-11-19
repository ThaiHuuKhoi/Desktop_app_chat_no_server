# Dong goi client thanh ban chay ngay cho Windows (khong can cai Java / Maven):
#   dist\ChatP2P\ChatP2P.exe  (+ Java runtime di kem)  va  dist\ChatP2P-windows.zip
#
# Yeu cau: JDK 17+ co jpackage (JDK 22 dang dung), Maven.
# Neu da cai WiX Toolset 3 thi doi -Type msi de ra file cai dat .msi.
#
# Chay tu thu muc goc repo:  powershell -ExecutionPolicy Bypass -File packaging\build-windows.ps1

param(
    [string]$Type = "app-image",
    [string]$AppVersion = "1.0.0",
    [string]$Mvn = "mvn"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "1/4 Build cac module (bo qua test)..."
& $Mvn -q "-Dmaven.test.skip=true" install
if ($LASTEXITCODE -ne 0) { throw "Build that bai" }

Write-Host "2/4 Gom thu vien cua client..."
$libs = Join-Path $root "client-javafx\target\package-input"
if (Test-Path $libs) { Remove-Item -Recurse -Force $libs }
& $Mvn -q -pl client-javafx dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$libs"
if ($LASTEXITCODE -ne 0) { throw "Copy thu vien that bai" }
Copy-Item (Get-ChildItem "client-javafx\target\client-javafx-*.jar" | Where-Object { $_.Name -notlike "*sources*" } | Select-Object -First 1).FullName $libs
$mainJar = (Get-ChildItem "$libs\client-javafx-*.jar" | Select-Object -First 1).Name

Write-Host "3/4 jpackage ($Type)..."
$dist = Join-Path $root "dist"
if (Test-Path "$dist\ChatP2P") { Remove-Item -Recurse -Force "$dist\ChatP2P" }
New-Item -ItemType Directory -Force $dist | Out-Null
$jpackageArgs = @(
    "--type", $Type,
    "--name", "ChatP2P",
    "--app-version", $AppVersion,
    "--vendor", "DATN Chat P2P",
    "--description", "Chat P2P ma hoa dau-cuoi",
    "--input", $libs,
    "--main-jar", $mainJar,
    "--main-class", "com.datn.chatp2p.client.Launcher",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--dest", $dist
)
if ($Type -ne "app-image") { $jpackageArgs += @("--win-menu", "--win-shortcut", "--win-dir-chooser") }
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage that bai" }

if ($Type -eq "app-image") {
    Write-Host "4/4 Nen thanh zip..."
    $zip = Join-Path $dist "ChatP2P-windows.zip"
    if (Test-Path $zip) { Remove-Item -Force $zip }
    # tar.exe co san tu Windows 10; thu lai vai lan vi OneDrive/antivirus hay khoa tam file vua tao.
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        & tar.exe -a -c -f $zip -C $dist ChatP2P
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 3
    }
    if ($LASTEXITCODE -ne 0) { throw "Nen zip that bai" }
    Write-Host "Xong: $dist\ChatP2P\ChatP2P.exe  va  $zip"
} else {
    Write-Host "Xong: file cai dat trong $dist"
}
