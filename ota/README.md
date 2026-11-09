# Publish a wireless update

Settings fetches one small, signed [version.txt](https://tommylikestech.github.io/Tommy-UI/version.txt) at boot when a network becomes available and whenever someone opens **About Tommy UI → Update Tommy UI** or taps **Check for updates**. A boot check can notify the user. Download and installation require a tap. The Android update engine streams the payload from the GitHub release asset, checks its metadata and payload hashes and platform signature, and stages the update for a user-requested restart. The feed signing public key is in `feed-public.pem`.

For an OS release, build a signed A/B OTA for fogona from device-specific target files using Android release tools. The source build, vendor firmware, inactive-slot contents and boot verification configuration must match the supported upgrade path. Test the upgrade, successful boot, rollback and preservation of adopted storage on that configuration before advertising the package.

1. Run `python3 tools/describe_ota.py OTA.zip release-out --vendor VENDOR_FINGERPRINT --type content --summary "Release notes"` (use `security` for a security release). It extracts the raw payload, checks its sizes and hashes, and reads the exact build prerequisites. Verify the OTA with the appropriate release signing keys during the device release build. This helper checks structure and hashes.
2. Publish the `.bin` asset on the repository's GitHub Release under the exact version tag from the descriptor. Check the asset is downloadable before updating Pages.
3. Run `python3 tools/sign_feed.py release-out/version-unsigned.txt /secure/path/feed-signing.pem docs/version.txt`. Commit the signed file. The RSA private key belongs with the maintainer, outside this repository. The public key for Android's update engine is part of the device image and has a different role.
4. Test the Pages response and open the update page from the supported source build. It shows installed Android and Tommy UI build, update type and release details. A source release has an empty URL. USB kits, APKs and raw system images are not OTA payloads.

The v1.003 GitHub Release is a content release with source only. The updater client can be installed on the v1.003 development system as matching platform-signed Settings and Tommy Storage APK updates. Install both before opening Update Tommy UI. They are built with the platform certificate used by this development ROM.

## Classic v1.1.1 preview

The preview upgrades the exact v1.1 Classic image on the North American XT2413-2 with the matching vendor build. Its version in signed feeds and release tags is `v1.101`. Capture the device inventory with `ota/update-zero/capture_pairs.py --classic --serial SERIAL inventory.txt` and pass `--classic` to `ota/update-zero/build_candidate.py` with the factory recipe, inventory, v1.1 source image and v1.1.1 target image. The inventory lists the active slot first.

Before enrollment, verify the signed metadata on the source phone with Android's `update_engine_client --verify`. On an enrolled v1.1 test phone, the matching platform-signed Settings client (version code 40) reads the signed `classic-preview/version.txt` when the global setting `tommyui_classic_preview` is 1. The opt-in applies only to this source build. It uses the regular Download and install flow, including Android's slot switch. Tap Restart to finish when Android offers it. Verify the running build, successful boot, snapshot merge and adopted storage after the restart. Publish a stable descriptor only after hardware validation.
