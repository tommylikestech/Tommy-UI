#!/usr/bin/env bash
# Build the newer system identity used only by the staged Update Zero trial.
set -eo pipefail
source_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
root="$(cd "$1" && pwd)"
python3 "$source_repo/tools/prepare.py" "$root" --verify-projects
cd "$root"
test "$(git -C .repo/manifests rev-parse HEAD)" = "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["manifest_commit"])' "$source_repo/release.json")"
source build/envsetup.sh
export BUILD_NUMBER=TommyUI.v1.004.dev.20261108 BUILD_DATETIME=1794182400
export NINJA_HIGHMEM_NUM_JOBS=1 GOMAXPROCS=4 GOMEMLIMIT=16GiB GOGC=50
lunch aosp_arm64-bp2a-userdebug
m -j"${TOMMYUI_JOBS:-8}" systemimage delta_generator
