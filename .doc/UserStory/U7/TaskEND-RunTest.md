US7:TaskEND : Run Test (Integration Test + System Test + User Acceptance Test)
-
User story U7: เจ้าหน้าที่และอาจารย์ตรวจสอบข้อมูลและสถานะการฝึกงาน/สหกิจของนักศึกษา (ยื่นใบสมัคร --> จับคู่บริษัท --> ฝึกงาน --> เสร็จสิ้น) และนักศึกษาดูสถานะของตนเองได้

> เอกสารของ Task นี้ตามเลข Task ของ Sprint 2 ใน Trello (การ์ด Task5:US5,US6,US7,US8:Design Test case และ TaskEND Run Test) · ออกแบบจาก Task2 (API Spec 11 endpoint, Sequence Diagram, ER Diagram) และ [Task3 · Unit Test Cases](T3.md) · TC1–TC8 ดูใน [Task3 ส่วนที่ 1](T3.md)
>
> ข้อมูลตัวอย่าง: ใช้นักศึกษาตัวอย่างของ U7 แยกจาก U3 / U4 / U5 / U6 เพื่อไม่ให้ผลของ Story อื่นมากระทบ

ข้อมูลตัวอย่างของ U7 (ใช้ร่วมกันทุกส่วน)
-
บัญชีรหัสผ่าน `1234` · วันที่ทดสอบ = D

| บัญชี | บทบาท | อาจารย์ที่ปรึกษา | ข้อมูลตั้งต้น | stage ที่ควรได้ | attention ที่ควรได้ |
|---|---|---|---|---|---|
| `staff01` | เจ้าหน้าที่ (id 1) | – | – | – | – |
| `advisor01` | อาจารย์ (id 2) | – | ไม่ได้ดูแล S1–S7 (ดูแลนักศึกษาของ Story อื่น) | – | – |
| `advisor02` | อาจารย์ | – | ดูแล S1–S7 | – | – |
| `6609650021` (S1) | นักศึกษา | advisor02 | ไม่มีใบสมัคร | NOT_APPLIED | null |
| `6609650022` (S2) | นักศึกษา | advisor02 | ใบสมัคร SUBMITTED | APPLYING | null |
| `6609650023` (S3) | นักศึกษา | advisor02 | ใบสมัคร APPROVED เมื่อ D−5 ไม่มี match | WAITING_MATCH | null (NO_MATCH เมื่อเตรียมข้อมูลตามข้อ ก) |
| `6609650024` (S4) | นักศึกษา | advisor02 | match PROPOSED ในรอบที่ประกาศผลแล้ว `confirmDeadline` = D+14 | MATCHING | null (NOT_CONFIRMED เมื่อเตรียมข้อมูลตามข้อ ข) |
| `6609650025` (S5) | นักศึกษา | advisor02 | match CONFIRMED + internship NOT_STARTED ข้อมูลครบ (ผู้ดูแล "U7 Supervisor") | READY | null |
| `6609650026` (S6) | นักศึกษา | advisor02 | internship IN_PROGRESS | IN_PROGRESS | null |
| `6609650027` (S7) | นักศึกษา | advisor02 | internship COMPLETED | COMPLETED | null |

- ข้อมูลชุดนี้สร้างโดย `U7DemoSeeder` (Task4) · S4–S7 จับคู่กับตำแหน่งจาก `MatchingDemoSeeder` (บริษัทของแต่ละคนดูได้จากหน้ารายละเอียด) · ต้องเปิด `APP_SEED_U7_DATA: "true"` ใน `docker-compose.yml` ก่อน (ค่าเริ่มต้นปิดไว้ เพื่อไม่ให้กระทบ U5/U6)
- ป้ายเตือน NO_MATCH / NOT_CONFIRMED ไม่เกิดจาก seeder (วันที่ยังไม่เกินกำหนด) ก่อนทดสอบป้ายเตือนให้เตรียมข้อมูลด้วย SQL:
  - ก. S3 อนุมัติเกิน 14 วัน: `UPDATE applications SET updated_at = NOW() - INTERVAL 15 DAY WHERE student_id = <S3>`
  - ข. รอบของ S4 เลยกำหนดยืนยัน: `UPDATE matching_rounds SET confirm_deadline = NOW() - INTERVAL 1 DAY WHERE id = <รอบของ S4>`
- Integration Test API–DAO ใช้ชุดเดียวกันแต่สร้างเองใน test (ฐานข้อมูลว่าง) จึงนับจำนวนได้แน่นอน · System Test / UAT ใช้ระบบจริงที่มีนักศึกษาของ Story อื่นอยู่ด้วย จึงค้นด้วย `q=660965002` หรือตรวจด้วย SQL แทนการนับทั้งตาราง


# ส่วนที่ 1 · Design Integration Test UI–API

ขอบเขต
-
- หน้า `student-status.html` (เจ้าหน้าที่ / อาจารย์) และ `my-status.html` (นักศึกษา) + `js/u7.js` เรียก backend จริง (ฐานข้อมูลใหม่ + seeder ของ U7)
- วิธีตรวจ: DevTools แท็บ Network ดู method, URL, header `X-User-Id`, status และ response แล้วดูผลบนหน้าจอ

Design Integration Test ระหว่าง UI กับ API
-
| TC ID | Scenario | Input (บนหน้าเว็บ) | Request ที่ต้องเห็น | Response | Expected บนหน้าเว็บ |
|---|---|---|---|---|---|
| IT-UA-01 | รายการนักศึกษา (TC1) | `staff01` เปิด `student-status.html` | GET /api/students/status?page=0 แล้ว GET /api/students/status/summary (`X-User-Id: 1`) | 200 `PageResponse` · 200 Map 9 stage | ตารางแสดงรหัส ชื่อ อาจารย์ บริษัท ตำแหน่ง ป้ายขั้น วันที่อัปเดต · S1–S7 อยู่ในรายการ |
| IT-UA-02 | ตัวเลขแต่ละขั้น (TC8) | ดูแท็บ stage | GET /api/students/status/summary | 200 | ตัวเลขบนแท็บตรงกับ response · ขั้นที่เป็น 0 ไม่มีตัวเลข |
| IT-UA-03 | กรองขั้น + ค้นหา (TC1) | กดแท็บ "กำลังจับคู่" แล้วพิมพ์ "6609650024" | GET /api/students/status?stage=MATCHING&q=6609650024&page=0 | 200 1 แถว | เหลือแถว S4 แถวเดียว · กลับไปหน้า 1 |
| IT-UA-04 | กรองบริษัท (TC1) | เลือกบริษัทของ S6 | GET /api/students/status?companyId={บริษัทของ S6}&page=0 | 200 | เห็น S6 และไม่เห็นนักศึกษาที่จับคู่บริษัทอื่น |
| IT-UA-05 | แบ่งหน้า (TC1) | มีนักศึกษาเกิน 20 คน กด "ถัดไป" | GET ...&page=1 | 200 `page: 1` | แสดง "หน้า 2 จาก N" · หน้าสุดท้ายปุ่ม "ถัดไป" กดไม่ได้ |
| IT-UA-06 | ป้ายเตือน (TC3) | เตรียมข้อมูลข้อ ก, ข แล้วโหลดหน้าใหม่ ดูแถว S3, S4 | GET /api/students/status?page=0 | `attention: NO_MATCH`, `NOT_CONFIRMED` | แถว S3, S4 ไฮไลต์ มีป้าย "ยังไม่ได้จับคู่เกิน 14 วัน" / "เลยกำหนดยืนยันสิทธิ์แล้ว" · แถวอื่นไม่มีป้าย |
| IT-UA-07 | อาจารย์เห็นเฉพาะของตน (TC2) | `advisor02` เปิดหน้า แล้วเปลี่ยนเป็น `advisor01` | GET /api/students/status?page=0 (`X-User-Id` ของแต่ละคน) | 200 | advisor02 เห็น S1–S7 · advisor01 ไม่เห็น S1–S7 · ทั้งคู่ไม่มีตัวกรองอาจารย์ ไม่มีปุ่มแก้ไข / เปลี่ยนสถานะ |
| IT-UA-08 | ดาวน์โหลด Excel (TC8) | แท็บ "กำลังฝึกงาน" กด "ดาวน์โหลด Excel" | GET /api/students/status/export?stage=IN_PROGRESS | 200 `text/csv` | ได้ไฟล์ `.csv` เปิดใน Excel ภาษาไทยอ่านได้ มีแถว S6 |
| IT-UA-09 | หน้ารายละเอียด + timeline (TC4) | คลิกชื่อ S6 | GET /api/students/{S6}/status | 200 `StudentStatusDetailResponse` | เห็นข้อมูลนักศึกษา ใบสมัคร อันดับที่เลือก ผลจับคู่ (บริษัทที่จับคู่) ข้อมูลฝึกงาน และ timeline เรียงเก่า → ใหม่ พร้อมชื่อผู้เปลี่ยน |
| IT-UA-10 | นักศึกษายังไม่ยื่น (TC4) | คลิกชื่อ S1 | GET /api/students/{S1}/status | 200 stage NOT_APPLIED | แสดง "ยังไม่ยื่นใบสมัคร" · ไม่มีส่วนผลจับคู่และการฝึกงาน · ไม่มี error |
| IT-UA-11 | แก้ข้อมูลการฝึกงาน (TC6) | เปิด S5 แก้วันเริ่ม-สิ้นสุด ผู้ดูแล แล้วกด "บันทึก" | PUT /api/internships/{id} body ครบ 5 field | 200 `InternshipResponse` | "บันทึกข้อมูลการฝึกงานแล้ว" · ค่าใหม่บนหน้า |
| IT-UA-12 | วันที่ผิด หลุดไปถึง server (TC6) | ยิง Postman PUT endDate < startDate (หน้าเว็บกันไว้แล้ว) | PUT /api/internships/{id} | 400 `INVALID_DATE_RANGE` | ข้อมูลบนหน้าไม่เปลี่ยน |
| IT-UA-13 | เริ่มฝึกงาน (TC7) | S5 กด "เริ่มฝึกงาน" 2 ครั้ง (ครั้งแรกขอยืนยัน) | POST /api/internships/{id}/start 1 ครั้ง | 200 IN_PROGRESS | ป้ายเป็น "กำลังฝึกงาน" · timeline เพิ่ม 1 รายการ |
| IT-UA-14 | เริ่มฝึกงานแต่ข้อมูลไม่ครบ (TC7) | ลบชื่อผู้ดูแลของ internship ที่ NOT_STARTED แล้วกด "เริ่มฝึกงาน" | POST .../start | 400 `MISSING_INFO` | แสดงข้อความจาก server · สถานะไม่เปลี่ยน |
| IT-UA-15 | ยุติพร้อมเหตุผล (TC7) | S6 กด "ยุติการฝึกงาน" ไม่กรอกเหตุผล แล้วกรอก "บริษัทยกเลิกโครงการ" | ครั้งแรกไม่มี request · ครั้งที่สอง POST .../terminate `{reason}` | 200 TERMINATED | ครั้งแรกแจ้ง "กรุณากรอกเหตุผล" · ครั้งที่สองป้ายเป็น "ยุติการฝึกงาน" และช่องแก้ไขถูกล็อก |
| IT-UA-16 | อาจารย์ขอนักศึกษาคนอื่น (TC2) | `advisor01` พิมพ์ URL รายละเอียดของ S6 | GET /api/students/{S6}/status | 403 `FORBIDDEN` | "คุณไม่มีสิทธิ์ดูข้อมูลนักศึกษาคนนี้" ไม่แสดงข้อมูล |
| IT-UA-17 | สถานะของฉัน (TC5) | `6609650025` (S5) เปิด `my-status.html` | GET /api/students/me/status | 200 stage READY | แถบขั้นแสดง "พร้อมฝึกงาน" เป็นขั้นปัจจุบัน · เห็นบริษัทที่จับคู่ วันเริ่ม และผู้ดูแล · ไม่มีปุ่มแก้ไข |
| IT-UA-18 | นักศึกษาเปิดหน้าเจ้าหน้าที่ (TC2) | `6609650025` พิมพ์ URL `student-status.html` | ไม่มี request ไป /api/students/status (ถ้ายิงด้วย Postman ได้ 403) | – / 403 | หน้าไม่มีสิทธิ์ |
| IT-UA-19 | server ไม่ทำงาน | ปิด backend แล้วเปิดหน้า | network error | – | "ไม่สามารถเชื่อมต่อระบบได้" ไม่ค้าง |

