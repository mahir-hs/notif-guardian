# Builds signed release APKs of Guardian and Guardian Helper into dist\, ready to attach to a GitHub release.
# Needs keystore.properties in the project root (see README "Building"); without it, APKs are debug-signed.
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

& .\gradlew.bat :app:assembleRelease :helper:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$version = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
New-Item -ItemType Directory -Force dist | Out-Null
Copy-Item app\build\outputs\apk\release\app-release.apk "dist\NotifGuardian-$version.apk" -Force
Copy-Item helper\build\outputs\apk\release\helper-release.apk "dist\GuardianHelper-$version.apk" -Force

Get-ChildItem dist -Filter *.apk | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "$hash  $($_.Name)"
} | Tee-Object dist\SHA256SUMS.txt
