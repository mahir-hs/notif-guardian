# Builds signed release APKs of Guardian and Guardian Helper into dist\, ready to attach to a GitHub release.
# Needs keystore.properties in the project root (see README "Building"); without it, APKs are debug-signed.
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

& .\gradlew.bat :app:assembleRelease :helper:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$version = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
# Start clean so dist\ holds exactly the files to attach to this release
Remove-Item dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force dist | Out-Null
Copy-Item app\build\outputs\apk\release\app-release.apk "dist\NotifGuardian-$version.apk" -Force
Copy-Item helper\build\outputs\apk\release\helper-release.apk "dist\GuardianHelper-$version.apk" -Force

$sums = Get-ChildItem dist -Filter *.apk | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "$hash  $($_.Name)"
}
# UTF-8 without BOM, LF endings: the standard sha256sum format
[IO.File]::WriteAllText((Join-Path $root 'dist\SHA256SUMS.txt'), (($sums -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
$sums