ความครอบคลุมกับ test case ของ U7
-
| เคส | TC ID |
|---|---|
| TC1 รายการนักศึกษาพร้อมขั้นล่าสุด · กรองขั้น บริษัท ค้นหา แบ่งหน้า | IT-UA-01, IT-UA-03, IT-UA-04, IT-UA-05 |
| TC2 สิทธิ์: อาจารย์เห็นเฉพาะของตน · นักศึกษาดูได้เฉพาะของตนเอง | IT-UA-07, IT-UA-16, IT-UA-18 |
| TC3 stage และ attention คำนวณถูกต้อง | IT-UA-06 |
| TC4 หน้ารายละเอียดและ timeline | IT-UA-09, IT-UA-10 |
| TC5 นักศึกษาดูสถานะของตนเอง | IT-UA-17 |
| TC6 แก้ข้อมูลการฝึกงาน · ข้อมูลผิดถูกปฏิเสธ | IT-UA-11, IT-UA-12 |
| TC7 เปลี่ยนสถานะการฝึกงาน / ยุติพร้อมเหตุผล | IT-UA-13, IT-UA-14, IT-UA-15 |
| TC8 ตัวเลขแต่ละขั้น และดาวน์โหลด Excel | IT-UA-02, IT-UA-08 |


# ส่วนที่ 2 · Design Integration Test API–DAO

ขอบเขต
-
- ทดสอบ Controller → Service → DAO → ฐานข้อมูล โดยไม่ใช้หน้าเว็บ ส่ง request ด้วย Postman หรือ MockMvc (`@SpringBootTest`) แล้วตรวจข้อมูลผ่าน Repository / SQL
- ฐานข้อมูลทดสอบ: H2 หรือ MySQL ทดสอบ ล้างข้อมูลก่อนทุกข้อ แล้วสร้างข้อมูลตัวอย่างของ U7 (S1–S7) · `Clock` เป็น Bean กำหนดวันที่ D ได้ (ใช้ทดสอบ attention)
- ตารางใหม่ของ U7 ตาม ER Diagram ใน Task2: `internships` (match_id UK, student_id, start_date, end_date, supervisor_name/email/phone, status, terminated_reason, updated_by, updated_at) · `status_history` (student_id, type, ref_id, from_status, to_status, changed_by, note, changed_at)
- ตารางเดิมใช้ชื่อตามโค้ด: `users`, `students`, `applications`, `matches` (U6), `company_positions`, `companies`, `notifications` (ER เขียนเป็น application, position, company, notification)

![ER Diagram](<us7-10-er-diagram.png>)

