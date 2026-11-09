$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
$assetRoot = Join-Path $PSScriptRoot 'assets'
[System.IO.Directory]::CreateDirectory($assetRoot) | Out-Null

$bootStream = [System.IO.File]::Open((Join-Path $assetRoot 'bootanimation.zip'), [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($bootStream, [System.IO.Compression.ZipArchiveMode]::Create)
$entry = $archive.CreateEntry('desc.txt', [System.IO.Compression.CompressionLevel]::NoCompression)
$writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
$writer.Write("720 1600 12`np 0 0 part0`n"); $writer.Dispose()
$font = [System.Drawing.Font]::new('Segoe UI', 38, [System.Drawing.FontStyle]::Regular)
$smallFont = [System.Drawing.Font]::new('Segoe UI', 15, [System.Drawing.FontStyle]::Regular)
$center = [System.Drawing.StringFormat]::new()
$center.Alignment = [System.Drawing.StringAlignment]::Center
for ($frame = 0; $frame -lt 12; $frame++) {
    $bitmap = [System.Drawing.Bitmap]::new(720, 1600)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::FromArgb(11, 32, 32))
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $ink = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(219, 235, 219))
    $accent = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(145, 196, 153))
    $ring=[Drawing.Pen]::new($accent,4); $graphics.DrawEllipse($ring,262,510,196,196); $ring.Dispose(); $graphics.FillRectangle($accent, 308, 568, 104, 14)
    $graphics.FillRectangle($accent, 353, 582, 14, 74)
    $graphics.DrawString('Tommy UI', $font, $ink, [System.Drawing.PointF]::new(360, 730), $center)
    $graphics.DrawString('Classic   /   v1.1.1', $smallFont, $accent, [System.Drawing.PointF]::new(360, 803), $center)
    for ($dot = 0; $dot -lt 12; $dot++) {
        $angle = $dot * [Math]::PI / 6
        $age = ($frame - $dot + 12) % 12
        $opacity = [int](235 - $age * 16)
        $dotBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($opacity, 145, 196, 153))
        $graphics.FillEllipse($dotBrush, [single](356 + 19 * [Math]::Sin($angle)), [single](1016 - 19 * [Math]::Cos($angle)), 7, 7)
        $dotBrush.Dispose()
    }
    $ink.Dispose(); $accent.Dispose(); $graphics.Dispose()
    $entry = $archive.CreateEntry(('part0/{0:d3}.png' -f $frame), [System.IO.Compression.CompressionLevel]::NoCompression)
    $memory = [System.IO.MemoryStream]::new()
    $bitmap.Save($memory, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $memory.ToArray()
    $target = $entry.Open(); $target.Write($bytes, 0, $bytes.Length); $target.Dispose()
    $memory.Dispose(); $bitmap.Dispose()
}
$font.Dispose(); $smallFont.Dispose(); $center.Dispose(); $archive.Dispose(); $bootStream.Dispose()
Write-Output 'Generated v1.1.1 Classic boot animation.'
