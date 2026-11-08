"""Prepare a clean AOSP checkout or verify a previously applied Tommy UI overlay."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[1]


def verify_revisions(root, files):
    projects = sorted(ET.parse(PROJECT / "manifests/aosp.xml").iter("project"),
                      key=lambda node: -len(node.get("path", node.get("name"))))
    affected = {}
    for name in files:
        for project in projects:
            path = project.get("path", project.get("name"))
            if name.startswith(path + "/"):
                affected[path] = project.get("revision")
                break
    for path, expected in affected.items():
        revision = subprocess.check_output(["git", "-C", root / path, "rev-parse", "HEAD"],
                                           text=True).strip()
        if revision != expected:
            raise ValueError(f"Unexpected upstream revision for {path}: {revision}")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def prepare(root, check_only, verify_projects=False):
    files = json.loads((PROJECT / "files.json").read_text())
    if verify_projects:
        verify_revisions(root, files)
    with tempfile.TemporaryDirectory(prefix="tommy-overlay-") as temp:
        stage = Path(temp)
        pending = []
        for name, spec in files.items():
            target = root / name
            if target.resolve() != target:
                raise ValueError(f"Unexpected symlink in destination: {name}")
            current = digest(target)
            if current == spec["result"]:
                continue
            if current != spec["upstream"] or (target.exists() and not target.is_file()):
                raise ValueError(f"Local file differs from the pinned baseline: {name}")
            source = PROJECT / spec["source"]
            staged = stage / name
            staged.parent.mkdir(parents=True, exist_ok=True)
            if spec["source"].startswith("patches/"):
                shutil.copyfile(target, staged)
                subprocess.run(["git", "apply", "--whitespace=nowarn", source],
                               cwd=stage, check=True)
            else:
                shutil.copyfile(source, staged)
            if digest(staged) != spec["result"]:
                raise ValueError(f"Unexpected result for {name}")
            pending.append((staged, target))

        if not check_only:
            for staged, target in pending:
                target.parent.mkdir(parents=True, exist_ok=True)
                with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as out:
                    tmp = Path(out.name)
                try:
                    shutil.copyfile(staged, tmp)
                    tmp.chmod(target.stat().st_mode if target.exists() else 0o644)
                    tmp.replace(target)
                finally:
                    tmp.unlink(missing_ok=True)
        print(f"Checked {len(files)} files. "
              f"{len(pending)} {'would change' if check_only else 'updated'}.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aosp", type=Path)
    parser.add_argument("--check", action="store_true", help="Validate without writing the checkout")
    parser.add_argument("--verify-projects", action="store_true", help="Check affected AOSP Git revisions")
    args = parser.parse_args()
    if not args.aosp.is_dir():
        parser.error("AOSP checkout directory does not exist")
    prepare(args.aosp.resolve(), args.check, args.verify_projects)
