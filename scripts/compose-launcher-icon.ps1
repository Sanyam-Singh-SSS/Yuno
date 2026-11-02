# Adaptive launcher assets from the master square icon.
# Launchers mask the center of the 108dp layer (about 66dp) and scale the
# foreground for parallax, so a full-bleed foreground reads zoomed. The finished
# icon sits on the background, inside that safe zone. The foreground is empty.
# Usage: .\scripts\compose-launcher-icon.ps1 [-Source path\to\icon.png]
param(
    [string]$Source = "$PSScriptRoot\..\app\src\main\ic_launcher-playstore.png"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$res = Join-Path $PSScriptRoot "..\app\src\main\res"
# Fraction of the 108dp layer the finished icon occupies. 66/108 is the safe
# zone; a little under that keeps the mark inside the mask when it parallax-shifts.
$IconScale = 0.60
# Themed-icon mark, as a fraction of the layer (BitChord's logo lands near 46%).
$MonoScale = 0.46

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
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($master, 0, 0, $size, $size)
    $g.Dispose()
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

if (-not (Test-Path $Source)) { throw "Missing source icon: $Source" }

$master = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Source))
$w = $master.Width
$h = $master.Height

# White-mark bounds, so the themed icon can be sized from the glyph rather than the square.
$minX = $w; $minY = $h; $maxX = 0; $maxY = 0
for ($y = 0; $y -lt $h; $y += 2) {
    for ($x = 0; $x -lt $w; $x += 2) {
        $c = $master.GetPixel($x, $y)
        if ($c.R -gt 200 -and $c.G -gt 200 -and $c.B -gt 200) {
            if ($x -lt $minX) { $minX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
$glyphW = [Math]::Max(1, $maxX - $minX)

# Background: the whole icon, centered, with its own edge colors continued
# out to the canvas so the mask doesn't reveal a second rectangle.
$bgSize = 512
$bg = New-Object System.Drawing.Bitmap $bgSize, $bgSize
$inner = [int][Math]::Round($bgSize * $IconScale)
$ox = [int](($bgSize - $inner) / 2)
$oy = $ox
$scaled = New-Object System.Drawing.Bitmap $inner, $inner
$sg = [System.Drawing.Graphics]::FromImage($scaled)
$sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$sg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$sg.DrawImage($master, 0, 0, $inner, $inner)
$sg.Dispose()
for ($y = 0; $y -lt $bgSize; $y++) {
    $sy = [Math]::Min($inner - 1, [Math]::Max(0, $y - $oy))
    for ($x = 0; $x -lt $bgSize; $x++) {
        $sx = [Math]::Min($inner - 1, [Math]::Max(0, $x - $ox))
        $bg.SetPixel($x, $y, $scaled.GetPixel($sx, $sy))
    }
}
$scaled.Dispose()
$bgPath = Join-Path $res "drawable-nodpi\ic_launcher_background.png"
$bg.Save($bgPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bg.Dispose()
Write-Host "Wrote background ($inner px icon on $bgSize)"

# Monochrome: white mark only, transparent everywhere else.
$mono = New-Object System.Drawing.Bitmap $bgSize, $bgSize, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$draw = [int][Math]::Round($bgSize * $MonoScale / ($glyphW / $w))
$mx = [int](($bgSize - $draw) / 2)
$my = $mx
$tmp = New-Object System.Drawing.Bitmap $draw, $draw
$tg = [System.Drawing.Graphics]::FromImage($tmp)
$tg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$tg.DrawImage($master, 0, 0, $draw, $draw)
$tg.Dispose()
for ($y = 0; $y -lt $draw; $y++) {
    for ($x = 0; $x -lt $draw; $x++) {
        $c = $tmp.GetPixel($x, $y)
        if ($c.R -gt 200 -and $c.G -gt 200 -and $c.B -gt 200) {
            $mono.SetPixel($mx + $x, $my + $y, [System.Drawing.Color]::FromArgb(255, 255, 255, 255))
        }
    }
}
$tmp.Dispose()
$monoPath = Join-Path $res "drawable-nodpi\ic_launcher_monochrome.png"
$mono.Save($monoPath, [System.Drawing.Imaging.ImageFormat]::Png)
$mono.Dispose()
Write-Host "Wrote monochrome"

foreach ($entry in $mipmapIcons.GetEnumerator()) {
    $folder = $entry.Key
    $size = $entry.Value
    Save-ScaledPng $master $size (Join-Path $res "$folder\ic_launcher.png")
    Save-ScaledPng $master $size (Join-Path $res "$folder\ic_launcher_round.png")
    Write-Host "Wrote $folder mipmap ($size px)"
}

$master.Dispose()

# Density foreground/monochrome PNGs override the safe-zone layers. Drop them.
Get-ChildItem $res -Recurse -Include "ic_launcher_foreground.png", "ic_launcher_monochrome.png" |
    Where-Object { $_.Directory.Name -ne "drawable-nodpi" } |
    ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "Removed $($_.FullName)" }

$oldBg = Join-Path $res "drawable\ic_launcher_background.xml"
if (Test-Path $oldBg) { Remove-Item $oldBg -Force; Write-Host "Removed gradient background xml" }

Write-Host "Done."
