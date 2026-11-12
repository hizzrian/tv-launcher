#!/bin/bash
# Mengembalikan Xiaomi MiTV-MZTU0 ke kondisi bawaan (launcher Google TV + aplikasi Xiaomi).
# Jalankan: bash kembalikan-launcher-bawaan.sh <IP_TV>
# Update aplikasi yang dihapus akan dipasang lagi otomatis oleh Play Store / Xiaomi.
TV="${1:?Pakai: bash kembalikan-launcher-bawaan.sh <IP_TV>}:5555"

adb connect "$TV"
for p in \
  com.google.android.apps.tv.launcherx \
  com.google.android.tungsten.setupwraith \
  com.mitv.tvhome.atv \
  com.mitv.tvhome.mitvplus \
  com.mitv.tvhome.oemtab \
  com.mitv.tvhome.michannel \
  com.miui.tv.analytics \
  com.xiaomi.statistic; do
  adb -s "$TV" shell pm enable "$p"
done
adb -s "$TV" shell cmd role add-role-holder android.app.role.HOME com.google.android.tungsten.setupwraith
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Selesai. Launcher dan aplikasi bawaan aktif kembali."
