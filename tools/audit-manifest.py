"""Fail if the compiled app requests permissions or adds hardware filters."""
import sys
import xml.etree.ElementTree as ET

ROOT = ET.parse(sys.argv[1]).getroot()
NS = "{http://schemas.android.com/apk/res/android}"
EXPECTED = {
    "android.hardware.screen.landscape",
    "android.hardware.screen.portrait",
    "android.hardware.touchscreen",
}
features = ROOT.findall("uses-feature")
assert len(features) == len(EXPECTED), "Unexpected or duplicate hardware feature"
assert all(item.attrib.get(NS + "required") == "false" for item in features), "Required hardware"
assert {item.attrib.get(NS + "name") for item in features} == EXPECTED
assert not any(child.tag.startswith("uses-permission") for child in ROOT), "Unexpected permissions"
if ROOT.get("package"):
    assert ROOT.get("package") == "com.magicbrush.brush"
sdk = ROOT.find("uses-sdk")
assert sdk is not None and sdk.get(NS + "targetSdkVersion") == "36"
assert sdk.get(NS + "minSdkVersion") == "26"
assert ROOT.find("application").get(NS + "allowBackup") == "false"
print("Manifest passed: API 26–36, three optional hardware declarations, zero requested permissions.")
