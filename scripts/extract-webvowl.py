"""Extract and verify the unchanged WebVOWL assets from a WIDOCO JAR."""
import hashlib
import json
import sys
import zipfile
from pathlib import Path, PurePosixPath

jar, source, output = map(Path, sys.argv[1:4])
prefix = "webvowl_1.1.7_patched/"
hashes = {}
with zipfile.ZipFile(jar) as archive:
    for entry in archive.infolist():
        if not entry.filename.startswith(prefix) or entry.is_dir():
            continue
        relative = PurePosixPath(entry.filename[len(prefix):])
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError("Unexpected asset path")
        data = archive.read(entry)
        if data != source.joinpath(*relative.parts).read_bytes():
            raise ValueError(f"Packaged viewer differs from WIDOCO: {relative}")
        destination = output.joinpath(*relative.parts)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        hashes[str(relative)] = hashlib.sha256(data).hexdigest()
if "index.html" not in hashes or "js/webvowl.js" not in hashes:
    raise ValueError("WIDOCO WebVOWL assets are missing")
output.with_suffix(".json").write_text(json.dumps({"jar": str(jar), "sha256": hashes}, indent=2), encoding="utf-8")
print(f"{len(hashes)} packaged viewer assets match WIDOCO")
