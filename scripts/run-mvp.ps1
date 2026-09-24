$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force bin | Out-Null
javac -encoding UTF-8 -d bin src\*.java blockchain\*.java contract\*.java model\*.java Main.java ServidorSeguroChain.java
java -cp bin ServidorSeguroChain 8080
