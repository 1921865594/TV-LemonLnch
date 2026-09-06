#!/system/bin/sh
# Root setup for MyTV Launcher's Android 9+ App Container.
# Run once after flashing/installing the launcher, then reboot if the ROM requires it.

settings put global enable_freeform_support 1
settings put global force_resizable_activities 1

# Hidden API policy: development/device-owner style setup, not required by the am backend itself.
SDK=$(getprop ro.build.version.sdk)
if [ "$SDK" = "28" ]; then
  settings put global hidden_api_policy_pre_p_apps 1
  settings put global hidden_api_policy_p_apps 1
elif [ "$SDK" -ge 29 ]; then
  settings put global hidden_api_policy 1
fi

echo "Freeform support and non-SDK access policy configured. SDK=$SDK"
