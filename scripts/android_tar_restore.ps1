param(
  [Parameter(Mandatory=$true)][string]$Adb,
  [Parameter(Mandatory=$true)][string]$OldSerial,
  [Parameter(Mandatory=$true)][string]$NewSerial,
  [Parameter(Mandatory=$true)][string]$Base,
  [Parameter(Mandatory=$true)][string]$List,
  [Parameter(Mandatory=$true)][string]$TouchList,
  [string]$Key = "phone-media-recovery",
  [string]$WorkDir = "/sdcard/Download/phone_media_recovery"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
  param([string]$Serial, [string[]]$Args)
  & $Adb -s $Serial @Args
  if ($LASTEXITCODE -ne 0) {
    throw "adb failed for ${Serial}: $($Args -join ' ')"
  }
}

function Quote-Sh {
  param([string]$Value)
  return "'" + $Value.Replace("'", "'\''") + "'"
}

if (-not (Test-Path -LiteralPath $Adb)) { throw "adb not found: $Adb" }
if (-not (Test-Path -LiteralPath $List)) { throw "list not found: $List" }
if (-not (Test-Path -LiteralPath $TouchList)) { throw "touch list not found: $TouchList" }

$localArchive = Join-Path (Get-Location) "$Key.tar"
$oldList = "$WorkDir/$Key.list"
$oldListLf = "$WorkDir/$Key.lf.list"
$newList = "$WorkDir/$Key.list"
$newTouch = "$WorkDir/$Key.touch"
$oldArchive = "$WorkDir/$Key.tar"
$newArchive = "$WorkDir/$Key.tar"
$verifyOld = "$WorkDir/$Key.old.verify"
$verifyNew = "$WorkDir/$Key.new.verify"
$localVerifyOld = Join-Path (Get-Location) "$Key.old.verify"
$localVerifyNew = Join-Path (Get-Location) "$Key.new.verify"

$verifyScript = @'
#!/system/bin/sh
BASE="$1"
LIST="$2"
while IFS= read -r rel; do
  rel=${rel%$'\r'}
  [ -z "$rel" ] && continue
  f="$BASE/$rel"
  if [ -f "$f" ]; then
    size="$(stat -c '%s' "$f" 2>/dev/null)"
    if command -v md5sum >/dev/null 2>&1; then
      sum="$(md5sum "$f" 2>/dev/null | awk '{print $1}')"
    else
      sum="$(sha256sum "$f" 2>/dev/null | awk '{print $1}')"
    fi
    echo "$sum|$size|$rel"
  else
    echo "MISSING||$rel"
  fi
done < "$LIST"
'@

$touchScript = @'
#!/system/bin/sh
BASE="$1"
LIST="$2"
count=0
while IFS='|' read -r ep rel; do
  rel=${rel%$'\r'}
  [ -z "$rel" ] && continue
  if touch -d "@$ep" "$BASE/$rel" 2>/dev/null; then
    count=$((count+1))
  fi
done < "$LIST"
echo "TOUCHED=$count"
'@

$tmp = New-Item -ItemType Directory -Force -Path (Join-Path $env:TEMP "phone-media-recovery") | Select-Object -ExpandProperty FullName
$verifyLocalScript = Join-Path $tmp "verify.sh"
$touchLocalScript = Join-Path $tmp "touch.sh"
$verifyScript | Set-Content -LiteralPath $verifyLocalScript -Encoding UTF8
$touchScript | Set-Content -LiteralPath $touchLocalScript -Encoding UTF8

Invoke-Adb $OldSerial @("shell", "mkdir -p $(Quote-Sh $WorkDir)")
Invoke-Adb $NewSerial @("shell", "mkdir -p $(Quote-Sh $WorkDir)")
Invoke-Adb $OldSerial @("push", $List, $oldList) | Out-Null
Invoke-Adb $NewSerial @("push", $List, $newList) | Out-Null
Invoke-Adb $NewSerial @("push", $TouchList, $newTouch) | Out-Null
Invoke-Adb $OldSerial @("push", $verifyLocalScript, "$WorkDir/verify.sh") | Out-Null
Invoke-Adb $NewSerial @("push", $verifyLocalScript, "$WorkDir/verify.sh") | Out-Null
Invoke-Adb $NewSerial @("push", $touchLocalScript, "$WorkDir/touch.sh") | Out-Null

Invoke-Adb $OldSerial @("shell", "tr -d '\r' < $(Quote-Sh $oldList) > $(Quote-Sh $oldListLf)")
Invoke-Adb $OldSerial @("shell", "rm -f $(Quote-Sh $oldArchive); tar -cf $(Quote-Sh $oldArchive) -C $(Quote-Sh $Base) -T $(Quote-Sh $oldListLf)")
Invoke-Adb $OldSerial @("pull", $oldArchive, $localArchive) | Out-Null
Invoke-Adb $NewSerial @("push", $localArchive, $newArchive) | Out-Null
Invoke-Adb $NewSerial @("shell", "mkdir -p $(Quote-Sh $Base); tar -xf $(Quote-Sh $newArchive) -C $(Quote-Sh $Base)")
Invoke-Adb $NewSerial @("shell", "sh $(Quote-Sh "$WorkDir/touch.sh") $(Quote-Sh $Base) $(Quote-Sh $newTouch)")

Invoke-Adb $OldSerial @("shell", "sh $(Quote-Sh "$WorkDir/verify.sh") $(Quote-Sh $Base) $(Quote-Sh $oldListLf) > $(Quote-Sh $verifyOld)")
Invoke-Adb $NewSerial @("shell", "sh $(Quote-Sh "$WorkDir/verify.sh") $(Quote-Sh $Base) $(Quote-Sh $newList) > $(Quote-Sh $verifyNew)")
Invoke-Adb $OldSerial @("pull", $verifyOld, $localVerifyOld) | Out-Null
Invoke-Adb $NewSerial @("pull", $verifyNew, $localVerifyNew) | Out-Null

$oldHash = (Get-FileHash -Algorithm MD5 -LiteralPath $localVerifyOld).Hash
$newHash = (Get-FileHash -Algorithm MD5 -LiteralPath $localVerifyNew).Hash
if ($oldHash -ne $newHash) {
  throw "verification mismatch: $localVerifyOld != $localVerifyNew"
}

Invoke-Adb $OldSerial @("shell", "rm -f $(Quote-Sh $oldArchive)")
Invoke-Adb $NewSerial @("shell", "rm -f $(Quote-Sh $newArchive)")
Remove-Item -LiteralPath $localArchive -Force -ErrorAction SilentlyContinue
Write-Host "RESTORE_OK key=$Key verify_md5=$oldHash"
