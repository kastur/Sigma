ADB="adb $1"
# Get all the debug traces from the sdcard, and output them as html for analysis.
rm -rf *.trace*
$ADB shell rm -rf /sdcard/tmp
$ADB shell mkdir /sdcard/tmp
$ADB shell mv /sdcard/*.trace /sdcard/tmp
$ADB $1 pull /sdcard/tmp .
