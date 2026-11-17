$ErrorActionPreference = 'Stop'
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
  Write-Host 'Gradle is required. Install Gradle 9.x and Java 21, then run this script again.' -ForegroundColor Yellow
  exit 1
}
gradle build
Write-Host 'Build output:'
Get-ChildItem .\build\libs\*.jar
