param([Parameter(Mandatory=$true)][string]$JavaHome)
$ErrorActionPreference='Stop'
$testDir=Join-Path $PSScriptRoot ('build/jvm-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir -Force | Out-Null
$basePath=Join-Path $PSScriptRoot 'app/src/main/java/br/com/easyvacc/android'
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -d $testDir (Join-Path $basePath 'Rules.java') (Join-Path $basePath 'ShareFlow.java') (Join-Path $basePath 'SaquaremaDirectory.java') (Join-Path $basePath 'Secrets.java') (Join-Path $PSScriptRoot 'tests/DomainTests.java')
if($LASTEXITCODE -ne 0){throw 'Falha ao compilar os testes JVM.'}
& (Join-Path $JavaHome 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $testDir br.com.easyvacc.android.DomainTests
if($LASTEXITCODE -ne 0){throw 'Há falhas nos testes JVM.'}
