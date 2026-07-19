# Business Rivals Online — Demo Vercel

Demo konsep game bisnis 3D online berbasis browser. Dibuat tanpa build step dan tanpa backend, jadi bisa langsung diunggah ke Vercel.

## Fitur demo

- Kota bergaya 3D isometrik dan karakter yang bisa digerakkan dengan WASD/tombol arah
- Pembuatan perusahaan dan pilihan sektor bisnis
- Simulasi operasi bisnis, investasi, pertumbuhan valuasi, dan event pasar
- Partner bisnis virtual
- Leaderboard perusahaan dan countdown season dua bulanan
- Penyimpanan progres demo menggunakan localStorage
- Tampilan responsif untuk desktop dan mobile

## Deploy ke Vercel

1. Ekstrak folder ini.
2. Masuk ke Vercel dan pilih **Add New > Project**.
3. Impor folder/repository ini.
4. Framework Preset: **Other**.
5. Build Command dan Output Directory dikosongkan.
6. Klik **Deploy**.

Atau dengan Vercel CLI:

```bash
npm i -g vercel
vercel
```

## Catatan

Ini adalah prototype front-end. Multiplayer nyata, akun, server ekonomi, database, anti-cheat, chat, dan build APK belum termasuk.
