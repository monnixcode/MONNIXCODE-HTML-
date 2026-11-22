# คู่มือ Bootstrap สำหรับผู้ใช้ MONNIXCODE Workspace

คู่มือนี้อธิบายระบบ Bootstrap แบบฝังใน APK สำหรับผู้ที่ไม่เคยใช้ Termux หรือ Linux มาก่อน

## ผู้ใช้ทั่วไปไม่ต้องดาวน์โหลด Bootstrap เอง

MONNIXCODE Workspace รุ่น embedded จะมี Bootstrap archive แยกตาม CPU architecture อยู่ใน APK แล้ว ระบบจะเลือก archive ที่ตรงกับโทรศัพท์และติดตั้งเองเมื่อเปิดแอปครั้งแรก

ผู้ใช้ไม่ต้อง:

- ดาวน์โหลดไฟล์ `.zip` หรือ `.tar.xz` เอง
- คำนวณ SHA-256 เอง
- เลือก archive จาก file picker
- ติดตั้ง Termux ก่อน
- root โทรศัพท์
- ติดตั้ง Magisk หรือใช้ `su`

ปุ่ม **Runtime** ในหน้าแอปใช้ดูสถานะเท่านั้น ไม่ใช่ปุ่มเลือก archive แบบรุ่นเก่า

> คำว่า “root” ในเอกสารนี้หมายถึง app-private runtime root ไม่ใช่ Android system root

## Bootstrap ที่ฝังมาจากไหน

ระบบ build ใช้ release ที่ pin ไว้ของ `termux/termux-packages` และตรวจ SHA-256 ก่อนฝังลง APK โดยมี archive สำหรับ:

- `arm64-v8a` จาก `bootstrap-aarch64.zip`
- `armeabi-v7a` จาก `bootstrap-arm.zip`
- `x86` จาก `bootstrap-i686.zip`
- `x86_64` จาก `bootstrap-x86_64.zip`

นี่เป็น user-space base runtime ที่ใช้แนวคิดเดียวกับ Termux ไม่ใช่การฝังแอป Termux ทั้งหมด และไม่ใช่ Android system root

Bootstrap จะติดตั้งไว้ในพื้นที่ส่วนตัวของแอป:

```text
/data/data/com.monnixcode.workspace/files/monnixcode-runtime/
├── home/
├── tmp/
└── usr/
    ├── bin/
    ├── etc/
    ├── lib/
    └── var/
```

## ขั้นตอนการติดตั้งอัตโนมัติ

### 1. ติดตั้ง APK

ติดตั้ง APK รุ่นที่ build พร้อม embedded Bootstrap แล้วเปิดแอปตามปกติ

APK ที่ build แบบเก่าซึ่งไม่มี archive ฝังอยู่จะไม่สามารถติดตั้ง runtime อัตโนมัติได้

### 2. เปิดแอปครั้งแรก

แอปจะตรวจ ABI จาก Android แล้วเลือก archive ที่ตรงกัน จากนั้นทำงานตามลำดับ:

```text
เลือก ABI
→ เปิด embedded archive
→ แตกไฟล์ลง staging
→ ตรวจ path และ symlink
→ ตรวจ usr/bin/sh
→ activate แบบ atomic
→ บันทึกสถานะ verified
```

ไม่ควรปิดแอประหว่างการติดตั้งครั้งแรก โดยเฉพาะเมื่อพื้นที่ว่างเหลือน้อย

### 3. ตรวจสถานะ

กดปุ่ม **Runtime** เพื่อดูสถานะ โดยสถานะที่คาดหวังคือ:

```text
Embedded Bootstrap: verified
```

ถ้ายังเห็น `not-installed` หรือ `layout-installed` แสดงว่า runtime ยังไม่พร้อมและแอปจะไม่อ้างว่า Linux shell ใช้งานได้จริง

### 4. เปิด Terminal

เมื่อสถานะเป็น `verified` แอปจะเริ่ม native PTY และเรียก:

```text
<runtime>/usr/bin/sh -i
```

ลองคำสั่งพื้นฐาน:

```sh
pwd
ls
printf '%s\n' "$SHELL"
printf '%s\n' "$PREFIX"
printf '%s\n' "$HOME"
```

ค่าที่คาดหวัง:

- `SHELL` ชี้ไปยัง `.../monnixcode-runtime/usr/bin/sh`
- `PREFIX` ชี้ไปยัง `.../monnixcode-runtime/usr`
- `HOME` อยู่ภายใน runtime
- `pwd` เริ่มต้นที่ runtime home

## เข้าใจคำว่า “เหมือน Termux” อย่างถูกต้อง

MONNIXCODE ใช้แนวทางเดียวกับ Termux ในส่วนสำคัญ ได้แก่ shell ใน user-space, prefix, HOME, PATH, library path และ PTY แบบ interactive

แต่ยังไม่ควรตีความว่าเป็น Termux ทุกส่วน เพราะ MONNIXCODE มี:

- application ID ของตัวเอง
- UI และ lifecycle ของตัวเอง
- native PTY layer ที่พัฒนาสำหรับแอปนี้
- package integration และ repository configuration ที่อาจต่างจาก Termux
- ข้อจำกัดของ Android sandbox และ OEM

คำสั่งจะมีเท่าที่อยู่ใน Bootstrap และ package set ที่ติดตั้ง หากต้องการ `git`, `python`, `node`, `vim` หรือโปรแกรมอื่น ต้องมี package เหล่านั้นใน runtime หรือมีระบบ package repository ที่รองรับ

## CPU architecture สำหรับผู้ที่ต้องตรวจสอบ

