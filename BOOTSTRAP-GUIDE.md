# คู่มือการติดตั้ง Termux-compatible Bootstrap สำหรับผู้เริ่มต้น

คู่มือนี้จัดทำสำหรับผู้ที่ไม่เคยใช้ Termux หรือ Linux มาก่อน และต้องการเปิดใช้งาน Linux shell จริงใน **MONNIXCODE Workspace Android**

## สรุปสั้น ๆ

MONNIXCODE ไม่สามารถสร้าง Linux shell ขึ้นมาเองได้ และจะไม่ติดตั้ง binary ปลอมให้ผู้ใช้ ดังนั้นต้องมีไฟล์ **Bootstrap archive** ที่เป็นไฟล์ `.tar.xz` ก่อน

Bootstrap archive คือชุดไฟล์พื้นฐานของ Linux user-space เช่น shell, library, directory และ package metadata ซึ่งแอปจะนำไปติดตั้งไว้ในพื้นที่ส่วนตัวของแอป

หลังติดตั้งสำเร็จ Terminal จะทำงานใกล้เคียง Termux ในระดับ user-space โดยไม่ต้อง:

- root โทรศัพท์
- ติดตั้ง Magisk
- ใช้คำสั่ง `su`
- ปลดล็อก bootloader
- แก้ไข `/system`

> คำว่า “root” ในคู่มือนี้หมายถึง **runtime root ภายในพื้นที่ของแอป** ไม่ใช่ Android system root

---

## 1. สิ่งที่ต้องเตรียม

เตรียมสิ่งต่อไปนี้ให้ครบ:

1. โทรศัพท์ Android 15 ขึ้นไป
2. MONNIXCODE Workspace รุ่นที่มีปุ่ม **Bootstrap**
3. Bootstrap archive นามสกุล `.tar.xz`
4. ค่า SHA-256 ของ archive จำนวน 64 ตัวอักษร
5. พื้นที่ว่างเพียงพอสำหรับ archive และไฟล์ที่แตกออกมา
6. archive ที่ตรงกับ CPU architecture ของโทรศัพท์

ไม่ควรดาวน์โหลด archive จากลิงก์สุ่มหรือไฟล์ที่ไม่ทราบแหล่งที่มา เพราะ archive สามารถบรรจุ executable ที่แอปนำไปรันได้

---

## 2. Bootstrap archive คืออะไร

ไฟล์ Bootstrap ที่ถูกต้องควรมีโครงสร้างลักษณะนี้เมื่อเปิดดูรายการไฟล์:

```text
usr/
├── bin/
│   └── sh
├── etc/
├── lib/
└── var/
```

ไฟล์สำคัญขั้นต่ำที่ installer ตรวจสอบคือ:

```text
usr/bin/sh
usr/etc/
```

archive จะต้องเป็นไฟล์ tar ที่บีบอัดด้วย XZ จึงมักลงท้ายด้วย:

```text
.tar.xz
```

ไฟล์ `.zip`, `.tar.gz`, `.apk` หรือไฟล์ติดตั้ง Termux ทั้งแอป ไม่ใช่ Bootstrap archive ที่หน้านี้ต้องการ

---

## 3. ตรวจสอบ CPU architecture ของโทรศัพท์

Bootstrap ต้องตรงกับ CPU architecture หากเลือกผิด shell อาจเริ่มไม่ได้หรือ binary รันไม่ได้

### วิธีที่ง่ายที่สุดใน Android

เปิด **Settings → About phone** แล้วค้นหาคำว่า:

- CPU
- Processor
- Architecture
- ABI

โทรศัพท์ Android รุ่นใหม่ส่วนใหญ่ใช้ `arm64-v8a` หรือเรียกสั้น ๆ ว่า `aarch64`

### หากมี Termux อยู่แล้ว

เปิด Termux แล้วรัน:

```sh
uname -m
```

ผลลัพธ์ที่พบบ่อย:

| ผลลัพธ์จาก `uname -m` | ABI ที่ควรเลือก |
|---|---|
| `aarch64` | `arm64-v8a` |
| `armv7l` หรือ `armv8l` | `armeabi-v7a` |
| `x86_64` | `x86_64` |
| `i686` หรือ `i386` | `x86` |

ถ้าไม่แน่ใจ ให้ใช้ข้อมูล ABI จากหน้าผู้ผลิตอุปกรณ์หรือ Android diagnostic app ที่เชื่อถือได้ อย่าเดาจากชื่อรุ่นเพียงอย่างเดียว

> MONNIXCODE ไม่ได้ทำให้ binary ของ architecture หนึ่งสามารถรันบนอีก architecture หนึ่งได้ การเลือก archive ต้องตรงกับอุปกรณ์จริง

