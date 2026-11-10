$ErrorActionPreference = 'Stop'
$art = Join-Path $PSScriptRoot '../overlay/frameworks/base/core/res/res/drawable-nodpi/default_wallpaper.png'
$assets = Join-Path $PSScriptRoot 'assets'
[System.IO.Directory]::CreateDirectory($assets) | Out-Null
Copy-Item -LiteralPath $art -Destination (Join-Path $assets 'default_wallpaper.png')

