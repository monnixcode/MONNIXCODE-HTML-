# Termux source integration

Repository นี้รวม MONNIXCODE HTML Terminal กับ Termux app source ที่แนบมาไว้ด้วยกัน โดยแยกส่วนอย่างชัดเจน:

```text
index.html       เว็บ Terminal แบบ standalone สำหรับ GitHub Pages
run.sh           สคริปต์เปิดเว็บบน Termux
termux-source/   Android/Termux source code ต้นฉบับที่แนบมา
```

## สิ่งที่ใช้งานได้ online

หน้า `index.html` เป็น static HTML จึงสามารถเผยแพร่ด้วย GitHub Pages ได้โดยไม่ต้องใช้ Gradle, Android SDK หรือ backend ส่วนคำสั่งในหน้าเว็บเป็น simulator และไม่สามารถเรียก shell จริงของ Android ได้

## สิ่งที่ใช้บน Termux จริง

โฟลเดอร์ `termux-source/` เป็น source code สำหรับ Android app ของ Termux และมีโครงสร้าง build ของ Android/Gradle อยู่ภายใน ใช้เป็น source อ้างอิงหรือพัฒนาต่อแยกจาก HTML ได้ แต่ไม่ได้ถูกนำมารันใน GitHub Pages

การ build Android source ต้องใช้ Android Studio/Android SDK/JDK/NDK และ dependencies ตามเอกสารของ source ภายใน `termux-source/README.md` ไม่ใช่คำสั่ง `./run.sh`

## ที่มาและลิขสิทธิ์

ไฟล์ใน `termux-source/` มาจาก archive `termux-app-master(1).zip` ที่ผู้ใช้นำเข้าในงานนี้ ควรรักษาไฟล์ license และ copyright notices เดิมไว้เมื่อนำไปเผยแพร่หรือดัดแปลงต่อ โปรดอ่าน:

- [`termux-source/LICENSE.md`](termux-source/LICENSE.md)
- [`termux-source/README.md`](termux-source/README.md)
- [`termux-source/SECURITY.md`](termux-source/SECURITY.md)

MONNIXCODE HTML และ Termux source เป็นส่วนแยกกัน: การเพิ่ม source ไว้ใน repository ไม่ได้หมายความว่า HTML simulator จะกลายเป็น shell หรือ Android app จริงโดยอัตโนมัติ
