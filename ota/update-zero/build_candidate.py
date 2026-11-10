"""Build a local, source-bound A/B trial for the probed XT2413-2. Never flashes a phone."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET

SOURCE_HASH = "58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f"


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def firmware_names(factory):
    """Read Motorola's named flash operations; exclude single-copy and erase operations."""
    xml = ET.parse(factory / "servicefile.xml")
    model = xml.find(".//phone_model")
    software = xml.find(".//software_version")
    if (model is None or model.get("model") != "fogona_g" or software is None
            or "U1TFS34.100-35-14-1-21 " not in software.get("version", "")):
        raise ValueError("Unexpected factory recipe model or revision")
    steps = xml.iter("step")
    names = {s.get("partition") for s in steps if s.get("operation") == "flash"}
    for group in ("bootloader", "radio"):
        names.remove(group)
        with (factory / (group + ".img")).open("rb") as f:
            prefix = f.read(65536)
        recipe = re.search(rb"<recipe>.*?</recipe>", prefix, re.S)
        if not recipe:
            raise ValueError("Missing factory recipe for " + group)
        names.update(e.get("partition") for e in ET.fromstring(recipe.group()).iter("flash"))
    return names - {"partition", "super"}


def pb(raw):
    return '"' + "".join(f"\\{v:03o}" for v in raw) + '"'


def build_date(props):
    """Read the timestamp the update engine compares against the phone's system partition."""
    dates = [p.partition("=")[2] for p in props if p.startswith("ro.system.build.date.utc=")]
    if len(dates) != 1 or not re.fullmatch(r"[1-9][0-9]*", dates[0]):
        raise ValueError("Missing system build date")
    return int(dates[0])


def unversioned(manifest):
    """Name partitions in a decoded manifest that lack the version a partial update requires."""
    missing = []
    for block in re.split(r"^partitions \{$", manifest, flags=re.M)[1:]:
        body = re.split(r"^\}$", block, maxsplit=1, flags=re.M)[0]
        if not re.search(r'^  version: "[0-9]+"$', body, re.M):
            missing.append(re.search(r'^  partition_name: "(.*)"$', body, re.M).group(1))
    return missing


