# Standard Magisk/KernelSU installer environment. Never alters launcher/system files.
[ "$ARCH" = arm64 ] || abort 'HyperDream Runtime requires arm64.'
[ "$API" -ge 31 ] || abort 'Android 12 or newer required.'
[ -x /system_ext/bin/hyos_spawner ] || abort 'HyperOS native spawner not present.'
ui_print 'Requires Zygisk Next with module API v4 and HYOS API v1.'
ui_print 'Diagnostic observer only: animation duration is NOT patched.'
ui_print 'Reboot, open/close apps, then use HyperDream > Device > Hook report.'
ui_print 'Recovery: disable HyperDream Native Runtime Probe in your root manager.'
set_perm_recursive "$MODPATH" 0 0 0755 0644
