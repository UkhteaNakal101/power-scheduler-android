# Panduan Build & Setup Power Scheduler (Android 9–17)

Proyek ini dikonfigurasi untuk berjalan pada **Android 9 (API 28)** hingga **Android 17 (API 37)** dengan dukungan **multi-jadwal kustom** (Senin–Minggu):

- **Restart Dini Hari Kerja** (Aktif): pukul `03:00` pada hari [Sen, Sel, Rab, Kam, Jum]
- **Pemeliharaan Akhir Pekan** (Aktif): pukul `04:30` pada hari [Sab, Min]

## 1. Persiapan Proyek Android Studio
1. Ekstrak arsip ZIP ini.
2. Buat proyek baru di Android Studio (**Empty Activity - Compose**) dengan `Package name`: `id.powerscheduler.app` dan `Minimum SDK`: **API 28 (Android 9.0)**.
3. Salin isi folder `app/` ke direktori proyek Anda dan lakukan **Sync Now (Gradle)**.

## 2. Aktivasi Metode Eksekusi (ROOT)
### Mode Root (Magisk / KernelSU)
- Pastikan perangkat sudah di-root.
- Saat aplikasi menjalankan uji coba pertama kali, tekan **Izinkan (Grant)** pada pop-up permintaan akses Superuser untuk `id.powerscheduler.app`.

## 3. Izin Wajib Android 9–17
1. **Alarm & Pengingat (Android 12–17)**: Izinkan *Alarms & Reminders* agar `AlarmManager.setAlarmClock()` tidak tertunda oleh Doze.
2. **Baterai Tanpa Batas**: Setel penggunaan baterai aplikasi menjadi **Tidak Dibatasi (Unrestricted)**.
3. **Notifikasi (Android 13–17)**: Izinkan notifikasi agar panel hitungan mundur `30 detik` tampil dengan tombol Tunda / Batalkan.
