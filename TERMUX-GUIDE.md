# คู่มือ MONNIXCODE HTML บน Termux

คู่มือนี้อธิบายการติดตั้งและใช้งาน MONNIXCODE HTML Terminal บน Android ผ่าน Termux ตั้งแต่เริ่มต้นจนถึงการอัปเดตโปรเจกต์และแก้ปัญหาเบื้องต้น

> **สำคัญ:** เวอร์ชันนี้เป็น Terminal จำลองบน Browser ด้วย HTML/CSS/JavaScript ไม่ใช่ Linux shell จริงในหน้าเว็บ คำสั่ง `nix` ที่อยู่ในหน้าเว็บเป็นการจำลอง และไม่สามารถติดตั้งแพ็กเกจจริงด้วย `apt` หรือ `pkg` ได้

## 1. สิ่งที่ต้องเตรียม

- โทรศัพท์ Android ที่ติดตั้ง Termux แล้ว
- อินเทอร์เน็ตสำหรับดาวน์โหลด Git และโปรเจกต์ครั้งแรก
- พื้นที่ว่างอย่างน้อยประมาณ 30 MB
- เบราว์เซอร์ เช่น Chrome, Firefox หรือเบราว์เซอร์ที่รองรับ JavaScript

แนะนำให้ติดตั้ง Termux จาก **F-Droid หรือ GitHub Releases ของ Termux** แทนการใช้ APK จากแหล่งที่ไม่รู้จัก และไม่ควรติดตั้ง Termux จากหลายแหล่งปะปนกัน เพราะลายเซ็นของแอปอาจไม่ตรงกัน

## 2. ติดตั้งเครื่องมือใน Termux

เปิดแอป Termux แล้วรันทีละคำสั่ง:

```sh
pkg update && pkg upgrade -y
pkg install git python -y
```

ตรวจสอบว่าเครื่องมือพร้อมใช้งาน:

```sh
git --version
python --version
```

ควรเห็นเวอร์ชันของ Git และ Python แสดงออกมา หากเห็น `command not found` ให้รันคำสั่งติดตั้งในขั้นตอนนี้อีกครั้ง

## 3. ดาวน์โหลดโปรเจกต์จาก GitHub

โคลน repository:

```sh
cd ~
git clone https://github.com/monnixcode/MONNIXCODE-HTML-.git
cd MONNIXCODE-HTML-
```

ตรวจสอบไฟล์:

```sh
ls -la
```

ควรพบไฟล์หลักดังนี้:

```text
index.html
run.sh
README.md
LICENSE.md
TERMUX-GUIDE.md
```

## 4. เปิดใช้งานด้วยสคริปต์ `run.sh`

ให้สิทธิ์สคริปต์ก่อนใช้งานครั้งแรก:

```sh
chmod +x run.sh
```

เริ่มเว็บเซิร์ฟเวอร์:

```sh
./run.sh
```

เมื่อเริ่มสำเร็จ จะเห็นข้อความลักษณะนี้:

```text
MONNIXCODE HTML is running at http://localhost:8080
Press Ctrl+C to stop.
```

เปิด Chrome หรือเบราว์เซอร์บนโทรศัพท์ แล้วเข้า URL:

```text
http://127.0.0.1:8080
```

หรือ:

```text
http://localhost:8080
```

ให้เปิดเว็บเซิร์ฟเวอร์ค้างไว้ใน Termux ขณะที่ใช้งานหน้าเว็บ หากกด `Ctrl+C` หรือปิดเซสชัน Termux เว็บจะหยุดทำงาน

## 5. เลือกพอร์ตอื่น

ถ้าพอร์ต 8080 ถูกใช้งานอยู่ ให้เลือกพอร์ตอื่น เช่น 3000:

```sh
./run.sh 3000
```

แล้วเปิด:

```text
http://127.0.0.1:3000
```

หรือใช้ Python โดยตรง:

```sh
python -m http.server 3000
```

## 6. รันแบบเบื้องหลัง

หากต้องการคืนหน้าจอ Termux และปล่อยเซิร์ฟเวอร์ทำงานเบื้องหลัง:

```sh
nohup ./run.sh 8080 > monnixcode.log 2>&1 &
```

ตรวจสอบว่าเซิร์ฟเวอร์ยังทำงานอยู่:

```sh
ps -A | grep http.server
```

ดู log:

```sh
cat monnixcode.log
```

หยุดเซิร์ฟเวอร์:

```sh
pkill -f 'http.server 8080'
```

หากใช้พอร์ตอื่น ให้เปลี่ยนเลข `8080` ให้ตรงกับพอร์ตที่ใช้งาน

## 7. การใช้งานภายในหน้า Terminal

เมื่อเปิดหน้าเว็บแล้ว ให้พิมพ์คำสั่งในช่องด้านล่างและกด Enter

### คำสั่งจัดการแพ็กเกจแบบจำลอง

```sh
nix help
nix up
nix in git curl
nix rm git
nix se python
nix ls
nix root
```

ความหมาย:

- `nix help` แสดงรายการคำสั่ง
- `nix up` จำลองการอัปเดต package database
- `nix in git curl` จำลองการติดตั้งแพ็กเกจ `git` และ `curl`
- `nix rm git` จำลองการลบแพ็กเกจ
- `nix se python` ค้นหาแพ็กเกจจากรายการจำลอง
- `nix ls` แสดงแพ็กเกจที่อยู่ในรายการติดตั้ง
- `nix root` แจ้งข้อจำกัดของ browser sandbox

