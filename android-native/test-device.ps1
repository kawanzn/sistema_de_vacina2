param(
    [string]$AndroidSdk="$env:LOCALAPPDATA/Android/Sdk",
    [string]$ApkDir=(Join-Path $PSScriptRoot 'build/output'),
    [string]$Serial='',
    [string]$EvidenceDir=(Join-Path $PSScriptRoot 'build/device-evidence')
)
$ErrorActionPreference='Stop'
$adb=Join-Path $AndroidSdk 'platform-tools/adb.exe'
if(!(Test-Path -LiteralPath $adb)){throw 'Android SDK não encontrado. Informe -AndroidSdk.'}
$devices=@(& $adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object {$_.Matches[0].Groups[1].Value})
if($LASTEXITCODE -ne 0){throw 'Não foi possível consultar aparelhos Android.'}
if($Serial){if($devices -notcontains $Serial){throw 'Aparelho indicado indisponível ou sem autorização de depuração.'}}
elseif($devices.Count -eq 1){$Serial=$devices[0]}
elseif($devices.Count -eq 0){throw 'Nenhum Android conectado. Conecte por USB, ative a depuração USB e aceite a autorização no aparelho.'}
else{throw 'Há mais de um Android. Selecione um com -Serial.'}
$main=Join-Path $ApkDir 'EasyVacc-Android-2.1.2.apk'
$tests=Join-Path $ApkDir 'EasyVacc-Android-Testes.apk'
foreach($file in @($main,$tests)){if(!(Test-Path -LiteralPath $file)){throw "APK ausente: $file. Compile com -BuildDeviceTests."}}
New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null
$log=Join-Path $EvidenceDir ('android-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.txt')
@('EasyVacc Android — execução em dispositivo', ('Data: '+(Get-Date -Format o)),('APK SHA256: '+(Get-FileHash -LiteralPath $main -Algorithm SHA256).Hash)) | Set-Content -LiteralPath $log -Encoding utf8
foreach($file in @($main,$tests)){
    $install=@(& $adb -s $Serial install -r $file 2>&1)
    $install | Add-Content -LiteralPath $log -Encoding utf8
    if($LASTEXITCODE -ne 0 -or ($install -join "`n") -notmatch '(?m)^Success\s*$'){throw "Instalação falhou. Consulte $log"}
}
$report=@(& $adb -s $Serial shell am instrument -w br.com.easyvacc.android.tests/br.com.easyvacc.android.NativeInstrumentation 2>&1)
$report | Add-Content -LiteralPath $log -Encoding utf8
$report | Write-Output
$text=$report -join "`n"
if($LASTEXITCODE -ne 0 -or $text -match 'FALHA:|INSTRUMENTATION_FAILED|Process crashed' -or $text -notmatch 'TOTAL: \d+ verificações Android aprovadas\.' -or $text -notmatch 'INSTRUMENTATION_CODE: -1'){throw "Testes não aprovados. Consulte $log"}
Write-Output "Execução Android aprovada. Evidência: $log"
