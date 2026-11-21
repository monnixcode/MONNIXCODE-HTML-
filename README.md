# MONNIXCODE HTML Terminal

เวอร์ชัน Standalone ของ MONNIXCODE สำหรับเปิดใช้งานบน Browser โดยไม่มี Android project, Java, Gradle หรือ Gradle wrapper เป็นแอป HTML/CSS/JavaScript ที่เปิดผ่าน HTTP server ธรรมดา

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
