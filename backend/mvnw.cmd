<# : batch portion
@echo off
setlocal
set "__MVNW_ARG0_NAME__=%~nx0"
for /f "usebackq tokens=1* delims==" %%A in (`powershell -noprofile "& {$scriptDir='%~dp0'; $script='%__MVNW_ARG0_NAME__%'; icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw '%~f0'))) -NoNewScope}"`) do @(
  if "%%A"=="MVN_CMD" (set "__MVNW_CMD__=%%B") else if "%%B"=="" (echo %%A) else (echo %%A=%%B)
)
if not "%__MVNW_CMD__%"=="" "%__MVNW_CMD__%" %*
echo Cannot start Maven from wrapper 1>&2
exit /b 1
: end batch / begin powershell #>

$ErrorActionPreference = 'Stop'
$propertiesPath = Join-Path $scriptDir '.mvn/wrapper/maven-wrapper.properties'
$distributionUrl = (Get-Content -Raw $propertiesPath | ConvertFrom-StringData).distributionUrl
if (!$distributionUrl) { throw "Missing distributionUrl in $propertiesPath" }

$archiveName = $distributionUrl -replace '^.*/', ''
$distributionName = $archiveName -replace '\.zip$', '' -replace '-bin$', ''
$mavenUserHome = if ($env:MAVEN_USER_HOME) {
  $env:MAVEN_USER_HOME
} elseif ($env:USERPROFILE) {
  Join-Path $env:USERPROFILE '.m2'
} else {
  Join-Path $HOME '.m2'
}
$distributionRoot = Join-Path $mavenUserHome "wrapper/dists/$($archiveName -replace '\.zip$')"
$existingHome = Get-ChildItem -Path $distributionRoot -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName $distributionName } |
    Where-Object { Test-Path (Join-Path $_ 'bin/mvn.cmd') } |
    Select-Object -First 1
$hash = ([System.Security.Cryptography.MD5]::Create().ComputeHash([byte[]][char[]]$distributionUrl) | ForEach-Object { $_.ToString('x2') }) -join ''
$mavenHome = if ($existingHome) { $existingHome } else { Join-Path $distributionRoot "$hash/$distributionName" }
$mvnCommand = Join-Path $mavenHome 'bin/mvn.cmd'

if (!(Test-Path $mvnCommand)) {
  $temporaryArchive = Join-Path ([System.IO.Path]::GetTempPath()) $archiveName
  try {
    Invoke-WebRequest -UseBasicParsing -Uri $distributionUrl -OutFile $temporaryArchive
    $distributionParent = Split-Path $mavenHome -Parent
    New-Item -ItemType Directory -Force -Path $distributionParent | Out-Null
    $expanded = Join-Path ([System.IO.Path]::GetTempPath()) ("maven-wrapper-" + [guid]::NewGuid())
    Expand-Archive -Path $temporaryArchive -DestinationPath $expanded -Force
    Move-Item -Path (Join-Path $expanded $distributionName) -Destination $mavenHome -Force
  } finally {
    Remove-Item -LiteralPath $temporaryArchive -Force -ErrorAction SilentlyContinue
    if ($expanded) { Remove-Item -LiteralPath $expanded -Force -Recurse -ErrorAction SilentlyContinue }
  }
}

Write-Output "MVN_CMD=$mvnCommand"
