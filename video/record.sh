#!/usr/bin/env bash
# Usage: video/record.sh <scene-name> [seconds]
# Records the phone screen over adb (max 180 s per clip) and pulls it to video/raw/.
set -e
export MSYS_NO_PATHCONV=1  # stop Git Bash rewriting /sdcard paths
S="${ADB_SERIAL:-adb-42c33242-fSeArq._adb-tls-connect._tcp}"
NAME="${1:?scene name}"
SECS="${2:-170}"
DIR="$(cd "$(dirname "$0")" && pwd -W)/raw"  # pwd -W gives a Windows path that adb.exe understands
mkdir -p "$DIR"
echo "Recording $NAME for up to ${SECS}s..."
adb -s "$S" shell screenrecord --time-limit "$SECS" --bit-rate 8000000 "/sdcard/ng_$NAME.mp4" || true
sleep 1
adb -s "$S" pull "/sdcard/ng_$NAME.mp4" "$DIR/$NAME.mp4"
adb -s "$S" shell rm "/sdcard/ng_$NAME.mp4"
echo "Saved $DIR/$NAME.mp4"
