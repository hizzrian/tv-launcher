# Kaca: launcher Android TV yang bersih, bergaya kaca

Kaca adalah launcher (layar utama) untuk Android TV / Google TV. Tidak ada iklan, banner promosi, baris Input, atau tombol Settings di layar utama. Yang ada:

- **Hero penuh layar** berisi artwork film dan serial populer. Gambarnya berganti otomatis setiap 8 detik dengan crossfade dan efek zoom pelan (Ken Burns), lengkap dengan logo judul, tahun, genre, dan rating.
- **Dock kaca** untuk 5 aplikasi favorit. Latar di baliknya diburamkan secara real-time (`RenderEffect`).
- **Grid 5 kolom** untuk semua aplikasi, memakai banner resmi tiap aplikasi.
- **Ikon gear tersembunyi**: tekan ↑ dari dock untuk membuka Settings TV.
- Ringan: **APK ±37 KB**, ditulis dengan Java murni tanpa library tambahan, di-build tanpa Gradle.

Diuji di Xiaomi TV (Google TV, Android 14, 1920×1080).

## Kontrol remote

| Tombol | Fungsi |
|---|---|
| ← → ↑ ↓ | Pindah antar-aplikasi |
| ↑ dari dock | Pilih ikon gear (Settings TV) |
| OK | Buka aplikasi / Settings |
| Tahan OK atau Menu | Pindahkan ke/dari dock, geser kiri/kanan, sembunyikan, tampilkan lagi aplikasi tersembunyi, Pengaturan TV |
| Back | Kembali ke dock |

## Build

Butuh JDK 17 dan Android SDK (platform 34, build-tools 34.0.0):

```bash
brew install openjdk@17
brew install --cask android-commandlinetools android-platform-tools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
./build.sh
```

Hasilnya ada di `build/kaca.apk`. Saat build pertama, `build.sh` membuat kunci tanda tangan `kaca.keystore` beserta kata sandinya di `keystore.properties`. Kedua file itu sengaja **tidak di-commit**. Simpan baik-baik, karena update APK harus ditandatangani dengan kunci yang sama.

## Pasang ke TV

Aktifkan **Developer options → USB/Network debugging** di TV, lalu:

```bash
adb connect <IP_TV>:5555
adb install -r build/kaca.apk
adb shell cmd role add-role-holder android.app.role.HOME com.kaca.launcher 0
```

Di Google TV, launcher bawaan dan setup wizard akan mengambil alih tombol Home. Nonaktifkan keduanya (bisa dikembalikan):

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Akibatnya, tombol Settings di remote (yang membuka panel Dashboard Google TV) tidak berfungsi lagi. Gunakan ikon gear di Kaca sebagai gantinya.

## Kembali ke launcher bawaan

```bash
bash scripts/kembalikan-launcher-bawaan.sh <IP_TV>
```

## Struktur

```
src/com/kaca/launcher/
  HomeActivity.java   layar utama, tata letak, navigasi remote, gear, dock kaca
  Hero.java           rotasi artwork, crossfade, Ken Burns, blur saat di grid
  Catalog.java        katalog publik Cinemeta + cache gambar di disk (refresh 6 jam)
  AppStore.java       daftar aplikasi TV, urutan dock, aplikasi tersembunyi
  TileView.java       ikon aplikasi: banner, kilap kaca, efek fokus
  MenuOverlay.java    menu tahan-OK
design/mockup-launcher.html   mockup desain (buka di browser, navigasi dengan panah)
scripts/                      skrip pemulihan
```

Tata letak dirancang di kanvas virtual 1920×1080 dan diskalakan ke resolusi layar.

## Catatan

- Artwork film diambil saat runtime dari katalog publik [Cinemeta](https://v3-cinemeta.strem.io/) (tanpa API key) dan tidak disertakan di repo ini. Hak cipta gambar tetap milik pemiliknya masing-masing.
- Banner dan ikon aplikasi diambil dari aplikasi yang terpasang di TV.
