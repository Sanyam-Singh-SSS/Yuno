# Regenerate launcher PNGs from a 512x512 master (same layout as Velora).
# Usage: .\scripts\compose-launcher-icon.ps1 [-Source path\to\icon.png]
param(
    [string]$Source = "$PSScriptRoot\..\app\src\main\ic_launcher-playstore.png"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$res = Join-Path $PSScriptRoot "..\app\src\main\res"
$adaptiveLayers = @{
    "drawable-mdpi"    = 108
    "drawable-hdpi"    = 162
    "drawable-xhdpi"   = 216
    "drawable-xxhdpi"  = 324
    "drawable-xxxhdpi" = 432
}
# Legacy @mipmap/ic_launcher fallbacks (Velora keeps webp here; PNG links the same).
$mipmapIcons = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

function Save-ScaledPng([System.Drawing.Image]$master, [int]$size, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($master, 0, 0, $size, $size)
    $g.Dispose()
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

function New-MonochromeMaster([System.Drawing.Bitmap]$src) {
    $w = $src.Width
    $h = $src.Height
    $mono = New-Object System.Drawing.Bitmap $w, $h
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = $src.GetPixel($x, $y)
            $lum = 0.2126 * $c.R + 0.7152 * $c.G + 0.0722 * $c.B
            if ($lum -gt 180) {
                $mono.SetPixel($x, $y, [System.Drawing.Color]::White)
            } else {
                $mono.SetPixel($x, $y, [System.Drawing.Color]::Black)
            }
        }
    }
    return $mono
}

if (-not (Test-Path $Source)) { throw "Missing source icon: $Source" }

$master = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Source))
if ($master.Width -ne 512 -or $master.Height -ne 512) {
    Write-Warning "Expected 512x512; got $($master.Width)x$($master.Height) — scaling anyway."
}

$monoMaster = New-MonochromeMaster $master

foreach ($entry in $adaptiveLayers.GetEnumerator()) {
    $folder = $entry.Key
    $size = $entry.Value
    $fg = Join-Path $res "$folder\ic_launcher_foreground.png"
    $mc = Join-Path $res "$folder\ic_launcher_monochrome.png"
    Save-ScaledPng $master $size $fg
    Save-ScaledPng $monoMaster $size $mc
    Write-Host "Wrote $folder ($size px)"
}

foreach ($entry in $mipmapIcons.GetEnumerator()) {
    $folder = $entry.Key
    $size = $entry.Value
    $icon = Join-Path $res "$folder\ic_launcher.png"
    $round = Join-Path $res "$folder\ic_launcher_round.png"
    Save-ScaledPng $master $size $icon
    Save-ScaledPng $master $size $round
    Write-Host "Wrote $folder mipmap ($size px)"
}

$master.Dispose()
$monoMaster.Dispose()
Write-Host "Done."
