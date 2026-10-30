# Pulls Notif Guardian logs plus system-level state over adb (phone connected, USB debugging on).
# Output: diagnostics\<timestamp>\ in the project folder.
$ErrorActionPreference = 'Continue'
$app = 'dev.notifguardian'
# The apps the user chose to protect, from the app's own preferences
$prefs = adb shell run-as $app cat shared_prefs/protected_apps.xml
$targets = @([regex]::Matches(($prefs -join ''), '<string>([^<]+)</string>') | ForEach-Object { $_.Groups[1].Value }) +
    'com.google.android.gms', $app | Select-Object -Unique

$out = Join-Path $PSScriptRoot "..\diagnostics\$(Get-Date -Format 'yyyyMMdd-HHmmss')"
New-Item -ItemType Directory -Force $out | Out-Null

function Save($name, $text) { ($text -join "`n") | Set-Content -Encoding utf8 (Join-Path $out $name) }

Save 'devices.txt' (adb devices -l)
# App-private files (works on debug builds via run-as)
Save 'app.log' (adb shell run-as $app cat files/logs/app.log)
Save 'app.log.1' (adb shell run-as $app cat files/logs/app.log.1)
Save 'events.jsonl' (adb shell run-as $app cat files/events.jsonl)

Save 'getprop.txt' (adb shell getprop)
Save 'deviceidle-whitelist.txt' (adb shell dumpsys deviceidle whitelist)
# FCM connection to mtalk.google.com: reconnect count, last connection duration, heartbeat interval
Save 'fcm-gcmservice.txt' (adb shell dumpsys activity service com.google.android.gms/.gcm.GcmService)
# When Google Play services handed each push to each app (enqueue time, app, FCM priority)
$bc = @(adb shell dumpsys activity broadcasts history)
$fcm = for ($k = 0; $k -lt $bc.Count; $k++) {
    if ($bc[$k] -match 'act=com.google.android.c2dm.intent.RECEIVE.*pkg=(\S+)') {
        $pkg = $matches[1]; $prio = ''; $enq = ''
        foreach ($l in $bc[$k..([Math]::Min($bc.Count - 1, $k + 6))]) {
            if ($l -match 'Reason=(\S+ FCM)') { $prio = $matches[1] }
            if ($l -match 'enqueueClockTime=(\S+ \S+)') { $enq = $matches[1] }
        }
        if ($enq) { "$enq  $pkg  $prio" }
    }
}
Save 'fcm-deliveries.txt' ($fcm | Sort-Object -Unique)
Save 'greezer.txt' (adb shell dumpsys greezer)
Save 'millet-props.txt' (adb shell getprop | Select-String 'millet|freez')
Save 'logcat-all.txt' (adb logcat -d -v threadtime -b main,system,events,crash)

$per = foreach ($p in $targets) {
    "===== $p"
    "standby bucket: $(adb shell am get-standby-bucket $p)"
    "-- appops"
    adb shell cmd appops get $p
    "-- package state"
    adb shell dumpsys package $p | Select-String -Pattern 'stopped=|enabled=|versionName|lastUpdateTime' | ForEach-Object { $_.Line.Trim() }
    ""
}
Save 'per-app.txt' $per

Write-Host "Saved to $((Resolve-Path $out).Path)"
