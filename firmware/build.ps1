<#
.SYNOPSIS
    Compiles the dropController V3 firmware and optionally uploads it.

.DESCRIPTION
    The firmware is a multi-file Arduino sketch in this folder. It is built with
    the arduino-cli.exe bundled with Arduino IDE 2.x.

    The private arduino-cli data folder contains the AVR core and toolchain. It
    is prepared once by setup-toolchain.ps1 and reused afterwards; that keeps the
    build independent from the IDE's normal Arduino15 folder and works offline.

    "Discovery not found" warnings are expected: the IDE bundle does not ship
    the serial/mDNS discovery helpers, and they are not needed to compile or to
    upload to a port given with -Port.

.PARAMETER IdeHome
    Arduino IDE 2.x installation folder. Defaults to
    C:\Program Files\Arduino IDE.

.PARAMETER DataDir
    Private arduino-cli data folder prepared by setup-toolchain.ps1. Defaults to
    %LOCALAPPDATA%\DropControllerArduino\cli-data.

.PARAMETER Cpu
    atmega328old for a Nano with the old bootloader (57600 baud upload),
    atmega328 for the new bootloader (115200 baud upload).

.PARAMETER Upload
    Upload the compiled firmware after a successful build.

.PARAMETER Port
    Serial port of the device, for example COM5. Required with -Upload.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File firmware/build.ps1

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File firmware/build.ps1 -Upload -Port COM5
#>
[CmdletBinding()]
param(
    [string]$IdeHome = $(if ($env:ARDUINO_IDE_HOME) {
        $env:ARDUINO_IDE_HOME
    } else {
        'C:\Program Files\Arduino IDE'
    }),
    [string]$DataDir = $(if ($env:DC3_ARDUINO_DATA) {
        $env:DC3_ARDUINO_DATA
    } else {
        Join-Path (Join-Path $env:LOCALAPPDATA 'DropControllerArduino') 'cli-data'
    }),
    [ValidateSet('atmega328', 'atmega328old')]
    [string]$Cpu = 'atmega328old',
    [switch]$Upload,
    [string]$Port
)

$ErrorActionPreference = 'Stop'

$sketchName = 'dropControllerV3_dC3_AS25006'
$sketchDir = Join-Path $PSScriptRoot $sketchName
$buildDir = Join-Path $PSScriptRoot 'build'
$cli = Join-Path $IdeHome 'resources\app\lib\backend\resources\arduino-cli.exe'

if (-not (Test-Path $cli)) {
    throw "arduino-cli.exe not found under '$IdeHome'. Pass -IdeHome with the Arduino IDE 2.x folder."
}
if (-not (Test-Path (Join-Path $sketchDir "$sketchName.ino"))) {
    throw "Sketch not found: $sketchDir"
}
if ($Upload -and -not $Port) {
    throw 'Use -Port COMx together with -Upload.'
}

$avrCoreDir = Join-Path $DataDir 'packages\arduino\hardware\avr'
$avrCore = Get-ChildItem $avrCoreDir -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'platform.txt') } |
    Select-Object -First 1
if (-not $avrCore) {
    $setup = Join-Path $PSScriptRoot 'setup-toolchain.ps1'
    Write-Host 'Preparing the AVR toolchain (first run)...'
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & powershell -NoProfile -ExecutionPolicy Bypass -File $setup -IdeHome $IdeHome -DataDir $DataDir
    $setupCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
    if ($setupCode -ne 0) {
        throw 'Toolchain setup failed. Run firmware/setup-toolchain.ps1 manually, see firmware/README.md.'
    }
}

$env:ARDUINO_DIRECTORIES_DATA = $DataDir
$env:ARDUINO_DIRECTORIES_USER = Join-Path $DataDir 'user'

$fqbn = "arduino:avr:nano:cpu=$Cpu"
New-Item -ItemType Directory -Force $buildDir | Out-Null

function Invoke-ArduinoCli {
    param([string[]]$CliArguments)
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $cli @CliArguments | Out-Host
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
}

Write-Host "Compiling $sketchName (${Cpu})..."
$compileCode = Invoke-ArduinoCli @('compile', '--fqbn', $fqbn, '--build-path', $buildDir, $sketchDir)
if ($compileCode -ne 0) {
    throw "Firmware compilation failed (exit code $compileCode)."
}

$hexFile = Join-Path $buildDir "$sketchName.ino.hex"
if (-not $Upload) {
    Write-Host "Firmware compiled: $hexFile"
    return
}

Write-Host "Uploading to $Port..."
$uploadCode = Invoke-ArduinoCli @('upload', '--fqbn', $fqbn, '-p', $Port, '--input-dir', $buildDir)
if ($uploadCode -ne 0) {
    throw "Firmware upload failed (exit code $uploadCode)."
}
Write-Host 'Upload finished.'