โดยทั่วไปโทรศัพท์ Android รุ่นใหม่ใช้ `arm64-v8a`

หากมี Termux อยู่แล้ว ให้เปิด Termux และใช้:

```sh
uname -m
```

การแปลงค่าที่พบบ่อย:

```text
ตัวเลข/ข้อความจาก uname    ABI ของ Android
------------------------------------------
aarch64                     arm64-v8a
armv7l หรือ armv8l          armeabi-v7a
x86_64                      x86_64
i686 หรือ i386              x86
```

ผู้ใช้ทั่วไปไม่ต้องเลือกค่าเหล่านี้เอง เพราะ APK มี archive ครบตาม ABI ที่รองรับ แต่ข้อมูลนี้มีประโยชน์เมื่อวิเคราะห์ปัญหา

## ถ้า auto-install ไม่สำเร็จ

### สถานะเป็น `not-installed`

สาเหตุที่เป็นไปได้:

- ติดตั้ง APK รุ่นเก่าที่ไม่มี embedded archives
- build APK เองโดย task ดาวน์โหลด Bootstrap ไม่สำเร็จ
- ABI ของอุปกรณ์ไม่อยู่ใน 4 แบบที่รองรับ
- APK ถูกตัด assets ออกระหว่าง packaging

ให้ติดตั้ง APK release รุ่นใหม่จากแหล่งเดียวกัน และอย่าผสม APK จากคนละ source หรือคนละ signing key

### สถานะเป็น `layout-installed`

สถานะนี้หมายถึงมีเพียงโครงสร้างโฟลเดอร์เริ่มต้น ไม่ใช่ Bootstrap ที่ติดตั้งสมบูรณ์ แอปจะไม่เริ่ม shell จริงจนกว่าจะมีสถานะ `verified`

### ข้อความ `Required bootstrap file is missing`

archive ที่ถูกฝังไม่ครบหรือผิดรุ่น ต้อง build APK ใหม่โดยไม่แก้ไข archive และตรวจ SHA-256 pinning ให้ผ่าน

### Shell เริ่มไม่ได้

ตรวจสอบตามลำดับ:

1. ปุ่ม Runtime แสดง `verified` หรือไม่
2. อุปกรณ์ตรงกับ ABI ที่ APK รองรับหรือไม่
3. มีพื้นที่ว่างเพียงพอหรือไม่
4. แอปถูกหยุดโดย battery manager ของผู้ผลิตหรือไม่
5. ดู Android logcat ด้วย tag `MonnixcodePTY`

### คำสั่งหาไม่พบ

เช่น:

```text
git: command not found
python: command not found
```

ไม่ได้แปลว่า PTY เสีย แต่หมายความว่าโปรแกรมนั้นไม่ได้อยู่ใน Bootstrap หรือยังไม่ได้ติดตั้ง package ที่เกี่ยวข้อง

### แอปถูกหยุดเมื่ออยู่เบื้องหลัง

OEM บางรายมี battery optimization ที่เข้มงวด ให้ตรวจการตั้งค่า Battery ของแอปตามความเหมาะสม แอปไม่สามารถข้ามนโยบายของ Android ได้เอง

## สำหรับผู้พัฒนา: embedded archive ถูกสร้างอย่างไร

Gradle task `downloadBootstrapArchives` จะ:

1. ดาวน์โหลด release URL ที่กำหนดตายตัว
2. ตรวจ SHA-256 ของแต่ละ ABI
3. เก็บไฟล์ไว้ใน generated Android assets
4. ให้ Gradle ฝัง assets ลง APK
5. ให้ `BootstrapInstaller.installBundled()` เลือก archive ตาม `Build.SUPPORTED_ABIS`

การ build ต้องมีอินเทอร์เน็ตในครั้งแรก:

```sh
cd android-app
./gradlew assembleDebug
```

หาก archive ถูกดาวน์โหลดไม่ครบหรือ checksum ไม่ตรง build จะหยุดทันที ไม่สร้าง APK ที่มี runtime ไม่สมบูรณ์

## ความปลอดภัยและ license

Bootstrap archive มี executable และ library ที่แอปจะนำไปรันใน app-private runtime จึงต้องใช้ release และ checksum ที่เชื่อถือได้เท่านั้น

ต้องรักษา license และ notices ของ Termux packages และ dependencies เมื่อแจกจ่าย APK ระบบนี้ไม่สามารถให้สิทธิ์ Android system root และไม่สามารถเข้าถึง private data ของแอปอื่นได้

## Checklist สำหรับรายงานปัญหา

```text
Android version:
Device model:
CPU ABI:
MONNIXCODE app version:
Runtime status:
พื้นที่ว่างโดยประมาณ:
ข้อความ error:
logcat ที่เกี่ยวข้อง:
```

อย่าส่งรหัสผ่าน, token, private key หรือไฟล์ส่วนตัวไปพร้อมกับรายงานปัญหา

## สรุปแบบเร็ว

```text
1. ติดตั้ง APK รุ่นที่มี embedded Bootstrap
2. เปิดแอป
3. รอ auto-install ครั้งแรก
4. กด Runtime และตรวจว่าเป็น verified
5. เปิด Terminal
6. ทดสอบ pwd, ls และตัวแปร $SHELL
```

หากยังไม่เป็น `verified` แอปจะไม่อ้างว่า Linux shell พร้อมใช้งาน และจะไม่สร้าง binary ปลอมขึ้นมา

## แหล่งข้อมูล upstream

- [Termux app](https://github.com/termux/termux-app)
- [Termux packages](https://github.com/termux/termux-packages)
- [Termux maintainer documentation](https://github.com/termux/termux-packages/wiki/For-maintainers)
