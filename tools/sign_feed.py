"""Sign a UTF-8 release descriptor for GitHub Pages. Keep the RSA private key offline."""
import argparse
import base64
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("description", type=Path)
parser.add_argument("key", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
body = (args.description.read_text(encoding="utf-8").rstrip("\n") + "\n").encode("utf-8")
if b"\nsignature=" in body:
    parser.error("Provide an unsigned descriptor")
signature = subprocess.run(["openssl", "dgst", "-sha256", "-sign", args.key], input=body,
                           stdout=subprocess.PIPE, check=True).stdout
args.output.write_bytes(body + b"signature=" + base64.b64encode(signature) + b"\n")