Design Integration Test ระหว่าง API กับ DAO
-
| TC ID | Scenario | Request | DAO ที่ถูกเรียก | Expected response | Expected ในฐานข้อมูล |
|---|---|---|---|---|---|
| IT-AD-01 | รายการทั้งหมด (TC1) | GET /api/students/status?page=0&size=20 (staff01) | StudentStatusQueryDao.search | 200 `totalElements: 7` · S1 มีแถวแม้ไม่มีใบสมัคร · companyName ของ S4–S7 ตรงกับ match | ข้อมูลไม่เปลี่ยน |
| IT-AD-02 | กรอง + ค้นหา (TC1) | `?stage=MATCHING` · `?q=6609650026` · `?companyId={บริษัทของ S6}` | StudentStatusQueryDao.search | 200 เฉพาะ S4 · เฉพาะ S6 · มี S6 | ข้อมูลไม่เปลี่ยน |
| IT-AD-03 | แบ่งหน้า (TC1) | `?size=3&page=2` | StudentStatusQueryDao.search | 200 1 แถว `totalElements: 7` | – |
| IT-AD-04 | อาจารย์ถูกบังคับเป็นของตน (TC2) | advisor01 ส่ง `?advisorId={advisor02}` | StudentStatusQueryDao.search (advisorId = advisor01) | 200 ไม่มี S1–S7 (เห็นเฉพาะนักศึกษาของ advisor01) | – |
| IT-AD-05 | นักศึกษาเรียกรายการ (TC2) | GET /api/students/status (S1) · ไม่ส่ง `X-User-Id` | ไม่ถูกเรียก | 403 · 401 | – |
| IT-AD-06 | stage + attention ครบทุกขั้น (TC3) | เตรียมข้อมูลข้อ ก, ข แล้ว GET /api/students/status (Clock = D) | StudentStatusQueryDao.search, InternshipDao, MatchDao | 200 stage ของ S1–S7 ตรงกับตาราง "ข้อมูลตัวอย่างของ U7" · S3 NO_MATCH · S4 NOT_CONFIRMED | – |
| IT-AD-07 | ขอบเขตพอดี (TC3) | ตั้ง APPROVED ของ S3 = D−14 · `confirmDeadline` ของรอบ S4 = D+1 · = เวลาเดียวกับ Clock พอดี | เหมือน IT-AD-06 + MatchingRoundRepository | S3, S4 `attention: null` (ต้องเกิน 14 วัน / ต้องเลย confirmDeadline ก่อนจึงเตือน · ข้อตกลง S9 ของ U6) | – |
| IT-AD-08 | ตัวเลขแต่ละขั้น (TC8) | GET /api/students/status/summary (staff01 · advisor02) | StudentStatusQueryDao.countByStage | 200 มีครบ 9 key · advisor02: ทุกขั้นที่ S1–S7 อยู่ = 1 (NOT_APPLIED … COMPLETED) | ตรงกับจำนวนใน IT-AD-06 |
| IT-AD-09 | export CSV (TC8) | GET /api/students/status/export?stage=IN_PROGRESS · ไม่ส่ง stage | StudentStatusQueryDao.findAllForExport | 200 `text/csv` ขึ้นต้น UTF-8 BOM | หัวตาราง + 1 แถว (S6) · หัวตาราง + 7 แถว |
| IT-AD-10 | รายละเอียด + timeline (TC4) | GET /api/students/{S6}/status · GET /api/students/{S6}/timeline | ApplicationDao.findLatestByStudentId, MatchDao.findActiveWithPositionByApplicationId, InternshipDao.findByStudentId, StatusHistoryDao.findByStudentIdOrderByChangedAt, UserDao.findAllById | 200 ครบทุกส่วน · timeline เรียง changedAt เก่า → ใหม่ มีชื่อผู้เปลี่ยน | – |
| IT-AD-11 | ใบสมัครล่าสุด (TC4) | นักศึกษามีใบสมัคร 2 ใบ (เก่า REJECTED ใหม่ SUBMITTED) แล้ว GET detail | ApplicationDao.findLatestByStudentId | 200 application = ใบ SUBMITTED · stage APPLYING | – |
| IT-AD-12 | ของฉัน (TC5) | GET /api/students/me/status (S5) | เหมือน IT-AD-10 ด้วย id ของผู้ login | 200 ข้อมูลของ S5 เท่านั้น | – |
| IT-AD-13 | match ยืนยันแล้วสร้าง internship (TC7 · ต่อจาก U6) | S4 เรียก POST /api/matches/{id}/respond `{accept: true}` | MatchingService.respond → ส่ง `MatchConfirmedEvent` → InternshipService.onMatchConfirmed → createFromMatch → InternshipDao.save, StatusHistoryDao.save (ข้อตกลง S8 ของ U6) | 200 | `matches.status` = CONFIRMED · `internships` +1 แถว NOT_STARTED ผูก match_id · `status_history` +1 (type INTERNSHIP, ref_id = id ของ internship, from_status null → to_status NOT_STARTED) · stage ของ S4 = READY |
| IT-AD-14 | แก้ข้อมูลการฝึกงาน (TC6) | PUT /api/internships/{S5} body ครบ | InternshipDao.findById, save · NotificationRepository.save | 200 | ค่าใหม่ครบ 5 field · status ยัง NOT_STARTED · updated_by = 1 · `status_history` ไม่เพิ่ม · `notifications` type INTERNSHIP_UPDATED +1 S5 +1 advisor02 (ตาม Task3 SV-I07 · Sequence ③ ไม่ได้วาดขั้นนี้) |
| IT-AD-15 | ข้อมูลผิด / ปิดแล้ว (TC6) | PUT endDate < startDate · PUT อีเมลผิดรูปแบบ · PUT ของ S7 (COMPLETED) | InternshipDao.findById (ข้อสุดท้าย) | 400 INVALID_DATE_RANGE · 400 VALIDATION_ERROR · 409 INTERNSHIP_CLOSED | แถวไม่เปลี่ยน |
| IT-AD-16 | เปลี่ยนสถานะครบเส้นทาง (TC7) | S5: POST start → POST complete | InternshipDao.save, StatusHistoryDao.save, NotificationRepository.save | 200 IN_PROGRESS · 200 COMPLETED | `internships.updated_by` = 1 · `status_history` +2 แถว type INTERNSHIP (from_status → to_status: NOT_STARTED → IN_PROGRESS, IN_PROGRESS → COMPLETED) changed_by = 1 · `notifications` type INTERNSHIP_STATUS_CHANGED +2 ต่อคน (นักศึกษา + อาจารย์ที่ปรึกษา) |
| IT-AD-17 | ยุติ (TC7) | S6: POST terminate `{reason: "บริษัทยกเลิกโครงการ"}` · reason ว่าง | เหมือน IT-AD-16 | 200 TERMINATED · 400 VALIDATION_ERROR | terminated_reason ตรง · history +1 note = เหตุผล · ข้อที่ 400 ไม่มีแถวเพิ่ม |
| IT-AD-18 | เปลี่ยนผิดลำดับ / ข้อมูลไม่ครบ (TC7) | complete ของ NOT_STARTED · start ของ IN_PROGRESS · start เมื่อไม่มีผู้ดูแล | InternshipDao.findById | 409 INVALID_TRANSITION · 409 · 400 MISSING_INFO | status, history, notifications ไม่เปลี่ยน |
| IT-AD-19 | ผู้ไม่มีสิทธิ์แก้ (TC2) | advisor02 / S5 เรียก PUT และ POST start | ไม่ถูกเรียก save | 403 · 403 | ไม่มีแถวเปลี่ยน |
| IT-AD-20 | ฐานข้อมูลขัดข้องระหว่างบันทึก | ทำให้ StatusHistoryDao.save โยน error ตอน POST start | `@Transactional` | 500 | internship ยัง NOT_STARTED ไม่มี notification ค้าง (rollback) |
| IT-AD-21 | สร้าง internship ไม่สำเร็จ → ยืนยันสิทธิ์ rollback (TC7 · ข้อตกลง S8) | ทำให้ `createFromMatch` โยน error แล้ว S4 เรียก POST /api/matches/{id}/respond `{accept: true}` | MatchingService.respond + InternshipService.onMatchConfirmed (transaction เดียวกัน) | 500 | `matches.status` ยัง PROPOSED · `internships` ไม่เพิ่ม · `status_history` และ notifications ไม่เพิ่ม |
| IT-AD-22 | ยืนยันซ้ำไม่สร้าง internship ซ้ำ (TC7) | เรียก `createFromMatch` ด้วย match เดิมอีกครั้ง (เช่น event ส่งซ้ำ) | InternshipDao.findByMatchId | – | `internships` ของ match นี้มี 1 แถว (match_id UK) |

ความครอบคลุมกับ test case ของ U7
-
| เคส | TC ID |
|---|---|
| TC1 | IT-AD-01, IT-AD-02, IT-AD-03 |
| TC2 | IT-AD-04, IT-AD-05, IT-AD-19 |
| TC3 | IT-AD-06, IT-AD-07 |
| TC4 | IT-AD-10, IT-AD-11 |
| TC5 | IT-AD-12 |
| TC6 | IT-AD-14, IT-AD-15 |
| TC7 | IT-AD-13, IT-AD-16, IT-AD-17, IT-AD-18, IT-AD-20, IT-AD-21, IT-AD-22 |
| TC8 | IT-AD-08, IT-AD-09 |


# ส่วนที่ 3 · Design System Test

Sequence Diagram ที่ใช้
-
Sequence Diagram 3 รูปจาก Task2

| เส้นทาง | Sequence Diagram | จุดที่ใช้ตรวจ | ใช้กับ |
|---|---|---|---|
| ① | ดูรายการสถานะนักศึกษา | ขั้น 8–9 alt STUDENT → 403 · ขั้น 10 ADVISOR บังคับ advisorId · ขั้น 11 OFFICER ใช้ตัวกรอง · ขั้น 14–17 SELECT + COUNT · ขั้น 19 computeStage / computeAttention | TC1, TC2, TC3, TC8 |
| ② | ดูรายละเอียดและประวัติสถานะ | ขั้น 8–10 checkAccess → 403 · ขั้น 11–14 ใบสมัครล่าสุด · ขั้น 15–18 match PROPOSED / CONFIRMED · ขั้น 19–22 internship · ขั้น 23–26 status_history เรียง changed_at | TC2, TC4, TC5 |
| ③ | บันทึกข้อมูลและเปลี่ยนสถานะการฝึกงาน | ขั้น 8 INVALID_DATE_RANGE · ขั้น 9 INTERNSHIP_CLOSED · ขั้น 10–12 UPDATE · ขั้น 20 INVALID_TRANSITION · ขั้น 21 MISSING_INFO · ขั้น 22–28 UPDATE status + INSERT status_history + แจ้งนักศึกษาและอาจารย์ · complete / terminate ลำดับเดียวกัน (terminate ต้องมี reason) | TC6, TC7 |

![① Sequence Diagram ดูรายการสถานะนักศึกษา](<us7-07-seq-status-list.png>)

![② Sequence Diagram ดูรายละเอียดและประวัติสถานะ](<us7-08-seq-status-detail.png>)

![③ Sequence Diagram บันทึกข้อมูลและเปลี่ยนสถานะการฝึกงาน](<us7-09-seq-update-internship.png>)

ทดสอบทั้งระบบตามเส้นทางใน Sequence Diagram: หน้าเว็บจริง + backend จริง + MySQL (`docker compose up -d --build` + Live Server) แล้วตรวจ 3 จุด คือ หน้าจอ, DevTools แท็บ Network และข้อมูลใน MySQL