---

## 4. รับ Bootstrap archive อย่างปลอดภัย

ผู้ใช้ต้องรับ archive จากแหล่งที่เชื่อถือได้ เช่น:

- release หรือ artifact ของโครงการที่สร้าง archive โดยตรง
- server ภายในองค์กรที่มีการควบคุมไฟล์
- archive ที่ผู้ดูแลระบบสร้างและแจกพร้อม checksum
- package/runtime build ที่มี license และ notice ครบถ้วน

ก่อนใช้งานควรมีข้อมูลคู่กันเสมอ:

```text
ชื่อไฟล์: bootstrap-arm64-v8a.tar.xz
SHA-256: 64-character hexadecimal digest
ABI: arm64-v8a
แหล่งที่มา: URL หรือ release identifier
```

อย่าเชื่อ checksum ที่อยู่ในโฟลเดอร์เดียวกับไฟล์โดยอัตโนมัติ หาก checksum และ archive มาจากแหล่งเดียวกันที่ไม่น่าเชื่อถือ ผู้โจมตีอาจแก้ทั้งสองไฟล์พร้อมกันได้

---

## 5. คำนวณ SHA-256 ของ archive

ค่า SHA-256 ใช้ตรวจว่าไฟล์ที่ดาวน์โหลดมาไม่ถูกเปลี่ยนแปลงระหว่างทาง

### บน Termux หรือ Linux

สมมติไฟล์อยู่ในโฟลเดอร์ Downloads:

```sh
sha256sum ~/storage/downloads/bootstrap-arm64-v8a.tar.xz
```

หากยังเข้าถึงโฟลเดอร์ Downloads ไม่ได้ ให้เรียก:

```sh
termux-setup-storage
```

จากนั้นกดยอมรับ permission แล้วลองคำสั่งเดิมอีกครั้ง

ผลลัพธ์จะมีลักษณะนี้:

```text
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef  bootstrap-arm64-v8a.tar.xz
```

คัดลอกเฉพาะชุดตัวอักษร 64 ตัวด้านหน้าไปใส่ในช่อง SHA-256 ของแอป

### บน Windows PowerShell

```powershell
Get-FileHash .\bootstrap-arm64-v8a.tar.xz -Algorithm SHA256
```

### บน macOS

```sh
shasum -a 256 bootstrap-arm64-v8a.tar.xz
```

### ตรวจความยาว

SHA-256 ต้องมี 64 ตัวอักษร เป็นเลข `0-9` หรือตัวอักษร `a-f` เท่านั้น ตัวพิมพ์ใหญ่หรือตัวพิมพ์เล็กใช้ได้

---

## 6. ตรวจรายการไฟล์ก่อนนำเข้า (ถ้าต้องการ)

หากมีเครื่องที่ติดตั้งคำสั่ง `tar` และ `xz` แล้ว สามารถดูรายการไฟล์โดยไม่ต้องแตกไฟล์ได้:

```sh
tar -tJf bootstrap-arm64-v8a.tar.xz | head -50
```

ควรเห็น path ที่ขึ้นต้นด้วย `usr/` และควรพบ:

```text
usr/bin/sh
usr/etc/
```

หลีกเลี่ยง archive ที่มี path แปลก ๆ เช่น:

```text
../../system/file
/etc/passwd
/absolute/path
```

installer ของ MONNIXCODE จะปฏิเสธ path ที่พยายามออกนอก runtime root แต่ผู้ใช้ก็ควรตรวจแหล่งที่มาของไฟล์ก่อนติดตั้งเสมอ

---

## 7. ติดตั้งผ่านแอป MONNIXCODE

### ขั้นตอนที่ 1: เปิดแอป

เปิด **MONNIXCODE Workspace** แล้วรอหน้า Native Workspace โหลดเสร็จ

หากแอปแสดงข้อความว่า Bootstrap ยังไม่พร้อม ถือเป็นพฤติกรรมปกติในครั้งแรก

### ขั้นตอนที่ 2: เปิดหน้า Bootstrap

กดปุ่ม:

```text
Bootstrap
```

จะเปิดหน้า:

```text
MONNIXCODE / BOOTSTRAP
```

### ขั้นตอนที่ 3: เลือก archive

กด:

```text
เลือกไฟล์ archive
```

เลือกไฟล์ `.tar.xz` ที่ดาวน์โหลดไว้

หากไม่เห็นไฟล์:

