CARA BUILD APK TANPA PC (MENGGUNAKAN HP + GITHUB)

1. Ekstrak ZIP ini di HP.
2. Buka https://github.com lalu login / buat akun.
3. Buat repository baru. Nama misalnya power-scheduler-android.
4. Upload seluruh ISI folder power-scheduler-android ke repository (termasuk folder .github).
   Pastikan file .github/workflows/build-apk.yml ikut ter-upload.
5. Buka tab Actions pada repository.
6. Pilih workflow "Build Android APK" lalu tekan Run workflow.
   Jika workflow tidak muncul, buka tab Actions dan aktifkan Actions untuk repository.
7. Tunggu sampai workflow selesai dengan status hijau.
8. Buka hasil run tersebut, cari bagian Artifacts, lalu unduh
   power-scheduler-debug-apk. ZIP artifact berisi app-debug.apk.
9. Ekstrak artifact dan instal app-debug.apk di Android.

CATATAN PENTING:
- Proyek asal ini adalah penjadwal RESTART, bukan penjadwal power-off/power-on.
- Kode memanggil `su -c "svc power reboot"`, jadi membutuhkan root yang diberi izin.
- Android 9 (API 28) minimum. APK dibangun dengan compile/target API 35 agar dapat
  dipasang pada Android 9 sampai versi lebih baru, termasuk Android 17.
- Power-on dari kondisi mati total tidak dapat dijamin oleh aplikasi Android;
  itu memerlukan dukungan hardware/firmware perangkat.
