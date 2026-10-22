param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [Parameter(Mandatory=$true)][string]$SigningStore,
    [Parameter(Mandatory=$true)][string]$SigningPasswordFile,
    [string]$Platform='android-35',
    [string]$BuildTools='36.0.0',
    [string]$OutputDir=(Join-Path $PSScriptRoot 'build/output'),
    [switch]$BuildDeviceTests
)
$ErrorActionPreference='Stop'
function Run-Checked([string]$Exe,[string[]]$Arguments) {
    & $Exe @Arguments
    if($LASTEXITCODE -ne 0){throw "Falha de compilação: $Exe (saída $LASTEXITCODE)"}
}
$javaExe=Join-Path $JavaHome 'bin/java.exe'
$compilerExe=Join-Path $JavaHome 'bin/javac.exe'
$jarExe=Join-Path $JavaHome 'bin/jar.exe'
$toolsPath=Join-Path $AndroidSdk "build-tools/$BuildTools"
$androidJar=Join-Path $AndroidSdk "platforms/$Platform/android.jar"
foreach($path in @($javaExe,$compilerExe,$jarExe,$androidJar,$SigningStore,$SigningPasswordFile)) {if(!(Test-Path -LiteralPath $path)){throw "Arquivo necessário não encontrado: $path"}}
$buildPath=Join-Path $PSScriptRoot ('build/run-'+[guid]::NewGuid().ToString('N'))
$classesPath=Join-Path $buildPath 'classes'
$generatedPath=Join-Path $buildPath 'generated'
$dexPath=Join-Path $buildPath 'dex'
New-Item -ItemType Directory -Force -Path $buildPath,$classesPath,$generatedPath,$dexPath,$OutputDir | Out-Null
$platformCopy=Join-Path $buildPath 'android.jar'
Copy-Item -LiteralPath $androidJar -Destination $platformCopy
$androidJar=$platformCopy
Run-Checked (Join-Path $toolsPath 'aapt2.exe') @('compile','--dir',(Join-Path $PSScriptRoot 'app/src/main/res'),'-o',(Join-Path $buildPath 'resources.zip'))
$manifestXml=[xml](Get-Content -LiteralPath (Join-Path $PSScriptRoot 'app/src/main/AndroidManifest.xml') -Raw)
$manifestXml.DocumentElement.SetAttribute('package','br.com.easyvacc.android')
$manifestPath=Join-Path $buildPath 'AndroidManifest.xml'
$manifestXml.Save($manifestPath)
Run-Checked (Join-Path $toolsPath 'aapt2.exe') @('link','-o',(Join-Path $buildPath 'resources.apk'),'-I',$androidJar,'--manifest',$manifestPath,'--java',$generatedPath,'--version-code','20102','--version-name','2.1.2','--min-sdk-version','23','--target-sdk-version','35',(Join-Path $buildPath 'resources.zip'))
$sources=Get-ChildItem (Join-Path $PSScriptRoot 'app/src/main/java'),$generatedPath -Recurse -Filter '*.java' | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' }
$sourcesPath=Join-Path $buildPath 'sources.txt'
[IO.File]::WriteAllLines($sourcesPath,$sources,[Text.UTF8Encoding]::new($false))
Run-Checked $compilerExe @('-encoding','UTF-8','-source','8','-target','8','-classpath',$androidJar,'-d',$classesPath,('@'+$sourcesPath))
Run-Checked $jarExe @('cf',(Join-Path $buildPath 'classes.jar'),'-C',$classesPath,'.')
Run-Checked $javaExe @('-Xmx1024m','-cp',(Join-Path $toolsPath 'lib/d8.jar'),'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','23','--output',$dexPath,(Join-Path $buildPath 'classes.jar'))
$unsignedPath=Join-Path $buildPath 'unsigned.apk'
Copy-Item -LiteralPath (Join-Path $buildPath 'resources.apk') -Destination $unsignedPath
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::Open($unsignedPath,[IO.Compression.ZipArchiveMode]::Update)
try {foreach($file in Get-ChildItem $dexPath -Filter '*.dex'){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$file.Name,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$archive.Dispose()}
$alignedPath=Join-Path $buildPath 'aligned.apk'
Run-Checked (Join-Path $toolsPath 'zipalign.exe') @('-f','-p','4',$unsignedPath,$alignedPath)
$apkPath=Join-Path $OutputDir 'EasyVacc-Android-2.1.2.apk'
Run-Checked $javaExe @('-jar',(Join-Path $toolsPath 'lib/apksigner.jar'),'sign','--ks',$SigningStore,'--ks-key-alias','easyvacc','--ks-pass',('file:'+$SigningPasswordFile),'--out',$apkPath,$alignedPath)
Run-Checked $javaExe @('-jar',(Join-Path $toolsPath 'lib/apksigner.jar'),'verify','--verbose','--print-certs',$apkPath)
Run-Checked (Join-Path $toolsPath 'zipalign.exe') @('-c','4',$apkPath)
Write-Output "APK gerado e assinatura verificada: $apkPath"
if($BuildDeviceTests){
    $testClasses=Join-Path $buildPath 'test-classes'
    $testDex=Join-Path $buildPath 'test-dex'
    New-Item -ItemType Directory -Path $testClasses,$testDex -Force | Out-Null
    $testJava=Join-Path $PSScriptRoot 'tests/android/NativeInstrumentation.java'
    Run-Checked $compilerExe @('-encoding','UTF-8','-source','8','-target','8','-classpath',($androidJar+';'+(Join-Path $buildPath 'classes.jar')),'-d',$testClasses,$testJava)
    Run-Checked $jarExe @('cf',(Join-Path $buildPath 'tests.jar'),'-C',$testClasses,'.')
    Run-Checked $javaExe @('-Xmx1024m','-cp',(Join-Path $toolsPath 'lib/d8.jar'),'com.android.tools.r8.D8','--lib',$androidJar,'--classpath',(Join-Path $buildPath 'classes.jar'),'--min-api','23','--output',$testDex,(Join-Path $buildPath 'tests.jar'))
    $testResources=Join-Path $buildPath 'test-resources.apk'
    Run-Checked (Join-Path $toolsPath 'aapt2.exe') @('link','-o',$testResources,'-I',$androidJar,'--manifest',(Join-Path $PSScriptRoot 'tests/android/AndroidManifest.xml'),'--version-code','1','--version-name','1.0','--min-sdk-version','23','--target-sdk-version','35')
    $testArchive=[IO.Compression.ZipFile]::Open($testResources,[IO.Compression.ZipArchiveMode]::Update)
    try{foreach($file in Get-ChildItem $testDex -Filter '*.dex'){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($testArchive,$file.FullName,$file.Name,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$testArchive.Dispose()}
    $testAligned=Join-Path $buildPath 'test-aligned.apk'
    Run-Checked (Join-Path $toolsPath 'zipalign.exe') @('-f','-p','4',$testResources,$testAligned)
    $testApk=Join-Path $OutputDir 'EasyVacc-Android-Testes.apk'
    Run-Checked $javaExe @('-jar',(Join-Path $toolsPath 'lib/apksigner.jar'),'sign','--ks',$SigningStore,'--ks-key-alias','easyvacc','--ks-pass',('file:'+$SigningPasswordFile),'--out',$testApk,$testAligned)
    Run-Checked $javaExe @('-jar',(Join-Path $toolsPath 'lib/apksigner.jar'),'verify',$testApk)
    Write-Output "APK de instrumentação compilado, sem execução em dispositivo: $testApk"
}
