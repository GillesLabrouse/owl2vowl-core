"""Select this checkout's converter version in an isolated WIDOCO test POM."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

target = Path(sys.argv[1])
version = ET.parse("pom.xml").getroot().find("{http://maven.apache.org/POM/4.0.0}version").text
text, count = re.subn(r"(<artifactId>owl2vowl-core</artifactId>\s*<version>)[^<]+(</version>)",
                      lambda match: match[1] + version + match[2], target.read_text(encoding="utf-8"))
if count != 1:
    raise ValueError("Expected exactly one WIDOCO converter dependency")
if len(sys.argv) > 2:
    # Optional, independent WIDOCO security variant; never a converter dependency.
    jsoup = sys.argv[2]
    text, count = re.subn(r"(<artifactId>jsoup</artifactId>\s*<version>)[^<]+(</version>)",
                         lambda match: match[1] + jsoup + match[2], text)
    if count != 1:
        raise ValueError("Expected exactly one WIDOCO jsoup dependency")
    print(f"Applying the separate WIDOCO jsoup {jsoup} security update")
target.write_text(text, encoding="utf-8")
print(f"Testing WIDOCO with owl2vowl-core {version}")