1. เปิดเมนูของ file picker
2. เลือก `Downloads` หรือโฟลเดอร์ที่บันทึกไฟล์
3. เปลี่ยนตัวกรองเป็นไฟล์ทั้งหมด หาก file picker มีตัวเลือกนี้
4. ตรวจว่าไฟล์ไม่ได้ลงท้ายด้วย `.tar.xz.zip` หรือชื่อซ้ำจากการดาวน์โหลด

### ขั้นตอนที่ 4: กรอก SHA-256

คัดลอก SHA-256 จำนวน 64 ตัวอักษรลงในช่อง:

```text
SHA-256 checksum (64 hex characters)
```

ตรวจให้แน่ใจว่าไม่มีช่องว่างหรือข้อความอื่นติดมาด้วย

### ขั้นตอนที่ 5: เริ่มติดตั้ง

กด:

```text
ติดตั้ง Bootstrap
```

แอปจะทำงานตามลำดับ:

1. **Verify** — คำนวณและเปรียบเทียบ SHA-256
2. **Extract** — แตกไฟล์ลง staging directory
3. **Validate** — ตรวจ required files และความปลอดภัยของ path
4. **Activate** — สลับ runtime ใหม่แบบ atomic
5. **Complete** — บันทึกสถานะ verified

อย่าปิดแอปหรือพยายามเปิด archive ด้วยแอปอื่นระหว่างขั้นตอนติดตั้ง

### ขั้นตอนที่ 6: กลับไปที่ Terminal

เมื่อเห็นข้อความลักษณะนี้:

```text
ติดตั้ง Bootstrap สำเร็จ
```

กลับไปหน้า Terminal แล้วลองพิมพ์:

```sh
pwd
```

หรือ:

```sh
ls
```

หาก native PTY เริ่มสำเร็จ จะเห็น prompt หรือ output จาก shell จริง ไม่ใช่ข้อความจำลองจาก UI

---

## 8. ตรวจสอบว่าเป็น shell จริงหรือไม่

คำสั่งพื้นฐานที่ใช้ตรวจสอบ:

```sh
printf '%s\n' "$MONNIXCODE_ROOT"
printf '%s\n' "$PREFIX"
printf '%s\n' "$HOME"
pwd
printf '%s\n' "$SHELL"
```

ค่าที่คาดหวัง:

- `MONNIXCODE_ROOT` ชี้ไปยัง `monnixcode-runtime`
- `PREFIX` ลงท้ายด้วย `/usr`
- `HOME` อยู่ภายใน runtime
- `SHELL` ชี้ไปยัง `usr/bin/sh`
- `pwd` เริ่มต้นที่ runtime home

ทดสอบ environment เพิ่มเติม:

```sh
printf 'shell=%s\n' "$SHELL"
printf 'term=%s\n' "$TERM"
printf 'path=%s\n' "$PATH"
```

> การมี shell จริงไม่ได้แปลว่าจะมีคำสั่งทุกตัว เช่น `git`, `python`, `node` หรือ package manager ต้องมีอยู่ใน archive หรือถูกติดตั้งใน runtime เพิ่มเติมก่อน

---

## 9. ถ้าไม่เคยใช้ Termux มาก่อน ควรเข้าใจอะไรบ้าง

### Shell คืออะไร

Shell คือโปรแกรมรับคำสั่ง เช่น `sh`, `bash` หรือ `zsh` แล้วส่งคำสั่งไปทำงานกับไฟล์และโปรแกรม

### Runtime คืออะไร

Runtime คือโฟลเดอร์ที่รวม shell, library, executable, home และ temporary directory ไว้ด้วยกัน ใน MONNIXCODE runtime อยู่ในพื้นที่ส่วนตัวของแอป

### Package คืออะไร

Package คือชุดไฟล์ของโปรแกรม เช่น `git` หรือ `python` พร้อม metadata และ dependency การมีเพียง `sh` ไม่ได้หมายความว่าจะมี package เหล่านี้ครบ

### PTY คืออะไร

PTY หรือ pseudo-terminal ทำให้แอปส่ง input และรับ output จาก shell แบบ interactive ได้ใกล้เคียง terminal จริง เช่น รองรับ prompt, สี, Enter และการ resize หน้าจอ

---

## 10. ปัญหาที่พบบ่อย

### SHA-256 mismatch

ข้อความนี้หมายถึง checksum ที่กรอกไม่ตรงกับไฟล์

ให้ทำดังนี้:

1. คำนวณ checksum ใหม่จากไฟล์เดิม
2. คัดลอกเฉพาะ digest 64 ตัวอักษร
3. ตรวจว่าเลือกไฟล์ถูกตัว
4. อย่าใช้ checksum ของ archive คนละ ABI
5. หากยังไม่ตรง ให้ดาวน์โหลดไฟล์ใหม่จากแหล่งที่เชื่อถือได้

