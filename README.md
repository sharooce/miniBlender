
**project miniBlender Android** adalah aplikasi 3D Mesh Editor & Viewer berbasis Android yang dibangun secara kustom menggunakan **OpenGL ES 2.0**. Proyek ini dirancang ringan, efisien, dan ramah kontrol layar sentuh (*touch-first*) untuk memuat, memanipulasi, memodifikasi winding poligon, serta menyimpan objek 3D dalam berbagai format ASCII maupun biner.

---

## ✨ Fitur Utama

- **🎮 Kontrol Multi-Touch Intuitif**
  - **1 Jari**: Pindah posisi objek aktif
  - **2 Jari**: Rotasi objek 3D
  - **3 Jari**: Rotasi pandangan ruangan/kamera 360°
  - **Pinch-to-Zoom**: Zoom in/out kamera

- **🔄 Instant Winding Mode Toggle (FLIP FACE)**
  - Pengalihan mode segitiga secara instan antara **CCW (Counter-Clockwise)** dan **CW (Clockwise)** tanpa perlu *re-load* file.
  
- **💾 Scene & Multi-Object JSON Persistence (Auto-Save)**
  

dukungan export/ import model
 
- **💡 OpenGL ES 2.0 Dynamic Shader & Depth-Test Fix**

- **📁 Dukungan Format File Luas**
  - Import & Export format ASCII `.3d` kustom (0-based indexing).
  - Import & Export format Wavefront `.obj` (1-based indexing).
  - Import & Export format biner ringkas `.mdl` (*Little-Endian*).

---

## 🛠️ Arsitektur Proyek

```text
com.miniblender.editor
 ├── MainActivity.java      # Activity utama, Glass Button UI dinamis & Lifecycle listener
 ├── MyGLSurfaceView.java   # Custom GLSurfaceView penangan event Multi-Touch (1, 2, & 3 jari)
 ├── MyGLRenderer.java      # OpenGL Renderer, manajer List<EditorObject> & Cache Manager
 ├── EditorObject.java      # Wrapper objek 3D (Transformasi, Nama, & Serialisasi JSON)
 └── MeshModel.java         # Data struktur non-interleaved (v, vt, vn, f), Shader GPU, & Parser File
└── Editorobject.java     # penyimpanan data mesh

```
## 🚀 Cara Menjalankan Project
### Prasyarat
 * **Android Studio** Jellyfish / Ladybug atau yang lebih baru.
 * **Android SDK**: API Level 21 (Android 5.0) ke atas.
 * **OpenGL ES**: support minimum GLES 2.0.

### Langkah-langkah
 1. *Clone* repository 
   ```bash
   git clone https://github.com/sharooce/miniBlender.git
   
   ```
 2. Buka Android Studio/Aide android, lalu pilih **Open an Existing Project** dan arahkan ke folder proyek ini.
 3. Jalankan *Gradle Sync* dan pastikan semua dependencies terunduh.
 4. Hubungkan perangkat Android / jalankan Emulator, lalu tekan **Run (Shift + F10)**.

## 🖐️ Panduan Kontrol & Gestur
| Gestur Layar | Aksi / Mode |
|---|---|
| **1 Jari** | Menyeleksi vertex / menggeser posisi objek
| **2 Jari** | Memutar rotasi objek 3D aktif 
| **3 Jari** | Memutar rotasi kamera / ruangan 
| **Cubit / Pinch** | Zoom In / Zoom Out pandangan kamera 
| **Tombol FLIP FACE** | Membalik arah hadap poligon 

project progress belum sempurna masih ada bug texture blm fix
project sekitar 80% 

## 📝 Lisensi
Proyek ini dirilis di bawah lisensi MIT License. Bebas untuk dikembangkan dan dimodifikasi lebih lanjut.
```
