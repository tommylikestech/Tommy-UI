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
$wash = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(26, 117, 175, 144))
$g.FillEllipse($wash, -550, 1100, 1950, 2100)
$wash.Dispose()
$wash = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(45, 145, 196, 153))
$g.FillEllipse($wash, 190, 1430, 1550, 1750)
$wash.Dispose()
$path = [System.Drawing.Drawing2D.GraphicsPath]::new()
$path.AddBezier(1050, -120, 150, 100, 1380, 850, 760, 1530)
$path.AddBezier(760, 1530, 280, 2110, -380, 1950, -300, 2550)
$pen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(160, 153, 196, 159), 3)
$g.DrawPath($pen, $path)
$pen.Dispose(); $path.Dispose(); $g.Dispose()
$wall.Save((Join-Path $assetRoot 'default_wallpaper.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$wall.Dispose()