อย่ากดข้ามการตรวจสอบด้วยการใส่ค่าแบบสุ่ม เพราะ installer ตั้งใจหยุดเมื่อ checksum ไม่ตรง

### Required bootstrap file is missing

archive ไม่มีไฟล์ที่จำเป็น เช่น:

```text
usr/bin/sh
```

ให้หา archive ที่เป็น runtime bootstrap จริง ไม่ใช่ source code, APK หรือไฟล์ package เดี่ยว

### Shell เริ่มไม่ได้หลังติดตั้ง

ตรวจสอบ:

- archive ตรงกับ ABI ของโทรศัพท์หรือไม่
- archive มี `usr/bin/sh` หรือไม่
- ไฟล์ shell มี executable mode หรือไม่
- พื้นที่ว่างเพียงพอหรือไม่
- archive ถูกสร้างสำหรับ Android/Termux-compatible environment หรือไม่

หากยังไม่สำเร็จ ให้ถอนข้อมูลแอปและติดตั้งใหม่เฉพาะเมื่อจำเป็น เพราะการล้าง app data จะลบ runtime ที่ติดตั้งไว้

### เลือกไฟล์ไม่ได้

ตรวจสอบว่า:

- ไฟล์ยังดาวน์โหลดไม่เสร็จหรือไม่
- ชื่อไฟล์ลงท้ายด้วย `.tar.xz` จริงหรือไม่
- Android file picker เปิดโฟลเดอร์ Downloads ถูกต้องหรือไม่
- มีพื้นที่ว่างในเครื่องหรือไม่

### เห็นสถานะ `layout-installed` แต่ shell ยังไม่ทำงาน

สถานะนี้เป็นเพียงโครงสร้างโฟลเดอร์เริ่มต้นของแอป ไม่ใช่ verified Bootstrap และไม่ใช่ Linux runtime เต็มรูปแบบ ต้องติดตั้ง archive จริงผ่านหน้า Bootstrap ก่อน

### คำสั่ง `git`, `python` หรือ `node` หาไม่พบ

เป็นเรื่องปกติหาก archive ยังไม่มีโปรแกรมเหล่านั้น ต้องใช้ package/runtime ที่มีโปรแกรมดังกล่าวและ dependency ครบ อย่านำ binary จาก Android คนละ ABI มาวางทับเอง

### แอปถูกหยุดเมื่ออยู่เบื้องหลัง

ผู้ผลิตบางรายมี battery optimization ที่เข้มงวด ให้ตรวจการตั้งค่า Battery ของแอปตามความเหมาะสม แต่แอปไม่สามารถข้ามนโยบายของ Android หรือ OEM ได้เอง

---

## 11. ความปลอดภัย

- ติดตั้ง archive จากแหล่งที่เชื่อถือได้เท่านั้น
- ตรวจ SHA-256 ก่อนติดตั้งทุกครั้ง
- เก็บ checksum จากช่องทางที่เชื่อถือได้และแยกจากไฟล์ archive หากทำได้
- อย่าใส่รหัสผ่าน, API key หรือ access token ในคำสั่งที่อาจถูกบันทึกไว้ใน screenshot/log
- อย่าใช้คำสั่งหรือ binary ที่ไม่ทราบที่มา
- สำรองข้อมูลสำคัญก่อนล้าง app data
- Bootstrap archive อาจรัน executable ในพื้นที่ของแอป จึงควรตรวจสอบ license และเนื้อหาก่อนติดตั้ง

---

## 12. Checklist ก่อนแจ้งปัญหา

กรุณาเก็บข้อมูลต่อไปนี้:

```text
Android version:
Device model:
CPU ABI:
MONNIXCODE app version:
Bootstrap filename:
Bootstrap ABI:
SHA-256:
Installer status:
ข้อความ error:
```

ห้ามส่งข้อมูลส่วนตัว, token หรือไฟล์ที่มีข้อมูลลับไปพร้อมกับรายงานปัญหา

---

## 13. สรุปแบบเร็ว

```text
1. เตรียม archive .tar.xz ที่ตรงกับ ABI
2. หา SHA-256 ของ archive
3. เปิด MONNIXCODE
4. กด Bootstrap
5. เลือก archive
6. กรอก SHA-256 64 ตัวอักษร
7. กด ติดตั้ง Bootstrap
8. รอ Verify → Extract → Validate → Activate
9. กลับ Terminal
10. ทดสอบด้วย pwd, ls และตรวจ $SHELL
```

หากยังไม่มี verified Bootstrap แอปจะไม่อ้างว่า Linux shell ใช้งานได้จริง และจะไม่สร้าง shell binary ปลอมขึ้นมา
