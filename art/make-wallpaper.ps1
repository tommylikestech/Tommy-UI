$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
$assetRoot = Join-Path $PSScriptRoot 'assets'
[System.IO.Directory]::CreateDirectory($assetRoot) | Out-Null

# Original, procedural artwork: no external pictures, fonts or downloads.
$wall = [System.Drawing.Bitmap]::new(1080, 2400)
$g = [System.Drawing.Graphics]::FromImage($wall)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$rect = [System.Drawing.Rectangle]::new(0, 0, 1080, 2400)
$brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new($rect,
    [System.Drawing.Color]::FromArgb(11, 32, 32), [System.Drawing.Color]::FromArgb(29, 75, 62), 70)
$g.FillRectangle($brush, $rect)
$brush.Dispose()
# Off-centre rings leave the top and centre quiet behind home-screen labels.
foreach ($ring in @(@(-440,920,1640,30),@(-344,1016,1448,22),@(-248,1112,1256,20))) {
    $brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb($ring[3], 131, 216, 179))
    $g.FillEllipse($brush,[int]$ring[0],[int]$ring[1],[int]$ring[2],[int]$ring[2]); $brush.Dispose()
}
$line=[Drawing.Pen]::new([Drawing.Color]::FromArgb(68,184,233,198),3)
$g.DrawEllipse($line,-344,1016,1448,1448);$line.Dispose()
$light=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(195,221,243,208))
$g.FillEllipse($light,817,1159,10,10);$light.Dispose()
$g.Dispose()
$wall.Save((Join-Path $assetRoot 'default_wallpaper.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$wall.Dispose()