เตรียมก่อนทดสอบ
-
1. ใน `docker-compose.yml` เปลี่ยน `APP_SEED_U7_DATA: "false"` เป็น `"true"` แล้ว `docker compose down -v` และ `docker compose up -d --build` (ได้ข้อมูลตั้งต้นจาก `DemoDataSeeder` + `MatchingDemoSeeder` + `U7DemoSeeder`) · เทสเสร็จเปลี่ยนกลับเป็น `"false"`
2. บัญชี รหัสผ่าน `1234`: `staff01` · `advisor01` · `advisor02` · `6609650021`–`6609650027` (S1–S7)
3. ดูข้อมูลใน MySQL: `docker exec -it cs261-db mysql -uroot -proot1234 cs261db`

Test cases
-
| TC ID | เคส | เส้นทางใน Sequence Diagram | Input / ขั้นตอน | Expected result (หน้าจอ / API / ฐานข้อมูล) |
|---|---|---|---|---|
| ST-U7-01 | TC1 | ① รายการ | `staff01` → สถานะนักศึกษา → พิมพ์ "660965002" ในช่องค้นหา | `GET /api/students/status?q=660965002&page=0` 200 · ตาราง 7 แถว (S1–S7) ป้ายขั้นตรงกับตารางข้อมูลตัวอย่าง |
| ST-U7-02 | TC1 | ① กรองขั้น + บริษัท + แบ่งหน้า | กดแท็บ "กำลังฝึกงาน" · เลือกบริษัทของ S6 · ล้างตัวกรองแล้วกด "ถัดไป" | request มี `stage`, `companyId`, `page` ตามที่เลือก · ผลตรงกับ `SELECT` ที่ JOIN `students`, `applications`, `matches`, `internships` |
| ST-U7-03 | TC3 | ① stage + attention | เตรียมข้อมูลข้อ ก, ข · แล้ว `UPDATE internships SET start_date = CURDATE() - INTERVAL 1 DAY WHERE student_id = <S5>` โหลดหน้าใหม่ | S3 ป้าย NO_MATCH · S4 ป้าย NOT_CONFIRMED · S5 ป้าย START_OVERDUE (เลยวันเริ่มฝึกงาน) |
| ST-U7-04 | TC8 | ① summary + export | ดูตัวเลขบนแท็บ → "ดาวน์โหลด Excel" | `GET /summary` 200 ตัวเลขตรงกับจำนวนในตาราง · ไฟล์ CSV เปิดใน Excel ภาษาไทยอ่านได้ จำนวนแถวตรงกับแท็บที่เลือก |
| ST-U7-05 | TC2 | ① advisor | ออกจากระบบ → `advisor02` → สถานะนักศึกษา · แล้วเข้าด้วย `advisor01` | advisor02 เห็น S1–S7 ไม่มีปุ่มแก้ไข / เปลี่ยนสถานะ ตัวเลขบนแท็บนับเฉพาะนักศึกษาของตน · advisor01 ไม่เห็น S1–S7 |
| ST-U7-06 | TC2 | ② alt 403 | `advisor01` พิมพ์ URL รายละเอียดของ S6 · Postman `GET /api/students/{S6}/status` ด้วย id ของ advisor01 | หน้าเว็บแจ้งไม่มีสิทธิ์ · API 403 body ไม่มีชื่อหรือรหัสนักศึกษา |
| ST-U7-07 | TC2 | ไม่มีสิทธิ์ / ไม่ login | `6609650025` พิมพ์ URL `student-status.html` · Postman `GET /api/students/status` ไม่ส่ง `X-User-Id` | หน้าไม่มีสิทธิ์ · API 401 |
| ST-U7-08 | TC4 | ② รายละเอียด + timeline | `staff01` คลิกชื่อ S6 | `GET /api/students/{S6}/status` 200 · เห็นใบสมัคร อันดับที่เลือก บริษัทที่จับคู่ ข้อมูลฝึกงาน · timeline เรียงเก่า → ใหม่ ตรงกับ `SELECT * FROM status_history WHERE student_id = <S6> ORDER BY changed_at` |
| ST-U7-09 | TC5 | ② สถานะของฉัน | `6609650025` (S5) → สถานะของฉัน | `GET /api/students/me/status` 200 · แถบขั้น "พร้อมฝึกงาน" · เห็นบริษัทที่จับคู่ วันเริ่ม ผู้ดูแล · ไม่มีปุ่มแก้ไข |
| ST-U7-10 | TC7 | U6 ยืนยันสิทธิ์ → ③ สร้าง internship | `6609650024` (S4) → ผลการจับคู่ → ยืนยันสิทธิ์ → เปิดสถานะของฉัน | `POST /api/matches/{id}/respond` 200 · `matches.status` = CONFIRMED · `internships` +1 แถว NOT_STARTED (สร้างจาก `MatchConfirmedEvent` ในคำขอเดียวกัน ไม่ต้องกดอะไรเพิ่ม) · stage ของ S4 เปลี่ยนเป็น READY · ป้าย MISSING_INFO (ยังไม่มีวันที่และผู้ดูแล) |
| ST-U7-11 | TC6 | ③ แก้ข้อมูล | `staff01` → รายละเอียด S4 → กรอกวันเริ่ม-สิ้นสุด ผู้ดูแล ชื่อ อีเมล เบอร์ → บันทึก | `PUT /api/internships/{id}` 200 · ค่าใหม่ใน `internships` · ป้าย MISSING_INFO หายไป · S4 และ advisor02 ได้แจ้งเตือน |
| ST-U7-12 | TC6 | ③ alt 400 / 409 | กรอกวันสิ้นสุดก่อนวันเริ่ม · Postman `PUT` ของ S7 (COMPLETED) | หน้าเว็บแจ้ง "วันสิ้นสุดต้องหลังวันเริ่ม" ไม่มี request · API 409 INTERNSHIP_CLOSED · ข้อมูลไม่เปลี่ยน |
| ST-U7-13 | TC7 | ③ start → complete | S4: "เริ่มฝึกงาน" (ยืนยัน) → "เสร็จสิ้นการฝึกงาน" (ยืนยัน) | `POST /start` 200 แล้ว `POST /complete` 200 · `internships.status` = COMPLETED · `status_history` +2 · S4 และ advisor02 ได้แจ้งเตือนครั้งละ 1 · หน้ารายละเอียดล็อกช่องแก้ไข |
| ST-U7-14 | TC7 | ③ terminate | S6: "ยุติการฝึกงาน" ไม่กรอกเหตุผล → กรอก "บริษัทยกเลิกโครงการ" | ครั้งแรกไม่มี request · ครั้งที่สอง 200 TERMINATED · `terminated_reason` ตรง · timeline มีเหตุผล · S6 เห็นแจ้งเตือนพร้อมเหตุผล |
| ST-U7-15 | TC7 | ③ alt 409 / 400 | Postman `POST /complete` ของ S5 (NOT_STARTED) · `POST /start` ของ internship ที่ไม่มีผู้ดูแล | 409 INVALID_TRANSITION · 400 MISSING_INFO · `status_history` ไม่เพิ่ม |
| ST-U7-16 | TC2 | ③ ผู้ไม่มีสิทธิ์แก้ | Postman `advisor02` และ `6609650025` เรียก `PUT` / `POST /start` | 403 ทั้งคู่ · `internships` ไม่เปลี่ยน |
| ST-U7-17 | TC1, TC4 | U3 → U5 → U6 → U7 ทั้งเส้น | นักศึกษาใหม่ยื่นคำร้อง (U3) → staff01 + advisor01 อนุมัติ (U5) → จับคู่และนักศึกษายืนยัน (U6) → staff01 เริ่มและเสร็จสิ้นการฝึกงาน (U7) · เปิดรายละเอียดทุกขั้น | stage เปลี่ยนตามลำดับ APPLYING → WAITING_MATCH → MATCHING → READY → IN_PROGRESS → COMPLETED · timeline มีครบทั้ง APPLICATION, MATCH, INTERNSHIP |
| ST-U7-18 | – | ระบบล่ม | `docker compose stop app` แล้วเปิดหน้าสถานะนักศึกษา (เสร็จแล้ว `docker compose start app`) | แจ้ง "ไม่สามารถเชื่อมต่อระบบได้" ไม่ค้าง |

ความครอบคลุม
-
| เคส | TC ID |
|---|---|
| TC1 | ST-U7-01, ST-U7-02, ST-U7-17 |
| TC2 | ST-U7-05, ST-U7-06, ST-U7-07, ST-U7-16 |
| TC3 | ST-U7-03, ST-U7-10 |
| TC4 | ST-U7-08, ST-U7-17 |
| TC5 | ST-U7-09 |
| TC6 | ST-U7-11, ST-U7-12 |
| TC7 | ST-U7-10, ST-U7-13, ST-U7-14, ST-U7-15 |
| TC8 | ST-U7-04 |

ผลการรันบันทึกใน ส่วน "ผล System Test" ในไฟล์นี้


# ส่วนที่ 4 · Design UAT

