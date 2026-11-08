# Tommy UI v1.001 additions to aosp_arm64; keep stock vendor interfaces intact.
PRODUCT_PACKAGES += TommyStorage TommyBootAnimation
# This product adds exactly one system artifact beyond generic_system.mk.
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += system/media/bootanimation.zip
