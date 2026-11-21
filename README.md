# MONNIXCODE HTML Terminal

เวอร์ชัน Standalone ของ MONNIXCODE สำหรับเปิดใช้งานบน Browser โดยไม่มี Android project, Java, Gradle หรือ Gradle wrapper เป็นแอป HTML/CSS/JavaScript ที่เปิดผ่าน HTTP server ธรรมดา

## Public online

เมื่อเปิดใช้งาน GitHub Pages แล้ว สามารถเปิดหน้าเว็บสาธารณะได้ที่:

<https://monnixcode.github.io/MONNIXCODE-HTML-/>

หน้า online ใช้ `index.html` จาก branch `main` โดยตรง และไม่ต้องติดตั้งอะไรในเครื่องสำหรับการทดลองหน้าเว็บ

## ติดตั้งและรันอย่างรวดเร็วบน Termux

```sh
pkg update && pkg upgrade -y
pkg install git python -y
git clone https://github.com/monnixcode/MONNIXCODE-HTML-.git
cd MONNIXCODE-HTML-
chmod +x run.sh
./run.sh
```

จากนั้นเปิดใน Browser:

```text
http://127.0.0.1:8080
```

หากต้องการใช้พอร์ตอื่น:

```sh
./run.sh 3000
```

แล้วเปิด `http://127.0.0.1:3000`

## คู่มือ Termux แบบละเอียด

อ่านคู่มือฉบับเต็มได้ที่ [TERMUX-GUIDE.md](TERMUX-GUIDE.md) ซึ่งครอบคลุม:

- การติดตั้ง Termux, Git และ Python
- การ clone และอัปเดตโปรเจกต์จาก GitHub
- การรันด้วย `run.sh` และ `python -m http.server`
- การรันแบบเบื้องหลังและการหยุดเซิร์ฟเวอร์
- การใช้งานคำสั่ง `nix` และ keyboard shortcuts
- การเปลี่ยนธีม Classic, Cyberpunk และ Matrix
- การแก้ปัญหา port, permission, git และ cache
- ข้อจำกัดด้าน browser sandbox และความปลอดภัย

## Termux source code

ใน repository นี้มี source code ของ Termux ที่แนบมาเก็บแยกไว้ในโฟลเดอร์ [`termux-source/`](termux-source/) เพื่อใช้เป็น source อ้างอิงหรือพัฒนาต่อฝั่ง Android โดยไม่ปะปนกับ HTML Terminal

- [`TERMUX-SOURCE.md`](TERMUX-SOURCE.md) — อธิบายโครงสร้างและขอบเขตการใช้งาน
- [`termux-source/README.md`](termux-source/README.md) — คู่มือของ Termux source เดิม
- [`termux-source/LICENSE.md`](termux-source/LICENSE.md) — license และ notices ของ source เดิม

ส่วน `index.html` ใช้สำหรับ GitHub Pages และไม่ใช้ Gradle ส่วน `termux-source/` เป็น Android source แยกชุด หากจะ build APK ต้องทำตาม dependencies และคำสั่งในเอกสารของ Termux source

## ธีม Terminal

กดปุ่ม Theme เพื่อวนเปลี่ยนธีม:

- **Classic** — สีเขียวแบบ Terminal ดั้งเดิม พร้อม scanline
- **Cyberpunk** — neon magenta/cyan, grid และ glow
- **Matrix** — สีเขียวเข้มแบบ Matrix พร้อม scanline หนาแน่น

ธีมที่เลือกจะถูกบันทึกใน browser และเปิดกลับมาใช้ธีมเดิมในครั้งถัดไป

## คำสั่งที่รองรับในหน้าเว็บ

```sh
nix help
nix up
nix in git curl
nix rm git
nix se python
nix ls
nix root
clear
pwd
whoami
date
uname
neofetch
echo hello
ls
```

คำสั่งเหล่านี้เป็นการจำลองภายใน JavaScript ไม่ได้รัน shell จริง ไม่สามารถใช้ `apt`, `pkg`, `sudo` หรือติดตั้งแพ็กเกจลง Android ได้ หากต้องการรันคำสั่ง Linux จริง ให้พิมพ์ใน Termux โดยตรง

## ไฟล์หลัก

- `index.html` — หน้า Terminal และ logic ทั้งหมด
- `run.sh` — สคริปต์เปิด Python HTTP server
- `TERMUX-GUIDE.md` — คู่มือการติดตั้งและใช้งาน Termux แบบละเอียด
- `LICENSE.md` — ข้อมูลลิขสิทธิ์