-
- **As a** อาจารย์ที่ปรึกษา (และเจ้าหน้าที่ฝ่ายสหกิจศึกษา)
- **I want to** ติดตามสถานะของนักศึกษาตลอดกระบวนการฝึกงาน ตั้งแต่ยื่นใบสมัคร จับคู่บริษัท ฝึกงาน จนเสร็จสิ้น
- **So that** ตรวจสอบได้ทันทีว่านักศึกษาแต่ละคนอยู่ขั้นไหน ใครติดปัญหา โดยไม่ต้องไล่ถามทีละคน และนักศึกษาเห็นสถานะของตนเองได้

ผู้ทดสอบ
-
- ผู้รับบทอาจารย์ 1–2 คน (`advisor01`, `advisor02`) · เจ้าหน้าที่ 1 คน (`staff01`) · นักศึกษา 2 คน (`6609650024`, `6609650025`) รหัสผ่าน `1234`
- ทีมอธิบายแค่สถานการณ์ (Given) **ไม่บอกว่าต้องกดปุ่มไหน** แล้วสังเกตว่าผู้ทดสอบทำได้เองหรือไม่ จับเวลา และจดคำพูด / ปัญหาที่เจอ
- เริ่มทุกรอบด้วยการตั้ง `APP_SEED_U7_DATA: "true"` แล้ว `docker compose down -v` และ `docker compose up -d --build`

Test cases
-
| TC ID | เคส | Given | When | Then | เกณฑ์ที่ user ยอมรับได้ |
|---|---|---|---|---|---|
| UAT-U7-01 | TC1 | อาจารย์อยากรู้ว่านักศึกษาที่ดูแลอยู่ขั้นไหนบ้าง | เปิดหน้าสถานะนักศึกษา | เห็นนักศึกษาของตนทุกคนพร้อมขั้นล่าสุดและบริษัท | ตอบได้ว่าแต่ละคนอยู่ขั้นไหนภายใน 1 นาที |
| UAT-U7-02 | TC1 | อาจารย์ต้องการดูเฉพาะคนที่กำลังฝึกงาน / ค้นนักศึกษาคนหนึ่ง | กรองขั้นและค้นด้วยชื่อหรือรหัส | เหลือเฉพาะคนที่ต้องการ | หาเจอเองภายใน 30 วินาที |
| UAT-U7-03 | TC3 | มีนักศึกษาที่อนุมัติแล้วแต่ยังไม่ได้จับคู่นาน และคนที่เลยกำหนดยืนยันบริษัทแล้วแต่ยังไม่ยืนยัน | เปิดหน้ารายการ | คนที่มีปัญหามีป้ายเตือนอธิบายเหตุผล | รู้ว่าควรติดตามใครก่อนโดยไม่ต้องเปิดดูทีละคน |
| UAT-U7-04 | TC4 | อาจารย์อยากรู้ประวัติของนักศึกษาคนหนึ่ง | เปิดรายละเอียดนักศึกษา | เห็นใบสมัคร บริษัทที่ได้ ข้อมูลฝึกงาน และ timeline ว่าเปลี่ยนเมื่อไหร่ ใครเปลี่ยน | เข้าใจลำดับเหตุการณ์ได้โดยไม่ต้องถามเจ้าหน้าที่ |
| UAT-U7-05 | TC2 | อาจารย์ advisor01 ไม่ได้ดูแลนักศึกษาของ U7 | เปิดหน้ารายการ และลองเปิดรายละเอียดของ S6 (นักศึกษาของ advisor02) | เห็นเฉพาะนักศึกษาของตน · เปิดของคนอื่นไม่ได้ | มั่นใจว่าข้อมูลนักศึกษาไม่รั่วไปอาจารย์อื่น |
| UAT-U7-06 | TC5 | นักศึกษาอยากรู้ว่าตอนนี้ตัวเองอยู่ขั้นไหน | เปิดเมนูสถานะของฉัน | เห็นแถบขั้นทั้งหมด ขั้นปัจจุบัน บริษัท วันเริ่ม และผู้ดูแล | ตอบได้ว่าอยู่ขั้นไหนและต้องทำอะไรต่อภายใน 30 วินาที |
| UAT-U7-07 | TC7 | นักศึกษายืนยันบริษัทแล้ว (U6) | เจ้าหน้าที่เปิดรายละเอียดของนักศึกษาคนนั้น | มีข้อมูลการฝึกงานรอกรอก พร้อมป้ายข้อมูลไม่ครบ | เจ้าหน้าที่รู้ทันทีว่าต้องกรอกข้อมูลอะไรต่อ |
| UAT-U7-08 | TC6 | บริษัทแจ้งวันเริ่ม-สิ้นสุดและผู้ดูแลมาแล้ว | เจ้าหน้าที่กรอกข้อมูลการฝึกงาน | บันทึกสำเร็จ นักศึกษาและอาจารย์ได้แจ้งเตือน | ทำเองได้ภายใน 2 นาที · ถ้ากรอกวันผิด ระบบบอกและข้อมูลที่กรอกไม่หาย |
| UAT-U7-09 | TC7 | ถึงวันเริ่มฝึกงาน / ฝึกครบแล้ว | เจ้าหน้าที่เปลี่ยนสถานะเป็นเริ่มฝึกงาน แล้วเสร็จสิ้น | สถานะเปลี่ยน timeline เพิ่ม นักศึกษาและอาจารย์ได้แจ้งเตือน | ไม่กดผิดขั้น (ระบบไม่ให้ข้ามขั้น) และมั่นใจว่าบันทึกแล้ว |
| UAT-U7-10 | TC7 | บริษัทยกเลิกโครงการกลางคัน | เจ้าหน้าที่ยุติการฝึกงาน | ระบบบังคับใส่เหตุผล · นักศึกษาและอาจารย์เห็นเหตุผล | ผู้เกี่ยวข้องรู้สาเหตุโดยไม่ต้องถามต่อ |
| UAT-U7-11 | TC8 | ต้องส่งสรุปจำนวนนักศึกษาแต่ละขั้นให้หัวหน้าภาค | ดูตัวเลขแต่ละขั้น และดาวน์โหลด Excel | ตัวเลขตรงกับรายการ ไฟล์เปิดใน Excel ภาษาไทยอ่านได้ | ส่งต่อได้ทันทีไม่ต้องพิมพ์ใหม่ |
| UAT-U7-12 | – | ระบบใช้งานไม่ได้ชั่วคราว | เปิดหน้าสถานะ | มีข้อความแจ้งว่าเชื่อมต่อไม่ได้ ไม่ค้าง | ผู้ใช้รู้ว่าต้องลองใหม่ภายหลัง |

เกณฑ์ผ่าน UAT โดยรวม
-
- UAT-U7-01 ถึง UAT-U7-11 ต้องผ่านทุกข้อ (เคสบังคับตาม user story)
- ผู้ทดสอบอย่างน้อย 80% ทำ UAT-U7-01, 04, 06 ได้เองโดยไม่ต้องถามทีม
- ไม่มีปัญหาระดับสูง (ทำงานต่อไม่ได้ / ข้อมูลผิด / เห็นข้อมูลนักศึกษาที่ไม่มีสิทธิ์) ค้างอยู่ · บันทึกผลใน ส่วน "ผล UAT" ในไฟล์นี้

หมายเหตุ
-
- รวมทั้งหมด 71 test case: Integration UI–API 19 · Integration API–DAO 22 · System Test 18 · UAT 12
- ปรับตามข้อตกลงกับ U6 (T2 ของ U6 และ T3 ของ U7 ฉบับ 13 พ.ย.): S8 · ยืนยันสิทธิ์แล้ว U6 ส่ง `MatchConfirmedEvent` ให้ U7 สร้าง internship ใน transaction เดียวกัน (IT-AD-13, 21, 22 · ST-U7-10) · S9 · ป้าย `NOT_CONFIRMED` ดูจาก `confirmDeadline` ของรอบ ไม่นับ 7 วัน (S4, IT-UA-06, IT-AD-07)
- หน้าเว็บของ U7 คือ `student-status.html` (เจ้าหน้าที่ / อาจารย์) และ `my-status.html` (นักศึกษา) · หน้า `status.html` + `js/status.js` ชุดเก่าจาก Task4 รอบแรกลบออกแล้ว
- ชื่อตารางการฝึกงานใช้ `internships` ตามโค้ด Task4 และ ER ของ U6 (T2 ของ U7 เขียนเป็น `internship`)
- ออกแบบตาม Task2/Task3 (11 endpoint) · โค้ด Task4 อยู่ใน branch `Feature-/-U7-Status-Check` (รวม Develop และ U6 แล้ว) · ข้อมูลตัวอย่างตาม `U7DemoSeeder`
- สถานะ match ใช้ตามโค้ด U6: `PROPOSED`, `CONFIRMED`, `DECLINED`, `CANCELLED`
- role เจ้าหน้าที่ใช้ `STAFF` ตามโค้ด (ER Diagram และ Sequence ① ใน Task2 ใช้ `OFFICER`)
- อาจารย์ที่ปรึกษาอยู่ที่ `students.advisor_id` ตามโค้ด (ER Diagram วางไว้ที่ `users.advisor_id`)
- ER Diagram ใช้ชื่อตารางเดิมแบบเอกพจน์ (application, position, company, notification) แต่ตารางจริงในโค้ดคือ `applications`, `company_positions`, `companies`, `notifications`