### คำสั่งพื้นฐาน

```sh
clear
pwd
whoami
date
uname
neofetch
echo hello
ls
```

### ปุ่มลัด

- `Enter` รันคำสั่ง
- `Arrow Up` เรียกคำสั่งก่อนหน้า
- `Arrow Down` เรียกคำสั่งถัดไป
- `Ctrl + L` ล้างหน้าจอ
- ปุ่ม `Clear` ล้าง output ทั้งหมด

## 8. การเปลี่ยนธีม

กดปุ่มธีมที่ด้านบนของหน้าเพื่อวนธีมตามลำดับ:

```text
Classic → Cyberpunk → Matrix → Classic
```

- **Classic:** เขียวแบบ Terminal ดั้งเดิม
- **Cyberpunk:** neon ชมพู/ม่วง/ฟ้า พร้อม glow และ grid
- **Matrix:** เขียวเข้มแบบ Matrix พร้อม scanline

ธีม, ประวัติคำสั่ง และรายการแพ็กเกจจำลองจะถูกเก็บไว้ใน `localStorage` ของเบราว์เซอร์เครื่องนั้น

## 9. อัปเดตเป็นเวอร์ชันล่าสุดจาก GitHub

เข้าโฟลเดอร์โปรเจกต์แล้วรัน:

```sh
cd ~/MONNIXCODE-HTML-
git pull --ff-only origin main
chmod +x run.sh
```

จากนั้นหยุดเซิร์ฟเวอร์เดิมด้วย `Ctrl+C` แล้วเปิดใหม่:

```sh
./run.sh
```

ถ้าเคยแก้ไขไฟล์เองและ `git pull` แจ้ง conflict ให้สำรองไฟล์ก่อน แล้วตรวจสอบสถานะ:

```sh
git status
git diff
```

สำหรับการติดตั้งใหม่แบบสะอาด ให้ใช้:

```sh
cd ~
mv MONNIXCODE-HTML- MONNIXCODE-HTML-backup

git clone https://github.com/monnixcode/MONNIXCODE-HTML-.git
cd MONNIXCODE-HTML-
chmod +x run.sh
./run.sh
```

## 10. แก้ปัญหาที่พบบ่อย

### เปิด `localhost:8080` ไม่ได้

ตรวจสอบว่า Termux ยังแสดงเซิร์ฟเวอร์อยู่หรือไม่ และดู log หากรันแบบเบื้องหลัง:

```sh
ps -A | grep http.server
cat monnixcode.log
```

ลองใช้ IP loopback อีกแบบ:

```text
http://127.0.0.1:8080
```

### แจ้งว่า Address already in use

มีโปรแกรมใช้พอร์ตอยู่แล้ว ให้ใช้พอร์ตใหม่:

```sh
./run.sh 8081
```

แล้วเปิด `http://127.0.0.1:8081`

### แจ้ง Permission denied ตอนใช้ `./run.sh`

รัน:

```sh
chmod +x run.sh
```

หรือเรียกผ่าน Bash โดยตรง:

```sh
bash run.sh
```

### `git clone` ไม่สำเร็จ

ตรวจสอบอินเทอร์เน็ตและลองอัปเดตแพ็กเกจ:

```sh
pkg update
pkg install git -y
```

จากนั้นลอง clone ใหม่อีกครั้ง

### หน้าเว็บไม่เปลี่ยนหลัง `git pull`

รีเฟรชแบบล้าง cache โดยกด reload ค้างไว้ หรือเปิดหน้าเว็บในแท็บใหม่ และตรวจสอบว่า URL ชี้ไปที่พอร์ตของเซิร์ฟเวอร์ตัวล่าสุด

### คำสั่ง `apt`, `pkg`, `sudo` ในหน้าเว็บใช้งานไม่ได้

เป็นพฤติกรรมปกติ เพราะหน้า HTML เป็น simulator ที่ไม่สามารถเรียกใช้ shell ของ Android ได้ หากต้องการใช้คำสั่ง Linux จริง ให้พิมพ์ในแอป Termux โดยตรง ไม่ใช่ในช่อง Terminal ของหน้าเว็บ

## 11. ความปลอดภัยและข้อจำกัด

- เว็บเซิร์ฟเวอร์นี้เหมาะสำหรับใช้งานภายในเครื่องด้วย `localhost`
- อย่าเปิดพอร์ตออกสู่อินเทอร์เน็ตโดยไม่เข้าใจความเสี่ยง
- หน้าเว็บไม่รันคำสั่ง shell จริงและไม่อ่านไฟล์ส่วนตัวในโทรศัพท์
- รายการแพ็กเกจใน `nix` เป็นข้อมูลจำลอง ไม่ได้ติดตั้ง package จริง
- การล้างข้อมูลเว็บไซต์หรือ `localStorage` จะลบธีม ประวัติคำสั่ง และ package state ที่จำลองไว้
- ห้ามนำรหัสผ่าน, access token หรือข้อมูลส่วนตัวไปใส่ในหน้าเว็บหรือ commit ลง GitHub

## 12. คำสั่งสรุปแบบเร็ว

ติดตั้งและรันครั้งแรก:

```sh
pkg update && pkg upgrade -y
pkg install git python -y
git clone https://github.com/monnixcode/MONNIXCODE-HTML-.git
cd MONNIXCODE-HTML-
chmod +x run.sh
./run.sh
```

อัปเดตภายหลัง:

```sh
cd ~/MONNIXCODE-HTML-
git pull --ff-only origin main
./run.sh
```
