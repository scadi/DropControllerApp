<#
.SYNOPSIS
    Prepares the offline AVR toolchain used by firmware/build.ps1.

.DESCRIPTION
    Arduino IDE 2.x ships arduino-cli.exe but no AVR core, and the IDE normally
    downloads the core from downloads.arduino.cc, which returns HTTP 403 on some
    networks. This script creates a private arduino-cli data directory and links
    the AVR core, avr-gcc, avrdude and ctags from an Arduino IDE 1.8.x toolchain
    into it, so build.ps1 can compile offline.

    If downloads.arduino.cc is reachable, the script falls back to the standard
    `arduino-cli core install arduino:avr`.

    Run this once. It is idempotent.

.PARAMETER IdeHome
    Arduino IDE 2.x installation folder. Defaults to
    C:\Program Files\Arduino IDE.

.PARAMETER SourceToolchain
    Folder with an Arduino IDE 1.8.x `hardware\` and `tools-builder\` tree.
    Defaults to %LOCALAPPDATA%\DropControllerArduino. When the folder is
    missing, the script copies the tree from the Arduino IDE package installed
    from the Microsoft Store.

.PARAMETER DataDir
    Private arduino-cli data folder. Defaults to
    %LOCALAPPDATA%\DropControllerArduino\cli-data.
#>
[CmdletBinding()]
param(
    [string]$IdeHome = $(if ($env:ARDUINO_IDE_HOME) {
        $env:ARDUINO_IDE_HOME
    } else {
        'C:\Program Files\Arduino IDE'
    }),
    [string]$SourceToolchain = $(if ($env:DC3_ARDUINO_SOURCE) {
        $env:DC3_ARDUINO_SOURCE
    } else {
        Join-Path $env:LOCALAPPDATA 'DropControllerArduino'
    }),
    [string]$DataDir = $(if ($env:DC3_ARDUINO_DATA) {
        $env:DC3_ARDUINO_DATA
    } else {
        Join-Path (Join-Path $env:LOCALAPPDATA 'DropControllerArduino') 'cli-data'
    })
)

$ErrorActionPreference = 'Stop'

$cli = Join-Path $IdeHome 'resources\app\lib\backend\resources\arduino-cli.exe'
if (-not (Test-Path $cli)) {
    throw "arduino-cli.exe not found under '$IdeHome'. Pass -IdeHome with the Arduino IDE 2.x folder."
}

$avrCore = Join-Path $SourceToolchain 'hardware\arduino\avr'
$ctags = Join-Path $SourceToolchain 'tools-builder\ctags\5.8-arduino11'
if (-not (Test-Path $avrCore) -or -not (Test-Path $ctags)) {
    $package = Get-AppxPackage -Name ArduinoLLC.ArduinoIDE -ErrorAction SilentlyContinue
    if (-not $package) {
        Write-Host 'No local AVR core found, trying the standard arduino-cli download...'
        $env:ARDUINO_DIRECTORIES_DATA = $DataDir
        $env:ARDUINO_DIRECTORIES_USER = Join-Path $DataDir 'user'
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $cli core install arduino:avr
        $installCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
        if ($installCode -ne 0) {
            throw 'The AVR core could not be installed. Set -SourceToolchain to an Arduino IDE 1.8.x folder.'
        }
        Write-Host "AVR core installed in $DataDir"
        return
    }

    Write-Host "Copying the AVR toolchain from $($package.InstallLocation)..."
    New-Item -ItemType Directory -Force $SourceToolchain | Out-Null
    robocopy "$($package.InstallLocation)\hardware" (Join-Path $SourceToolchain 'hardware') /E /NFL /NDL /NJH /NJS /NP | Out-Null
    robocopy "$($package.InstallLocation)\tools-builder" (Join-Path $SourceToolchain 'tools-builder') /E /NFL /NDL /NJH /NJS /NP | Out-Null
}

New-Item -ItemType Directory -Force $DataDir | Out-Null
New-Item -ItemType Directory -Force (Join-Path $DataDir 'user') | Out-Null

'{"packages":[]}' | Set-Content (Join-Path $DataDir 'package_index.json') -Encoding ASCII
'{"libraries":[]}' | Set-Content (Join-Path $DataDir 'library_index.json') -Encoding ASCII
'{}' | Set-Content (Join-Path $DataDir 'inventory.yaml') -Encoding ASCII

$links = @(
    @{
        Path = Join-Path $DataDir 'packages\arduino\hardware\avr\1.8.6'
        Target = $avrCore
    },
    @{
        Path = Join-Path $DataDir 'packages\arduino\tools\avr-gcc\7.3.0-atmel3.6.1-arduino7'
        Target = Join-Path $SourceToolchain 'hardware\tools\avr'
    },
    @{
        Path = Join-Path $DataDir 'packages\arduino\tools\avrdude\6.3.0-arduino17'
        Target = Join-Path $SourceToolchain 'hardware\tools\avr'
    },
    @{
        Path = Join-Path $DataDir 'packages\builtin\tools\ctags\5.8-arduino11'
        Target = $ctags
    }
)

foreach ($link in $links) {
    New-Item -ItemType Directory -Force (Split-Path $link.Path) | Out-Null
    if (-not (Test-Path $link.Path)) {
        New-Item -ItemType Junction -Path $link.Path -Target $link.Target | Out-Null
    }
}

$installedCore = Get-ChildItem (Join-Path $DataDir 'packages\arduino\hardware\avr') -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'platform.txt') } |
    Select-Object -First 1
if (-not $installedCore) {
    throw 'Toolchain setup failed: the AVR core is missing.'
}
Write-Host "Toolchain ready in $DataDir"