# ส่วนที่ 5 · ผล Integration Test UI–API

Artefact: รายงานผล Integration Test · อ้างอิง test cases: ส่วน "Design Integration Test UI–API" ในไฟล์นี้

สภาพแวดล้อมที่ใช้รัน
-
| รายการ | ค่า |
|---|---|
| วันที่รัน | 14 พ.ย. 2569 00:54–02:03 |
| ผู้รัน | Sintawee (รันเองผ่านหน้าเว็บ + Chrome DevTools) |
| Branch / commit | `Feature-/-U7-Status-Check` เริ่มจาก `c230d15` (= Develop หลัง PR #47) · แก้ bug ระหว่างรัน แล้ว push เป็น `79e1420` |
| Backend | Spring Boot (docker compose) http://localhost:8080 · `APP_SEED_U7_DATA: "true"` |
| ฐานข้อมูล | MySQL 8.4 (docker compose) เริ่มจาก `docker compose down -v` |
| Frontend | nginx (docker compose) http://localhost:8081 |
| บัญชี | `staff01`, `advisor01`, `advisor02`, `6609650021`–`6609650027` รหัสผ่าน `1234` |
| เบราว์เซอร์ | Google Chrome 157 |

ขั้นตอนการรัน
-
1. Unit test ทั้งโปรเจกต์ก่อน: `mvn test` (ผ่าน Docker image `maven:3.9-eclipse-temurin-21`) → **444 tests, Failures 0, Errors 0, BUILD SUCCESS**
2. ตั้ง `APP_SEED_U7_DATA: "true"` แล้ว `docker compose down -v` และ `docker compose up -d --build`
3. login ตามผู้ใช้ที่ test case กำหนด เปิด DevTools แท็บ Network ดู method, URL, status, response
4. ข้อที่ต้องยิง request ตรง (แทน Postman) ใช้ `fetch` ใน DevTools Console
5. บันทึกผล ✅ ผ่าน / ❌ ไม่ผ่าน / ⚠️ ผ่านบางส่วน

รายงานผล
-
| TC ID | Scenario | Expected | Actual | ผล |
|---|---|---|---|---|
| IT-UA-01 | รายการนักศึกษา | `status?page=0` + `summary` 200 · S1–S7 อยู่ในรายการ | `GET /api/students/status?page=0` 200 · `summary` 200 · ตารางครบทุกคอลัมน์ · S1–S7 ขั้นตรงตามตารางข้อมูลตัวอย่าง | ✅ |
| IT-UA-02 | ตัวเลขแต่ละขั้น | ตัวเลขตรง response · ขั้นที่เป็น 0 ไม่มีตัวเลข | ตัวเลขรวม 7+3+5+1+1+1+1 = 19 ตรงกับ "ทั้งหมด" · ขั้นที่เป็น 0 แสดงเลข 0 (unit test UI-02 กำหนดให้แสดง 0) | ✅ |
| IT-UA-03 | กรองขั้น + ค้นหา | `stage=MATCHING&q=6609650024` · เหลือ S4 | `status?stage=MATCHING&q=6609650024&page=0` 200 · เหลือแถว S4 · หน้า 1 จาก 1 | ✅ |
| IT-UA-04 | กรองบริษัท | `companyId={บริษัทของ S6}` · เห็น S6 | `status?companyId=2&page=0` 200 (Agoda Services id 2) · เหลือ S6 แถวเดียว | ✅ |
| IT-UA-05 | แบ่งหน้า | `page=1` · "หน้า 2 จาก N" | ข้อมูลในระบบมี 19 คน (< 20 ต่อหน้า) จึงไม่มีหน้า 2 · ใช้ผล unit test UI-04 (แบ่งหน้าและกดหน้าถัดไป) ผ่าน | ✅ (unit test) |
| IT-UA-06 | ป้ายเตือน | S3 NO_MATCH · S4 NOT_CONFIRMED · แถวอื่นไม่มีป้าย | หลังเตรียมข้อมูลข้อ ก, ข: S3 "อนุมัติเกิน 14 วัน แต่ยังไม่ได้จับคู่" · S4 "ยังไม่ยืนยันผลจับคู่" · ทั้งสองแถวไฮไลต์ · แถวอื่นไม่มีป้าย | ✅ |
| IT-UA-07 | อาจารย์เห็นเฉพาะของตน | advisor02 เห็น S1–S7 · advisor01 ไม่เห็น | advisor02 เห็น 7 คน (21–27) ตัวเลขแท็บนับเฉพาะของตน ไม่มีตัวกรองอาจารย์/ปุ่มแก้ไข · advisor01 เห็น 8 คนของตน (01–08) ไม่เห็น 21–27 | ✅ |
| IT-UA-08 | ดาวน์โหลด Excel | `export?stage=IN_PROGRESS` 200 · ไฟล์ .csv ภาษาไทยอ่านได้ มี S6 | `export?stage=IN_PROGRESS` 200 · ไฟล์ `student-status-in_progress.csv` เป็น UTF-8 with BOM · หัวตารางภาษาไทยอ่านได้ · มีแถว 6609650026 แถวเดียว | ✅ |
| IT-UA-09 | หน้ารายละเอียด + timeline | ครบทุกส่วน · timeline เก่า → ใหม่ พร้อมชื่อผู้เปลี่ยน | `GET /api/students/18/status` 200 · เห็นข้อมูลนักศึกษา ใบสมัคร ผลจับคู่ (Agoda) ข้อมูลฝึกงาน · timeline มีชื่อ "S staff" · ส่วน "อันดับที่เลือก" ว่างเพราะ seeder ไม่ได้สร้างอันดับ | ✅ |
| IT-UA-10 | นักศึกษายังไม่ยื่น | "ยังไม่ยื่นใบสมัคร" · ไม่มี error | 200 stage NOT_APPLIED · "ยังไม่ยื่นใบสมัคร" · ส่วนผลจับคู่/ฝึกงานแสดงข้อความ "ยังไม่มี…" แทนการซ่อน | ✅ |
| IT-UA-11 | แก้ข้อมูลการฝึกงาน | `PUT /api/internships/{id}` 200 · ค่าใหม่บนหน้า | `PUT /api/internships/1` 200 · "บันทึกข้อมูลการฝึกงานแล้ว" · 01/12/2026–31/03/2027 · คุณสมศรี ใจดี · สถานะยัง NOT_STARTED | ✅ |
| IT-UA-12 | วันที่ผิดหลุดไปถึง server | 400 INVALID_DATE_RANGE · ข้อมูลไม่เปลี่ยน | `fetch PUT` endDate < startDate → 400 `{code: 'INVALID_DATE_RANGE', message: 'วันสิ้นสุดต้องหลังวันเริ่ม'}` · โหลดใหม่ข้อมูลยังเหมือนเดิม | ✅ |
| IT-UA-13 | เริ่มฝึกงาน | `start` 1 ครั้ง 200 · ป้าย "กำลังฝึกงาน" · timeline +1 | `POST /api/internships/1/start` 1 ครั้ง 200 · "เริ่มฝึกงานเรียบร้อยแล้ว" · ป้าย "กำลังฝึกงาน" · timeline เพิ่ม NOT_STARTED → IN_PROGRESS | ✅ |
| IT-UA-14 | เริ่มฝึกงานแต่ข้อมูลไม่ครบ | 400 MISSING_INFO · สถานะไม่เปลี่ยน | S4 (internship 4 ยังไม่มีวันที่/ผู้ดูแล) `start` → 400 · "กรุณากรอกข้อมูลการฝึกงานให้ครบก่อนเริ่มฝึกงาน" · สถานะยัง "ยังไม่เริ่ม" | ✅ |
| IT-UA-15 | ยุติพร้อมเหตุผล | ครั้งแรกไม่มี request · ครั้งที่สอง 200 TERMINATED | ไม่กรอกเหตุผล: แจ้ง "กรุณากรอกเหตุผล" ไม่มี request · กรอก "บริษัทยกเลิกโครงการ": `terminate` 200 · ป้าย "ยุติการฝึกงาน" ช่องข้อมูลล็อก · timeline มีเหตุผล | ✅ |
| IT-UA-16 | อาจารย์ขอนักศึกษาคนอื่น | 403 FORBIDDEN | `GET /api/students/18/status` (X-User-Id 2 = advisor01) → 403 `{code: 'FORBIDDEN', message: 'คุณไม่มีสิทธิ์ใช้งานส่วนนี้'}` ไม่มีข้อมูลนักศึกษา | ✅ |
| IT-UA-17 | สถานะของฉัน | แถบขั้น · บริษัท วันเริ่ม ผู้ดูแล · ไม่มีปุ่มแก้ไข | `GET /api/students/me/status` 200 · แถบ 9 ขั้น ขั้นปัจจุบัน "กำลังฝึกงาน" (เริ่มไปแล้วใน IT-UA-13) · SCB TechX · 1 ธ.ค. 2569 · คุณสมศรี ใจดี · ไม่มีปุ่มแก้ไข | ✅ |
| IT-UA-18 | นักศึกษาเปิดหน้าเจ้าหน้าที่ | ไม่มี request รายการ · หน้าไม่มีสิทธิ์ | "หน้านี้สำหรับเจ้าหน้าที่และอาจารย์เท่านั้น" + ปุ่ม "ดูสถานะของฉัน" · ไม่มี request `/api/students/status` | ✅ |
| IT-UA-19 | server ไม่ทำงาน | "ไม่สามารถเชื่อมต่อระบบได้" ไม่ค้าง | `docker compose stop app` → request `(failed)` · "ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง" · หน้าไม่ค้าง | ✅ |

สรุป: **19/19 ผ่าน** (IT-UA-05 ใช้ผล unit test)


หลักฐาน (โฟลเดอร์ `evidence/`)
-
| ไฟล์ | TC ID |
|---|---|
| [U7-IT-UA-06-attention.png](<evidence/U7-IT-UA-06-attention.png>) | IT-UA-06 ป้ายเตือน S3 / S4 |
| [U7-IT-UA-16-403.png](<evidence/U7-IT-UA-16-403.png>) | IT-UA-16 อาจารย์ขอนักศึกษาคนอื่น → 403 |

![IT-UA-06](<evidence/U7-IT-UA-06-attention.png>)

![IT-UA-16](<evidence/U7-IT-UA-16-403.png>)


# ส่วนที่ 6 · ผล Integration Test API–DAO

Artefact: รายงานผล Integration Test · อ้างอิง test cases: ส่วน "Design Integration Test API–DAO" ในไฟล์นี้

วิธีรัน
-
- ข้อที่ทำได้กับระบบจริง: ยิง request ผ่านหน้าเว็บหรือ `fetch` ใน DevTools แล้วตรวจฐานข้อมูลด้วย `docker exec -it cs261-db mysql -uroot -proot1234 cs261db -e "..."`
- ข้อที่ต้องควบคุมเวลา (Clock) หรือทำให้ฐานข้อมูลโยน error: ใช้ผล unit test ของ Task4 (`StudentStatusControllerTest`, `InternshipControllerTest`, `StudentStatusServiceTest`, `InternshipServiceTest`, `InternshipRepositoryTest` · รวมอยู่ใน 444 tests ที่ผ่าน)
- ระบบจริงมีนักศึกษาของ Story อื่นอยู่ด้วย (19 คน) จึงตรวจเฉพาะ S1–S7 แทนการนับทั้งตาราง

รายงานผล
-
| TC ID | วิธี | Actual | ผล |
|---|---|---|---|
| IT-AD-01 | ระบบจริง | `status?page=0` 200 · S1 มีแถวแม้ไม่มีใบสมัคร · บริษัทของ S4–S7 ตรงกับ match | ✅ |
| IT-AD-02 | ระบบจริง | `stage=MATCHING` + `q=6609650024` เหลือ S4 · `companyId=2` เหลือ S6 | ✅ |
| IT-AD-03 | unit test | ข้อมูลจริงไม่ถึง 2 หน้า ใช้ unit test แบ่งหน้า | ✅ (unit test) |
| IT-AD-04 | ระบบจริง | advisor01 เห็นเฉพาะ 01–08 ไม่เห็น S1–S7 | ✅ |
| IT-AD-05 | ระบบจริง + unit test | นักศึกษาเปิดหน้ารายการแล้วไม่มี request ออกไป · กรณี 403 / 401 จาก API ใช้ unit test | ✅ |
| IT-AD-06 | ระบบจริง | stage ของ S1–S7 ตรงตาราง · S3 NO_MATCH · S4 NOT_CONFIRMED | ✅ |
| IT-AD-07 | unit test | ขอบเขต 14 วัน / confirmDeadline ต้องใช้ Clock คงที่ | ✅ (unit test) |
| IT-AD-08 | ระบบจริง | advisor02: ทุกขั้นที่ S1–S7 อยู่ = 1 · ไม่อนุมัติ/ยุติ = 0 | ✅ |
| IT-AD-09 | ระบบจริง | `export?stage=IN_PROGRESS` 200 UTF-8 BOM · หัวตาราง + 1 แถว (S6) | ✅ |
| IT-AD-10 | ระบบจริง | `GET /api/students/18/status` 200 ครบทุกส่วน · timeline เรียงเก่า → ใหม่ มีชื่อผู้เปลี่ยน | ✅ |
| IT-AD-11 | unit test | ใบสมัครล่าสุดเมื่อมีหลายใบ | ✅ (unit test) |
| IT-AD-12 | ระบบจริง | `GET /api/students/me/status` (S5) ได้ข้อมูลของ S5 เท่านั้น | ✅ |
| IT-AD-13 | ระบบจริง | **รอบแรก ❌** `POST /api/matches/{id}/respond` → 500 (`Internship.updatedBy` เป็น null) · **หลังแก้ ✅** respond 200 · `internships` id 4 NOT_STARTED ผูก match · `status_history` INTERNSHIP NULL → NOT_STARTED changed_by 1 · stage S4 = READY | ✅ (หลังแก้ bug #1) |
| IT-AD-14 | ระบบจริง | PUT 2 ครั้ง (S5, S4) → `notifications` INTERNSHIP_UPDATED = 4 (ครั้งละ 2 คน) · status ไม่เปลี่ยน · `status_history` ไม่เพิ่ม | ✅ |
| IT-AD-15 | ระบบจริง + unit test | endDate < startDate → 400 INVALID_DATE_RANGE ข้อมูลไม่เปลี่ยน · อีเมลผิด / 409 INTERNSHIP_CLOSED ใช้ unit test | ✅ |
| IT-AD-16 | ระบบจริง | S4: start 200 → complete 200 · `internships.status` COMPLETED updated_by 1 · `status_history` NOT_STARTED → IN_PROGRESS → COMPLETED changed_by 1 · INTERNSHIP_STATUS_CHANGED = 8 (เปลี่ยน 4 ครั้ง × 2 คน) | ✅ |
| IT-AD-17 | ระบบจริง | S6 terminate 200 TERMINATED · timeline มีเหตุผล "บริษัทยกเลิกโครงการ" · reason ว่างถูกกันที่หน้าเว็บ (400 จาก API ใช้ unit test) | ✅ |
| IT-AD-18 | ระบบจริง | start ของ COMPLETED (id 4) → 409 · start เมื่อไม่มีผู้ดูแล → 400 MISSING_INFO · ข้อมูลไม่เปลี่ยน | ✅ |
| IT-AD-19 | ระบบจริง | advisor02 (id 15) และ S5 (id 20) เรียก PUT / POST → 403 ทั้ง 4 ครั้ง | ✅ |
| IT-AD-20 | unit test | rollback เมื่อบันทึก history ไม่สำเร็จ | ✅ (unit test) |
| IT-AD-21 | ระบบจริง (เกิดจริง) | ตอน bug #1 respond ได้ 500 → `matches` ยัง PROPOSED (หน้าเว็บ "รอยืนยัน") · ไม่มี internship ค้าง = rollback ทั้ง transaction ตามข้อตกลง S8 | ✅ |
| IT-AD-22 | unit test | เรียก createFromMatch ซ้ำไม่สร้างแถวซ้ำ | ✅ (unit test) |

สรุป: **22/22 ผ่าน** (ระบบจริง 15 ข้อ · unit test 7 ข้อ) · IT-AD-13 ผ่านหลังแก้ bug #1


หลักฐาน (โฟลเดอร์ `evidence/`)
-
| ไฟล์ | TC ID |
|---|---|
| [U7-IT-AD-sql.png](<evidence/U7-IT-AD-sql.png>) | IT-AD-13, 14, 16 ผล SQL `internships` · `status_history` · `notifications` |
| [T4-u7-backend-unit-test-output.txt](<evidence/T4-u7-backend-unit-test-output.txt>) · [T4-api-dao-test-output.txt](<evidence/T4-api-dao-test-output.txt>) | ข้อที่ใช้ผล unit test (Task4) |

![IT-AD SQL](<evidence/U7-IT-AD-sql.png>)


# ส่วนที่ 7 · ผล System Test

Artefact: รายงานผล System Test · อ้างอิง test cases: ส่วน "Design System Test" ในไฟล์นี้ · สภาพแวดล้อมเดียวกับส่วนที่ 5

รายงานผล
-
| TC ID | เคส | Actual | ผล |
|---|---|---|---|
| ST-U7-01 | TC1 | ค้น "660965002" เห็น S1–S7 · ป้ายขั้นตรงตาราง (ผลเดียวกับ IT-UA-01) | ✅ |
| ST-U7-02 | TC1 | request มี `stage`, `q`, `companyId` ตามที่เลือก (IT-UA-03, 04) · แบ่งหน้าใช้ unit test | ✅ |
| ST-U7-03 | TC3 | S3 NO_MATCH · S4 NOT_CONFIRMED (IT-UA-06) · START_OVERDUE แสดงจริงบน S5 ตอนวันเริ่มเป็นอดีต (เจอระหว่างรันก่อนแก้ seeder) | ✅ |
| ST-U7-04 | TC8 | ตัวเลขแท็บรวม 19 ตรงตาราง · CSV เปิดใน Excel ภาษาไทยอ่านได้ (IT-UA-02, 08) | ✅ |
| ST-U7-05 | TC2 | advisor02 เห็นเฉพาะ S1–S7 ไม่มีปุ่มแก้ไข · advisor01 ไม่เห็น S1–S7 (IT-UA-07) | ✅ |
| ST-U7-06 | TC2 | API 403 ไม่มีชื่อ/รหัสนักศึกษาใน body (IT-UA-16) · หน้ารายละเอียดไม่มี URL ของตัวเองจึงทดสอบผ่าน API | ✅ |
| ST-U7-07 | TC2 | นักศึกษาเปิด `student-status.html` ได้หน้าไม่มีสิทธิ์ (IT-UA-18) · 401 เมื่อไม่ส่ง `X-User-Id` ใช้ unit test | ✅ |
| ST-U7-08 | TC4 | รายละเอียด S6 ครบ · timeline เรียงเก่า → ใหม่ ตรงกับ `status_history` (IT-UA-09) | ✅ |
| ST-U7-09 | TC5 | `me/status` 200 · แถบขั้น · บริษัท วันเริ่ม ผู้ดูแล · ไม่มีปุ่มแก้ไข (IT-UA-17) | ✅ |
| ST-U7-10 | TC7 | **รอบแรก ❌** respond 500 (bug #1) · **หลังแก้ ✅** respond 200 "ยืนยันสิทธิ์เรียบร้อยแล้ว" · `matches` CONFIRMED · internship NOT_STARTED ถูกสร้างทันที · S4 = "พร้อมฝึกงาน" + ป้าย "ข้อมูลการฝึกงานยังไม่ครบ" | ✅ (หลังแก้ bug #1) |
| ST-U7-11 | TC6 | `PUT /api/internships/4` 200 · ป้ายข้อมูลไม่ครบหายไป · แจ้งเตือน INTERNSHIP_UPDATED +2 | ✅ |
| ST-U7-12 | TC6 | `PUT` วันผิด → 400 INVALID_DATE_RANGE ข้อมูลไม่เปลี่ยน (IT-UA-12) · ช่องของ internship ที่ปิดแล้วถูกล็อกบนหน้าเว็บ · 409 INTERNSHIP_CLOSED จาก API ใช้ unit test | ✅ |
| ST-U7-13 | TC7 | S4: start 200 → complete 200 · "บันทึกการฝึกงานเสร็จสิ้นแล้ว" · COMPLETED · `status_history` +2 · ช่องแก้ไขถูกล็อก | ✅ |
| ST-U7-14 | TC7 | S6 terminate (IT-UA-15) · ครั้งแรกไม่มี request · ครั้งที่สอง 200 · เหตุผลอยู่ใน timeline | ✅ |
| ST-U7-15 | TC7 | start ของ COMPLETED → 409 · start ตอนข้อมูลไม่ครบ → 400 MISSING_INFO · history ไม่เพิ่ม | ✅ |
| ST-U7-16 | TC2 | advisor02 / 6609650025 เรียก PUT และ POST → 403 ทั้ง 4 ครั้ง | ✅ |
| ST-U7-17 | TC1, TC4 | ไม่ได้รันทั้งเส้น U3 → U7 · ส่วน U6 → U7 ครอบคลุมใน ST-U7-10 และ 13 | ⏭ ไม่ได้รัน |
| ST-U7-18 | – | `docker compose stop app` → "ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง" ไม่ค้าง (IT-UA-19) | ✅ |

สรุป: **17/18 ผ่าน · 1 ไม่ได้รัน (ST-U7-17)** · ST-U7-10 ผ่านหลังแก้ bug #1


หลักฐาน (โฟลเดอร์ `evidence/`)
-
| ไฟล์ | TC ID |
|---|---|
| [U7-ST-10-before-500.png](<evidence/U7-ST-10-before-500.png>) | ST-U7-10 รอบแรก respond 500 (bug #1) |
| [U7-ST-10-after-200.png](<evidence/U7-ST-10-after-200.png>) | ST-U7-10 หลังแก้ respond 200 ยืนยันแล้ว |
| [U7-ST-13-completed.png](<evidence/U7-ST-13-completed.png>) | ST-U7-11, 13 บันทึก → start → complete |
| [U7-ST-15-16-console.png](<evidence/U7-ST-15-16-console.png>) | ST-U7-15 (409) · ST-U7-16 (403 × 4) |

![ST-U7-10 ก่อนแก้](<evidence/U7-ST-10-before-500.png>)

![ST-U7-10 หลังแก้](<evidence/U7-ST-10-after-200.png>)

![ST-U7-13](<evidence/U7-ST-13-completed.png>)

![ST-U7-15, 16](<evidence/U7-ST-15-16-console.png>)

Bug ที่พบระหว่างรันและแก้แล้ว (commit `79e1420`)
-
| # | ความรุนแรง | อาการ | สาเหตุ | แก้ |
|---|---|---|---|---|
| 1 | สูง | นักศึกษากดยืนยันสิทธิ์ (U6) ได้ 500 · ยืนยันไม่ได้เลย | `InternshipService.createFromMatch` ส่ง `changedBy` (null) ให้ `Internship` และ `status_history` ทั้งที่ `updated_by` / `changed_by` เป็น NOT NULL · unit test ใช้ mock จึงจับไม่ได้ | ใช้ `effectiveChangedBy` (ผู้จับคู่) แทน 2 จุด |
| 2 | กลาง | เมนูไม่มีลิงก์ไปหน้าของ U7 · ต้องพิมพ์ URL เอง | `u5.js`, `u6.js` และหน้าของนักศึกษาไม่ได้ใส่ลิงก์ | เพิ่ม "สถานะนักศึกษา" (STAFF/ADVISOR) และ "สถานะของฉัน" (STUDENT) |
| 3 | กลาง | ปุ่ม "ยุติการฝึกงาน" มองไม่เห็นตัวหนังสือ | `.btn.danger` ใน `style.css` (U8) ให้ตัวหนังสือแดง ส่วน `u7.css` ให้พื้นแดง | `u7.css` เพิ่ม `color: #fff` |
| 4 | ต่ำ | S5 ขึ้นป้าย START_OVERDUE ทั้งที่ควรไม่มีป้าย | `U7DemoSeeder` ตั้งวันเริ่มของทุกคนเป็นเมื่อวาน | NOT_STARTED เริ่มอีก 7 วัน |

ข้อสังเกตที่ยังไม่ได้แก้
-
- `U7DemoSeeder` ทำงานทุกครั้งที่ app restart และเขียนทับวันที่ / ผู้ดูแลของ internship ที่มีอยู่แล้ว (เฉพาะตอนเปิด `APP_SEED_U7_DATA`)
- U6 ไม่บันทึก `status_history` MATCH PROPOSED → CONFIRMED ตอนนักศึกษายืนยันสิทธิ์ · timeline จึงขาดขั้นนี้
- เวลาบนหน้าเว็บเป็น UTC (ช้ากว่าเวลาไทย 7 ชม.) เพราะ container ไม่ได้ตั้ง timezone · เกิดทุก Story
- เมนูของ `student-status.html` / `my-status.html` เขียนตายตัวใน HTML ไม่ตรงกับ role (อาจารย์และนักศึกษาเห็นเมนูเจ้าหน้าที่)
- หน้ารายละเอียดไม่มี URL ของตัวเอง กด F5 แล้วกลับหน้ารายการ
- หน้าตรวจสอบคุณสมบัติ (U1–U4) ไม่มีลิงก์ไปหน้า "เลือกตำแหน่ง / ผลจับคู่" (U6)
- CSV ใช้ชื่อขั้นภาษาอังกฤษ (`IN_PROGRESS`) ไม่ใช่ภาษาไทยเหมือนหน้าเว็บ
- `favicon.ico` 404 · login ด้วยชื่อที่ไม่อยู่ใน demo-users ได้ 502 (U1 ไม่มี TU API key)


# ส่วนที่ 8 · ผล UAT

Artefact: รายงานผล UAT พร้อม feedback

- **ยังไม่ได้ทดสอบ** · ทีมตกลงรันเฉพาะ Integration Test และ System Test ในรอบนี้
- test cases พร้อมใช้งานตามส่วนที่ 4 (UAT-U7-01 ถึง 12) · เตรียมระบบด้วย `APP_SEED_U7_DATA: "true"` แล้ว `docker compose down -v` และ `docker compose up -d --build`