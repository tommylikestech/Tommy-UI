param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$uri = 'https://github.com/tommylikestech/Tommy-UI/releases/download/update-zero-1.0.03/update-zero.metadata'
$expected = 'a484ae0fa0ee0e8b2e3bbd1d78856157e49bd3454841f4b767a5a0429ae96ec4'
function Device([string[]]$Command) {
    $result = & adb -s $Serial @Command
    if ($LASTEXITCODE -ne 0) { throw "Device command failed: $($Command -join ' ')" }
    return $result
}
if ((Device @('shell','getprop','ro.boot.hardware.sku')).Trim() -ne 'XT2413-2' -or
    (Device @('shell','getprop','ro.build.version.incremental')).Trim() -ne 'TommyUI.v1.003.dev.20261106') {
    throw 'This test is for the original Tommy UI v1.003 on XT2413-2.'
}
$id = [guid]::NewGuid().ToString('N')
$local = Join-Path ([IO.Path]::GetTempPath()) "test-$id.metadata"
$stage = "/data/local/tmp/tommy-preflight-$id"
$verify = "/data/misc/tommy-preflight-$id"
try {
    Invoke-WebRequest -Uri $uri -OutFile $local
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $local).Hash -ne $expected) { throw 'Checksum mismatch' }
    Device @('push',$local,$stage)
    Device @('shell','su','0','cp',$stage,$verify)
    Device @('shell','su','0','chcon','u:object_r:ota_package_file:s0',$verify)
    Device @('shell','su','0','chmod','644',$verify)
    $result = (& adb -s $Serial shell su 0 update_engine_client --verify "--metadata=$verify" 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $result -notmatch 'Payload is applicable\.' -or $result -match 'Payload is not applicable\.') {
        throw "Android did not confirm payload applicability. $result"
    }
    $result.Trim()
} finally {
    & adb -s $Serial shell su 0 rm -f $stage $verify
    if (Test-Path -LiteralPath $local) { Remove-Item -LiteralPath $local }
}
