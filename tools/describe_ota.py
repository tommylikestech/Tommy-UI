"""Extract and describe a signed, device-specific Android A/B package for the Pages feed."""
import argparse
import base64
import hashlib
import re
import shutil
import tempfile
import zipfile
from pathlib import Path


def fields(text):
    return dict(line.split("=", 1) for line in text.splitlines() if "=" in line)


def digest(stream, limit=None):
    h = hashlib.sha256()
    while limit is None or limit > 0:
        chunk = stream.read(min(1 << 20, limit) if limit is not None else 1 << 20)
        if not chunk:
            break
        h.update(chunk)
        if limit is not None:
            limit -= len(chunk)
    return base64.b64encode(h.digest()).decode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--vendor", required=True, help="Exact source ro.vendor.build.fingerprint")
    parser.add_argument("--type", choices=("security", "content"), required=True)
    parser.add_argument("--summary", required=True)
    args = parser.parse_args()
    if not args.summary or not args.vendor or any(c in args.summary + args.vendor for c in "\n\r"):
        parser.error("Provide single-line summary and vendor fingerprint")
    args.output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.package) as archive, tempfile.TemporaryDirectory(dir=args.output) as temp:
        metadata = fields(archive.read("META-INF/com/android/metadata").decode())
        source = metadata.get("pre-build-incremental", "")
        target = metadata.get("post-build-incremental", "")
        pattern = r"TommyUI\.(v\d{1,5}\.\d{3})\.dev\.\d{8}"
        new = re.fullmatch(pattern, target)
        old = re.fullmatch(pattern, source)
        if (metadata.get("ota-type") != "AB" or "fogona" not in metadata.get("pre-device", "").split("|")
                or not new or not old or metadata.get("ota-wipe") == "yes"
                or metadata.get("ota-downgrade") == "yes"):
            parser.error("Expected a fogona A/B upgrade with exact Tommy UI source and target builds")
        version = new.group(1)
        number = lambda tag: tuple(map(int, tag[1:].split(".")))
        if number(version) <= number(old.group(1)):
            parser.error("Target version must be newer")
        props = fields(archive.read("payload_properties.txt").decode())
        name = "Tommy-UI-fogona.bin"
        tmp = Path(temp) / name
        with archive.open("payload.bin") as src, tmp.open("wb") as dst:
            shutil.copyfileobj(src, dst)
        with tmp.open("rb") as src:
            if src.read(4) != b"CrAU" or int.from_bytes(src.read(8), "big") != 2:
                parser.error("Invalid Android payload header")
            manifest_size = int.from_bytes(src.read(8), "big")
            signature_size = int.from_bytes(src.read(4), "big")
            size = tmp.stat().st_size
            meta_size = int(props["METADATA_SIZE"])
            if (signature_size <= 0 or meta_size != 24 + manifest_size or meta_size+signature_size >= size
                    or size != int(props["FILE_SIZE"])):
                parser.error("Invalid payload sizes or missing signature")
            src.seek(0)
            if digest(src, meta_size) != props["METADATA_HASH"]:
                parser.error("Metadata hash mismatch")
            src.seek(0)
            if digest(src) != props["FILE_HASH"]:
                parser.error("Payload hash mismatch")
        values = dict(version=version, type=args.type, summary=args.summary,
                      url=f"https://github.com/tommylikestech/Tommy-UI/releases/download/{version}/{name}",
                      device="fogona", sku="XT2413-2", vendor=args.vendor)
        values["from"] = source
        for key in ("FILE_SIZE", "FILE_HASH", "METADATA_SIZE", "METADATA_HASH"):
            values[key] = props[key]
        desc = Path(temp) / "version-unsigned.txt"
        desc.write_text("".join(f"{k}={v}\n" for k,v in values.items()), encoding="utf-8")
        tmp.replace(args.output / name)
        desc.replace(args.output / desc.name)


if __name__ == "__main__":
    main()