def copy_operations(factory, inventory, version, classic=False):
    names = firmware_names(factory)
    covered = set()
    updates = []
    suffix = None
    for line in inventory.read_text(encoding="utf-8-sig").splitlines():
        part, old_size, new_size, source, inactive = line.split()
        if not re.fullmatch(r"[a-z0-9_]+_[ab]" if classic else r"[a-z0-9_]+_a", part) or not all(
                re.fullmatch(r"[0-9a-f]{64}", x) for x in (source, inactive)):
            raise ValueError("Malformed partition inventory")
        if suffix is not None and suffix != part[-2:]:
            raise ValueError("Mixed source slots in inventory")
        suffix = part[-2:]
        part = part[:-2]
        if part not in names:
            if source != inactive:
                raise ValueError("Unaccounted inactive-slot difference: " + part)
            continue
        size = int(old_size)
        if part in covered or size != int(new_size) or size <= 0 or size % 4096:
            raise ValueError("Incompatible partition layout: " + part)
        covered.add(part)
        if source == inactive and not classic:
            continue
        digest = pb(bytes.fromhex(source))
        info = f"size:{size} hash:{digest}"
        ext = f"start_block:0 num_blocks:{size // 4096}"
        updates.append(f'partitions {{ partition_name:"{part}" version:"{version}" '
                       f'old_partition_info{{{info}}} '
                       f'new_partition_info{{{info}}} operations {{ type:SOURCE_COPY '
                       f'src_extents{{{ext}}} dst_extents{{{ext}}} src_sha256_hash:{digest} }} }}')
    if covered != names:
        raise ValueError("Missing factory partitions: " + ", ".join(sorted(names-covered)))
    return updates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("aosp", "factory", "inventory", "source_system", "target_system", "output"):
        parser.add_argument(name, type=Path)
    channel = parser.add_mutually_exclusive_group()
    channel.add_argument("--to-v12", choices=["v1.003", "v1.100", "v1.101"], help="Build a direct update to Tommy UI v1.2")
    channel.add_argument("--skip-to-latest", action="store_true", help="Build v1.003 directly to Classic v1.1.1")
    channel.add_argument("--to-classic", action="store_true", help="Build the original v1.003 to v1.1 Classic payload")
    channel.add_argument("--classic", action="store_true", help="Build the v1.1 to v1.1.1 Classic payload")
    args = parser.parse_args()
    source_hash = "b2c95b70bc2d2a57acb345b026075c50d7224b57730c58ccb0cd1e191bacf95e" if args.classic else SOURCE_HASH
    if args.to_v12:
        source_hash = {"v1.003": SOURCE_HASH,
                       "v1.100": "b2c95b70bc2d2a57acb345b026075c50d7224b57730c58ccb0cd1e191bacf95e",
                       "v1.101": "0539fd853af72a1844bfd0384ebc682b7ba6806919418e2a0b76c61934691ca8"}[args.to_v12]
    if sha(args.source_system) != source_hash:
        parser.error("Unexpected source system image")
    def read_props(image):
        return subprocess.check_output(["debugfs", "-R", "cat /system/build.prop", image], text=True).splitlines()
    props = read_props(args.target_system)
    target = "TommyUI.v1.101.dev.20261109" if args.classic or args.skip_to_latest else "TommyUI.v1.004.dev.20261108"
    if args.to_v12:
        target = "TommyUI.v1.200.dev.20261109"
    if args.to_classic:
        target = "TommyUI.v1.100.dev.20261109"
        if sha(args.target_system) != "b2c95b70bc2d2a57acb345b026075c50d7224b57730c58ccb0cd1e191bacf95e":
            parser.error("Expected the released Classic v1.1 target")
    if "ro.build.version.incremental=" + target not in props:
        parser.error("Unexpected test target build")
    # The update engine rejects a partial update with error 23 unless every partition carries a
    # version, and with error 51 when one is older than the phone's ro.<partition>.build.date.utc.
    version = build_date(props)
    if version < build_date(read_props(args.source_system)):
        parser.error("Target build is older than the source")
    copies = copy_operations(args.factory, args.inventory, version, args.classic or args.to_classic or args.skip_to_latest or bool(args.to_v12))
    host = args.aosp / "out/host/linux-x86/bin"
    generator = host / "delta_generator"
    certdir = args.aosp / "build/make/target/product/security"
    proto = args.aosp / "system/update_engine/update_metadata.proto"
    os.environ["PATH"] = str(host) + os.pathsep + os.environ["PATH"]
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output) as directory:
        work = Path(directory)
        def gen(*args):
            subprocess.run([generator,*args],check=True)
        def protobuf(mode, data):
            return subprocess.check_output([host/"aprotoc", "--proto_path="+str(proto.parent),
                    f"--{mode}=chromeos_update_engine.DeltaArchiveManifest",proto.name],input=data)
        dynamic = work / "dynamic.txt"
        dynamic.write_text("super_partition_groups=mot_dp_group\nsuper_mot_dp_group_group_size=6706692096\n"
                           "super_mot_dp_group_partition_list=system\nvirtual_ab=true\n"
                           "virtual_ab_compression=true\nvirtual_ab_compression_method=gz\nvirtual_ab_cow_version=2\n")
        system = work / "system.delta"
        gen("--partition_names=system",f"--old_partitions={args.source_system}",
            f"--new_partitions={args.target_system}",f"--out_file={system}",
            f"--dynamic_partition_info_file={dynamic}","--is_partial_update=true",
            "--major_version=2","--minor_version=8",f"--max_timestamp={max(version,1794182400)}",
            f"--partition_timestamps=system:{version}",
            "--max_threads=3","--enable_puffdiff=false","--enable_zucchini=false")
        # Replay the generated system delta with the host implementation before adding firmware.
        replay = work / "replayed.img"
        with replay.open("wb") as f:
            f.truncate(args.target_system.stat().st_size)
        gen(f"--in_file={system}","--partition_names=system",f"--old_partitions={args.source_system}",
            f"--new_partitions={replay}","--is_partial_update=true")
        if sha(replay) != sha(args.target_system):
            raise ValueError("Replayed system differs from the built image")
        unsigned = work / "all.delta"
        with system.open("rb") as inp, unsigned.open("wb") as out:
            magic,major,length,signature = struct.unpack(">4sQQI", inp.read(24))
            if (magic,major,signature) != (b"CrAU",2,0):
                raise ValueError("Unexpected unsigned delta")
            text = protobuf("decode",inp.read(length))
            manifest = protobuf("encode",text+b"\n"+"\n".join(copies).encode())
            missing = unversioned(protobuf("decode",manifest).decode())
            if missing:
                raise ValueError("Partitions without a version: " + ", ".join(missing))
            out.write(struct.pack(">4sQQI",magic,major,len(manifest),0))
            out.write(manifest)
            shutil.copyfileobj(inp,out)
        hp,hm = work/"payload.hash",work/"metadata.hash"
        gen(f"--in_file={unsigned}","--signature_size=256",f"--out_hash_file={hp}",f"--out_metadata_hash_file={hm}")
        def sign(path):
            sig = path.with_suffix(".sig")
            subprocess.run(["openssl","pkeyutl","-sign","-keyform","DER","-inkey",certdir/"testkey.pk8",
                "-pkeyopt","digest:sha256","-in",path,"-out",sig],check=True)
            return sig
        payload = work / "Tommy-UI-fogona.bin"
        gen(f"--in_file={unsigned}", "--signature_size=256",f"--payload_signature_file={sign(hp)}",
            f"--metadata_signature_file={sign(hm)}",f"--out_file={payload}")
        pub = work/"public.pem"
        pub.write_bytes(subprocess.check_output(["openssl","x509","-in",certdir/"testkey.x509.pem","-pubkey","-noout"]))
        gen(f"--in_file={payload}", f"--public_key={pub}")
        properties = work/"payload_properties.txt"
        gen(f"--in_file={payload}",f"--properties_file={properties}")
        meta = work/"candidate.metadata"
        with payload.open("rb") as f:
            header = f.read(24)
            _,_,n,sig = struct.unpack(">4sQQI",header)
            meta.write_bytes(header+f.read(n+sig))
        for artifact in (payload,properties,meta):
            artifact.replace(args.output/artifact.name)
        print(f"Verified system replay, signatures and {len(copies)} firmware source-copy declarations.")


if __name__ == "__main__":
    main()
