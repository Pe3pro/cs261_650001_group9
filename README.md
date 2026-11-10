# CS261 Group 9 — ระบบยื่นคำร้องและจับคู่สถานที่ฝึกสหกิจศึกษา

โปรเจกต์นี้เป็นเว็บแอปพลิเคชันสำหรับนักศึกษาและเจ้าหน้าที่ของมหาวิทยาลัยธรรมศาสตร์ ช่วยให้นักศึกษาตรวจสอบคุณสมบัติก่อนฝึกสหกิจศึกษา ยื่นคำร้องออนไลน์ และจัดอันดับบริษัทที่สนใจได้ในที่เดียว ส่วนเจ้าหน้าที่สามารถสร้างแบบฟอร์มคำร้องและรับคำร้องของนักศึกษาผ่านระบบได้โดยตรง

Repository: https://github.com/Pe3pro/cs261_650001_group9  
รายวิชา CS261 Section 650001

---

## สารบัญ
1. [เทคโนโลยีที่ใช้](#เทคโนโลยีที่ใช้)
2. [โครงสร้างโปรเจกต์](#โครงสร้างโปรเจกต์)
3. [การเตรียมเครื่องก่อนเริ่มงาน](#การเตรียมเครื่องก่อนเริ่มงาน)
4. [การรันโปรเจกต์ด้วย Docker](#การรันโปรเจกต์ด้วย-docker)
5. [การรันระหว่างพัฒนาใน VS Code](#การรันระหว่างพัฒนาใน-vs-code)
6. [ฐานข้อมูล](#ฐานข้อมูล)
7. [API Specification](#api-specification)
8. [แนวทางการใช้ Git ของทีม](#แนวทางการใช้-git-ของทีม)
9. [User Stories ใน Sprint 1](#user-stories-ใน-sprint-1)
10. [ปัญหาที่พบบ่อยและวิธีแก้](#ปัญหาที่พบบ่อยและวิธีแก้)
11. [สมาชิกกลุ่ม](#สมาชิกกลุ่ม)

---

## เทคโนโลยีที่ใช้

| ส่วน | เทคโนโลยี |
|---|---|
| Frontend | HTML, CSS, JavaScript |
| Backend | Java 21, Spring Boot 4.1.1 (Spring Web MVC, Spring Data JPA) |
| Database | MySQL 8.4 |
| Container | Docker, Docker Compose |
| Build tool | Maven (ผ่าน Maven Wrapper `mvnw`) |

---

## โครงสร้างโปรเจกต์

```
cs261_650001_group9/
├── docker-compose.yml          # ไฟล์สำหรับรัน Web Server และ DB Server พร้อมกัน
├── .doc/UserStory/U1–U5/       # เอกสารออกแบบ เช่น API spec, API class diagram, DTO class
├── demo/                       # Backend (Spring Boot)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/demo/
│       │   ├── controller/     # REST API
│       │   ├── service/        # Business logic
│       │   ├── dto/            # ข้อมูลที่รับและส่งผ่าน API
│       │   ├── entity/         # คลาสที่แทนตารางในฐานข้อมูล
│       │   ├── dao/            # Repository สำหรับติดต่อฐานข้อมูล
│       │   └── DemoApplication.java
│       └── resources/application.properties
└── frontend/                   # Frontend
    ├── index.html              # หน้าแรก จะพาไปยังหน้า login ทันที
    ├── login.html  check.html  submit.html  ranking.html
    ├── css/style.css
    └── js/script.js  api.js  main.js
```

การทำงานของระบบเริ่มจากหน้าเว็บใน `frontend` ส่งคำขอไปที่ `controller` จากนั้น `controller` เรียก `service` เพื่อประมวลผล และ `service` ใช้ `dao` ในการอ่านหรือบันทึกข้อมูลลง MySQL โดยข้อมูลที่ส่งระหว่างชั้นจะอยู่ในรูปของ `dto` ส่วน `entity` ใช้แทนโครงสร้างตารางในฐานข้อมูล

---

## การเตรียมเครื่องก่อนเริ่มงาน

กรุณาติดตั้งโปรแกรมต่อไปนี้ให้เรียบร้อยก่อน และตรวจสอบได้ด้วยคำสั่งในคอลัมน์ขวา

| โปรแกรม | ใช้สำหรับ | คำสั่งตรวจสอบ |
|---|---|---|
| [Git](https://git-scm.com/) | ดึงและส่งโค้ดขึ้น GitHub | `git --version` |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | รัน Web Server และฐานข้อมูล | `docker --version` |
| [JDK 21](https://adoptium.net/) | รัน Spring Boot ใน VS Code (ไม่จำเป็นหากรันผ่าน Docker เพียงอย่างเดียว) | `java -version` |
| VS Code พร้อม Extension Pack for Java และ Live Server | เขียนโค้ดและเปิดหน้าเว็บ | — |

**หมายเหตุ:** ทุกครั้งก่อนใช้คำสั่ง `docker` ต้องเปิดโปรแกรม Docker Desktop และรอจนขึ้นสถานะ "Engine running" ก่อน

---

## การรันโปรเจกต์ด้วย Docker

### 1. Clone โปรเจกต์และเปลี่ยนไปที่ branch Develop
```powershell
git clone https://github.com/Pe3pro/cs261_650001_group9.git
cd cs261_650001_group9
git checkout Develop
```

### 2. เปิด Web Server และ DB Server
```powershell
docker compose up -d --build
```
การรันครั้งแรกอาจใช้เวลาประมาณ 10–20 นาที เนื่องจาก Docker ต้องดาวน์โหลด Maven, Java และ MySQL ครั้งต่อไปจะเร็วขึ้นมาก เมื่อเสร็จแล้วจะมี container ทำงานอยู่ 2 ตัว ดังนี้

| Container | หน้าที่ | Port |
|---|---|---|
| `cs261-db` | MySQL 8.4 | 3306 |
| `cs261-app` | Spring Boot | 8080 |

### 3. ตรวจสอบว่าระบบทำงานแล้ว
```powershell
docker ps
```
ในรายการควรมีทั้ง `cs261-db` (สถานะ healthy) และ `cs261-app` หากต้องการดู log ของ Spring Boot ให้ใช้คำสั่ง

```powershell
docker compose logs -f app
```
เมื่อเห็นข้อความ `Started DemoApplication` แสดงว่าระบบพร้อมใช้งาน กด `Ctrl+C` เพื่อออกจากหน้า log ได้

เมื่อเปิด http://localhost:8080 แล้วพบหน้า Whitelabel Error Page ถือว่าปกติ เพราะ server ทำงานแล้วแต่ยังไม่มีหน้าที่ path `/`

### 4. เปิดหน้าเว็บ
ใน VS Code ให้คลิกขวาที่ไฟล์ `frontend/index.html` แล้วเลือก Open with Live Server

### คำสั่ง Docker ที่ใช้บ่อย

| คำสั่ง | ใช้เมื่อ |
|---|---|
| `docker compose up -d` | เปิดระบบในเบื้องหลัง |
| `docker compose up -d --build` | เปิดระบบหลังจากแก้ไขโค้ด Java เพื่อให้ container ใช้โค้ดล่าสุด |
| `docker compose up -d db` | เปิดเฉพาะฐานข้อมูล ใช้คู่กับการรัน Spring Boot ใน VS Code |
| `docker compose down` | ปิดระบบ โดยข้อมูลในฐานข้อมูลยังอยู่ |
| `docker compose down -v` | ปิดระบบและลบข้อมูลในฐานข้อมูลทั้งหมด |
| `docker compose logs -f app` | ดู log ของ Spring Boot |
| `docker ps` | ดูรายการ container ที่กำลังทำงาน |

---

## การรันระหว่างพัฒนาใน VS Code

ระหว่างเขียนโค้ด Java แนะนำให้รัน Spring Boot ใน VS Code แทน เพราะรันใหม่ได้เร็วกว่าการ build Docker ทุกครั้ง โดยเปิดเฉพาะฐานข้อมูลไว้ใน Docker

```powershell
# 1. เปิดเฉพาะฐานข้อมูล
docker compose up -d db

# 2. ปิด container ของแอปก่อน (ถ้าเปิดอยู่) เพื่อไม่ให้ใช้ port 8080 ซ้ำกัน
docker compose stop app

# 3. รัน Spring Boot
cd demo
.\mvnw.cmd spring-boot:run
```
เมื่อต้องการหยุด ให้กด `Ctrl+C` ตอบ `Y` แล้วพิมพ์ `cd ..` เพื่อกลับไปที่โฟลเดอร์หลัก

**หมายเหตุ:** ไม่ควรกดปุ่ม Run ที่มุมขวาบนของ VS Code เพราะเป็นปุ่มของ extension Code Runner ซึ่งไม่ได้รันผ่าน Maven และจะเกิด error `cannot find symbol SpringApplication` ให้กดคำว่า Run ที่อยู่เหนือ `public static void main` ในไฟล์ `DemoApplication.java` หรือใช้คำสั่ง `mvnw` ข้างต้นแทน

---

## ฐานข้อมูล

| รายการ | เชื่อมต่อจากใน Docker | เชื่อมต่อจากเครื่องตัวเอง |
|---|---|---|
| Host | `db` | `localhost` |
| Port | `3306` | `3306` |
| Database | `cs261db` | `cs261db` |
| Username | `root` | `root` |
| Password | `root1234` | `root1234` |

ระบบตั้งค่าให้สร้างตารางโดยอัตโนมัติจาก Entity class (`spring.jpa.hibernate.ddl-auto=update`) จึงไม่จำเป็นต้องเขียนคำสั่ง `CREATE TABLE` เอง

หากต้องการเข้าไปดูข้อมูลในฐานข้อมูล ใช้คำสั่ง
```powershell
docker exec -it cs261-db mysql -uroot -proot1234 cs261db
```
จากนั้นใช้ `SHOW TABLES;` หรือ `SELECT * FROM <ชื่อตาราง>;` และพิมพ์ `exit` เพื่อออก

รหัสผ่านข้างต้นใช้สำหรับการพัฒนาภายในเครื่องเท่านั้น

---

## API Specification

รายละเอียดของแต่ละ User Story อยู่ในไฟล์ `.doc/UserStory/U*/T5.md`

| User Story | Method | Endpoint | หน้าที่ |
|---|---|---|---|
| U1 | `POST` | `/api/auth/login` | เข้าสู่ระบบสำหรับนักศึกษาและเจ้าหน้าที่ |
| U2–U5 | — | ดูใน `.doc/UserStory/U2–U4/T5.md` และ `.doc/UserStory/U5/T2.md` | — |

การทดสอบ API ใช้ Postman โดยให้เก็บไฟล์ Postman Collection ไว้ที่ `.doc/postman/`

---

## แนวทางการใช้ Git ของทีม

```
Feature-/-<ชื่องาน>  ──Pull Request──▶  Develop  ──Scrum Master merge──▶  main
```

| Branch | ใช้สำหรับ |
|---|---|
| `main` | โค้ดที่ผ่านการทดสอบแล้ว ใช้สำหรับส่งงานและ demo ห้าม push เข้า branch นี้โดยตรง |
| `Develop` | รวมงานของสมาชิกทุกคน |
| `Feature-/-<ชื่องาน>` | งานของแต่ละคน เช่น `Feature-/-Login`, `Feature-/-AutoEligibility`, `Feature-/-Intern-Request-Form` |

### ขั้นตอนการทำงาน
```powershell
# 1. อัปเดต Develop ให้เป็นเวอร์ชันล่าสุดก่อนเริ่มงาน
git checkout Develop
git pull origin Develop

# 2. สร้าง branch ของงานตัวเอง
git checkout -b Feature-/-<ชื่องาน>
# หากมี branch อยู่แล้ว ให้ใช้ git checkout Feature-/-<ชื่องาน> แล้วตามด้วย git merge Develop

# 3. เขียนโค้ดแล้ว commit
git add <ไฟล์ที่แก้ไข>
git commit -m "U1:T9 Add User entity and UserRepository"

# 4. push ขึ้น GitHub แล้วเปิด Pull Request เข้า Develop
git push origin Feature-/-<ชื่องาน>
```

### ข้อตกลงร่วมกัน
- ข้อความ commit ให้ขึ้นต้นด้วยรหัสงานใน Trello เช่น `U1:T8 ...` หรือ `U3:T9 ...` เพื่อให้ตรวจสอบย้อนหลังได้ง่าย
- Pull Request ทุกรายการต้องผ่านการ review จาก Scrum Master ก่อน merge
- ก่อน push ควรรัน `docker compose up -d --build` และตรวจสอบว่าแอปเปิดได้จริง
- ไม่ commit ไฟล์ที่ได้จากการ build เช่น `target/` และ `bin/` ซึ่งกำหนดไว้ใน `.gitignore` แล้ว
- หาก push แล้วขึ้นข้อความ `rejected (fetch first)` แสดงว่ามีคนส่งงานขึ้นไปก่อน ให้รัน `git pull --no-rebase origin <ชื่อ branch>` แล้ว push ใหม่อีกครั้ง

---

## User Stories ใน Sprint 1

| รหัส | User Story | หน้าเว็บที่เกี่ยวข้อง |
|---|---|---|
| U1 | การเข้าสู่ระบบ (นักศึกษา, เจ้าหน้าที่) | `login.html` |
| U2 | การตรวจสอบคุณสมบัติอัตโนมัติ | `check.html` |
| U3 | การสร้างและยื่นคำร้องออนไลน์ (นักศึกษา) | `submit.html` |
| U4 | การสร้างและยื่นคำร้องออนไลน์ (เจ้าหน้าที่) | — |
| U5 | การจับคู่บริษัท (จัดอันดับ) | `ranking.html` |

แต่ละ User Story แบ่งเป็น Task T4 ถึง T21 ตามบอร์ด Trello ตั้งแต่การกำหนดรายละเอียด การออกแบบ การพัฒนา การทดสอบ ไปจนถึงการ demo

---

## ปัญหาที่พบบ่อยและวิธีแก้

| อาการ | สาเหตุและวิธีแก้ |
|---|---|
| `failed to connect to the docker API ... dockerDesktopLinuxEngine` | ยังไม่ได้เปิด Docker Desktop ให้เปิดโปรแกรมและรอจนขึ้น "Engine running" |
| `ports are not available ... 8080` | มีโปรแกรมอื่นใช้ port 8080 อยู่ ส่วนใหญ่เป็น `mvnw spring-boot:run` ที่ยังไม่ได้ปิด ให้ปิด Terminal นั้น หรือใช้ `netstat -ano \| findstr :8080` แล้วตามด้วย `taskkill /PID <PID> /F` |
| `port is already allocated` ที่ port 3306 | เครื่องมี MySQL ติดตั้งอยู่แล้ว ให้เปลี่ยน `"3306:3306"` ใน `docker-compose.yml` เป็น `"3307:3306"` และแก้ port ใน `application.properties` ให้ตรงกัน |
| `Failed to configure a DataSource: 'url' attribute is not specified` | แก้ไข `application.properties` ผิดไฟล์ ไฟล์ที่ถูกต้องคือ `demo/src/main/resources/application.properties` (ไม่ใช่ไฟล์ใน `target/`) จากนั้นรัน `.\mvnw.cmd clean spring-boot:run` |
| `Communications link failure` | ฐานข้อมูลยังไม่ได้เปิด ให้รัน `docker compose up -d db` |
| `cannot find symbol SpringApplication` | รันด้วยปุ่มของ Code Runner ให้ใช้ `.\mvnw.cmd spring-boot:run` แทน |
| คำสั่งใน Terminal ซ้ำกันหลายรอบในบรรทัดเดียว | วางข้อความซ้ำ (กด `Ctrl+V` และคลิกขวาพร้อมกัน) ให้เลือกใช้วิธีวางเพียงวิธีเดียว |
| หน้าจอหยุดนิ่งหลังขึ้น `Started DemoApplication` | ไม่ได้ค้าง server กำลังทำงานรอรับคำขออยู่ หากต้องการหยุดให้กด `Ctrl+C` |

---

## สมาชิกกลุ่ม

| ลำดับ | ชื่อ-นามสกุล | รหัสนักศึกษา | บทบาท / หน้าที่หลัก |
|---|---|---|---|
| 1 | นาย สินทวี ชนะภัย | 6809650119 | |
| 2 | นาง สาวจณิตา นาทอง | 6809650366 | |
| 3 | นาย ปันฐ์พิสิษฐ์ จิระดำรง | 6809650564 | Scrum Master
| 4 | นาย ธีรภัทร ใจคำ | 6809650044 | |
| 5 | นาย ภีรพัฒน์ คงเนียม | 6809650085 | |
| 6 | นาย ศุภวิชญ์ จารึกธรรม | 6809650713 | |