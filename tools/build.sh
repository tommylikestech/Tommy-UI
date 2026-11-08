#!/usr/bin/env bash
set -eo pipefail
release="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$1" && pwd)"
cd "$root"
source build/envsetup.sh
test "$(git -C .repo/manifests rev-parse HEAD)" = "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["manifest_commit"])' "$release/../release.json")"
python3 "$release/prepare.py" "$root" --verify-projects
export BUILD_NUMBER="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["build_number"])' "$release/../release.json")"
export BUILD_DATETIME="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["build_datetime"])' "$release/../release.json")"
export NINJA_HIGHMEM_NUM_JOBS=1 GOMAXPROCS=4 GOMEMLIMIT=16GiB GOGC=50
lunch aosp_arm64-bp2a-userdebug
m -j"${TOMMYUI_JOBS:-8}" systemimage
test -s out/target/product/generic_arm64/system.img
