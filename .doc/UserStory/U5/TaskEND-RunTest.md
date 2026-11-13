US5:TaskEND : Run Test (Integration Test + System Test + User Acceptance Test)
-
User story U5: เจ้าหน้าที่ตรวจสอบใบสมัครสหกิจของนักศึกษา แล้วอนุมัติหรือส่งกลับแก้ไข --> จากนั้นอาจารย์พิจารณาอนุมัติ และระบบแจ้งผลให้นักศึกษา

> เอกสารของ Task นี้ตามเลข Task ของ Sprint 2 ใน Trello · รวมจากไฟล์ T13–T20


# ส่วนที่ 1 · Design Integration Test UI–API

ขอบเขต
-
- หน้า staff-forms.html, staff-review.html, notifications.html เรียก backend จริง (ฐานข้อมูลใหม่ + DemoDataSeeder)
- วิธีตรวจ: DevTools แท็บ Network ดู request/response และดูผลบนหน้าจอ

Design Integration Test ระหว่าง UI กับ API
-
| TC ID | Scenario | Input (บนหน้าเว็บ) | Request ที่ต้องเห็น | Response | Expected บนหน้าเว็บ |
|---|---|---|---|---|---|
| IT-UA-01 | สร้างแบบฟอร์ม (TC1) | เจ้าหน้าที่ กรอกชื่อ 3 ช่อง แล้วกดบันทึกฉบับร่าง | POST /api/forms | 201 DRAFT | กลับหน้ารายการ แจ้งบันทึกฉบับร่าง เห็นฉบับใหม่ |
| IT-UA-02 | เปิดใช้งาน (TC1) | กดเปิดใช้งาน 2 ครั้ง | POST /api/forms/{id}/publish | 200 PUBLISHED | ฉบับใหม่เปิดใช้งาน ฉบับเดิมเลิกใช้ |
| IT-UA-03 | ชื่อช่องซ้ำหลุดไปถึง server (TC2) | ช่อง 'อีเมล' 2 ช่อง | POST /api/forms | 400 VALIDATION_ERROR | แสดงข้อความจาก server ข้อมูลที่กรอกไม่หาย |
| IT-UA-04 | แก้ฉบับที่เปิดใช้งาน (TC2) | ยิง PUT ด้วย Postman ขณะหน้าเว็บเปิดอยู่ | PUT /api/forms/{id} | 409 | หน้าเว็บไม่มีปุ่มแก้ไขฉบับนี้ |
| IT-UA-05 | รายการคำร้อง (TC3) | เจ้าหน้าที่เปิดหน้าตรวจ | GET /api/applications?status=SUBMITTED | 200 2 รายการ | ตารางมี 2 แถว ชื่อและเอกสารตรง |
| IT-UA-06 | รายละเอียดคำร้อง (TC3) | กดตรวจคำร้องแรก | GET /api/applications/{id}/detail | 200 | คำตอบครบทุกช่อง และไฟล์ PDF |
| IT-UA-07 | อนุมัติส่งต่ออาจารย์ (TC4) | เลือกอนุมัติ กดบันทึก | POST /api/approvals/{id}/decision {decision: APPROVE} | 200 UNDER_REVIEW | หน้าสำเร็จ คำร้องหายจากรายการรอตรวจ |
| IT-UA-08 | ส่งกลับแก้ไข (TC4) | เลือกส่งกลับ ใส่เหตุผล | POST decision {RETURN, comment} | 200 NEED_CORRECTION | สถานะใหม่ส่งกลับแก้ไข |
| IT-UA-09 | นักศึกษาเห็นแจ้งเตือน (TC4) | นักศึกษาเปิด notifications.html | GET /api/notifications | 200 | เห็นแจ้งเตือนพร้อมเหตุผลอยู่บนสุด |
| IT-UA-10 | ตัดสินซ้ำจาก 2 หน้าต่าง (TC5) | เปิดคำร้องเดียวกัน 2 แท็บ บันทึกทั้งคู่ | POST decision 2 ครั้ง | 200 แล้ว 409 | แท็บที่สองแสดงข้อความว่าตัดสินไปแล้ว |
| IT-UA-11 | อาจารย์พิจารณา (TC4) | อาจารย์อนุมัติคำร้อง UNDER_REVIEW | POST decision | 200 APPROVED | สถานะใหม่อนุมัติแล้ว |
| IT-UA-12 | server ไม่ทำงาน | ปิด backend แล้วเปิดหน้า | network error | – | 'ไม่สามารถเชื่อมต่อระบบได้' |
| IT-UA-13 | ดูตัวอย่างหน้านักศึกษา (TC6) | แก้แบบฟอร์ม เพิ่มช่องชนิด "แนบไฟล์" แล้วกด "ดูตัวอย่างหน้านักศึกษา" | ไม่มี request | – | เห็นหน้าตาแบบนักศึกษา ช่องไฟล์เป็นปุ่มเลือกไฟล์ ช่องบังคับมี * · ยังไม่บันทึก |
| IT-UA-14 | ปิดรับก่อนกำหนด (TC6) | กด "ปิดรับก่อนกำหนด" 2 ครั้ง ที่ฉบับที่เปิดใช้งาน | POST /api/forms/{id}/close | 200 deadline = เวลานี้ | แจ้งปิดรับแล้ว ปุ่มหายไป · หน้ายื่นคำร้องของนักศึกษาขึ้นว่าหมดเขต |
| IT-UA-15 | แนบไฟล์หลายช่อง (TC6) | แบบฟอร์มมีช่องไฟล์ 2 ช่อง · นักศึกษาแนบ Transcript และไฟล์ช่องที่ 2 แล้วส่ง | POST /api/requests/{id}/documents แล้ว POST .../documents?fieldId={id} | 201, 201 แล้ว submit 200 | ส่งสำเร็จ · หน้าตรวจของเจ้าหน้าที่เห็นชื่อไฟล์ทั้ง 2 ช่อง |
| IT-UA-16 | ขาดไฟล์ช่องที่ 2 (TC6) | แนบแค่ Transcript แล้วส่ง | POST .../submit | 400 MISSING_DOCUMENT | ข้อความบอกชื่อช่องที่ยังไม่ได้แนบ |
| IT-UA-17 | จำนวนทุกสถานะ (TC7) | เจ้าหน้าที่เปิดหน้าตรวจ | GET /api/applications?status=SUBMITTED แล้ว GET /api/applications/summary | 200 | ตัวเลขบนทุกแท็บตรงกับจำนวนคำร้องในฐานข้อมูล |
| IT-UA-18 | ป้ายรอนาน (TC7) | มีคำร้องที่ส่งมาเกิน 3 วัน | GET /api/applications?status=SUBMITTED | 200 `attention: OVERDUE` | แถวนั้นมีป้าย "รอนาน" ไฮไลต์และอยู่บนสุด |
| IT-UA-19 | ดาวน์โหลด Excel (TC7) | กด "ดาวน์โหลด Excel" | GET /api/applications/export?status=SUBMITTED | 200 text/csv | ได้ไฟล์ `applications-SUBMITTED.csv` เปิดใน Excel ภาษาไทยอ่านได้ |
| IT-UA-20 | ส่งกลับพร้อมช่องที่ต้องแก้ (TC8) | เลือกส่งกลับ เลือกเหตุผลที่ใช้บ่อย ติ๊กช่อง "เบอร์โทรศัพท์" | POST decision {RETURN, comment, fieldIds: [id]} | 200 NEED_CORRECTION | ประวัติแสดงเหตุผลและ "ช่องที่ต้องแก้: เบอร์โทรศัพท์" |
| IT-UA-21 | อนุมัติหลายใบ (TC8) | ติ๊ก "เลือกทั้งหมด" กด "อนุมัติที่เลือก" 2 ครั้ง | POST /api/approvals/bulk {applicationIds} | 200 approved / skipped | แจ้งจำนวนที่อนุมัติ และใบที่ถูกข้ามพร้อมเหตุผล · รายการรอตรวจโหลดใหม่ |
| IT-UA-22 | ดูค่าเดิมของฉบับแก้ (TC8) | หลัง IT-UA-20 นักศึกษาแก้เบอร์แล้วส่งใหม่ · เจ้าหน้าที่เปิดรายละเอียด | GET /api/applications/{id}/detail | 200 `previousValue` | ช่องเบอร์โทรมีป้าย "แก้แล้ว" และค่าเดิมขีดฆ่า |
| IT-UA-23 | เจ้าหน้าที่ได้แจ้งเตือน (TC9) | หลัง IT-UA-22 เจ้าหน้าที่เปิด notifications.html | GET /api/notifications | 200 | เห็น "มีคำร้องที่แก้ไขแล้วส่งกลับมา" |

ความครอบคลุมกับ test case ของ U5
-
| เคส | TC ID |
|---|---|
| TC1 เจ้าหน้าที่สร้างแบบฟอร์มและเปิดใช้งานได้ นักศึกษาใช้ฉบับที่เปิดอยู่ยื่นคำร้อง | IT-UA-01, IT-UA-02 |
| TC2 แบบฟอร์มข้อมูลไม่ครบหรือผิด (ไม่มีชื่อ ไม่มีช่อง ชื่อช่องซ้ำ ไม่มีวันหมดเขต) ถูกปฏิเสธ | IT-UA-03, IT-UA-04 |
| TC3 เจ้าหน้าที่เห็นคำร้องที่ยื่นเข้ามาครบ: ข้อมูลนักศึกษา คำตอบทุกช่อง และไฟล์ PDF | IT-UA-05, IT-UA-06 |
| TC4 เจ้าหน้าที่อนุมัติส่งต่ออาจารย์ หรือส่งกลับแก้ไข/ปฏิเสธพร้อมเหตุผล และนักศึกษาได้รับแจ้งทุกครั้ง | IT-UA-07, IT-UA-08, IT-UA-09, IT-UA-11 |
| TC5 การตัดสินที่ไม่ถูกต้องถูกปฏิเสธ: ตัดสินซ้ำ ข้ามขั้น หรือผู้ใช้ที่ไม่มีสิทธิ์ | IT-UA-10 |
| TC6 แบบฟอร์ม: ช่องไฟล์หลายช่อง · ดูตัวอย่าง · ปิดรับก่อนกำหนด | IT-UA-13, IT-UA-14, IT-UA-15, IT-UA-16 |
| TC7 ภาพรวมคำร้อง: จำนวนทุกสถานะ · ป้ายรอนาน / ใกล้หมดเขต · Excel | IT-UA-17, IT-UA-18, IT-UA-19 |
| TC8 ตรวจเร็วขึ้น: เหตุผลที่ใช้บ่อย · ช่องที่ต้องแก้ · อนุมัติหลายใบ · ค่าเดิมของฉบับแก้ | IT-UA-20, IT-UA-21, IT-UA-22 |
| TC9 แจ้งเตือนเพิ่ม: แจ้งเจ้าหน้าที่ · เตือนนักศึกษาก่อนหมดเขต | IT-UA-23 (การเตือนตอน 08:00 ทดสอบใน T14 IT-AD-20) |


# ส่วนที่ 2 · Design Integration Test API–DAO

ขอบเขต
-
- ทดสอบ Controller → Service → Repository → ฐานข้อมูล โดยไม่ใช้หน้าเว็บ ส่ง request ด้วย Postman หรือ MockMvc (@SpringBootTest) แล้วตรวจข้อมูลผ่าน Repository
- ฐานข้อมูลทดสอบ: H2 หรือ MySQL ทดสอบ ล้างข้อมูลก่อนทุกข้อ · Clock เป็น Bean จึงกำหนดเวลาใน test ได้ (ใช้ทดสอบวันหมดเขต)
- ผู้ใช้: 1 เจ้าหน้าที่, 2 อาจารย์, 3–4 นักศึกษา (DemoDataSeeder)

Design Integration Test ระหว่าง API กับ DAO
-
| TC ID | Scenario | Request | DAO ที่ถูกเรียก | Expected response | Expected ในฐานข้อมูล |
|---|---|---|---|---|---|
| IT-AD-01 | สร้างแบบฟอร์ม (TC1) | POST /api/forms (3 ช่อง) | FormTemplateRepository.findTopByOrderByVersionDesc, save · FormFieldRepository.save | 201 version = ล่าสุด+1 | form_templates +1 แถว DRAFT, form_fields +3 แถว display_order 1–3 |
| IT-AD-02 | ชื่อช่องซ้ำ (TC2) | POST /api/forms | ไม่ถูกเรียก | 400 | ไม่มีแถวใหม่ |
| IT-AD-03 | แก้ฉบับร่าง (TC1) | PUT /api/forms/{id} เหลือ 1 ช่อง | FormFieldRepository.deleteByFormTemplateId, save | 200 | form_fields ของฉบับนี้เหลือ 1 แถว |
| IT-AD-04 | เปิดใช้งาน (TC1) | POST /api/forms/{id}/publish | FormFieldRepository.countByFormTemplateId, FormTemplateRepository.findByStatus, save | 200 | PUBLISHED มี 1 แถว ฉบับเดิม ARCHIVED published_at ถูกเก็บ |
| IT-AD-05 | เปิดใช้งานเมื่อหมดเขตแล้ว (TC2) | Clock = 1 ธ.ค. 2569 | FormTemplateRepository.findById | 400 | ยังเป็น DRAFT |
| IT-AD-06 | รายการคำร้อง (TC3) | GET /api/applications?status=SUBMITTED | ApplicationRepository.findByStatusOrderBySubmittedAtAsc, StudentRepository, UserRepository, DocumentRepository | 200 เรียงตามเวลายื่น | ข้อมูลไม่เปลี่ยน |
| IT-AD-07 | เจ้าหน้าที่อนุมัติ (TC4) | POST /api/approvals/{id}/decision APPROVE | ApprovalRepository.save, ApplicationRepository.save, StudentRepository.findById (advisor_id), UserRepository.findById, NotificationRepository.save | 200 | approvals +1 (STAFF), applications = UNDER_REVIEW, notifications +1 อาจารย์ที่ปรึกษาของนักศึกษาคนนั้น +1 นักศึกษา |
| IT-AD-08 | ส่งกลับแก้ไข (TC4) | POST decision RETURN + comment | ApprovalRepository.save, ApplicationRepository.save, NotificationRepository.save | 200 | NEED_CORRECTION, แจ้งเตือนนักศึกษามีเหตุผล |
| IT-AD-09 | ตัดสินซ้ำ (TC5) | POST decision คำร้อง UNDER_REVIEW โดยเจ้าหน้าที่ | ApplicationRepository.findById | 409 | ไม่มีแถว approvals เพิ่ม |
| IT-AD-10 | ตัดสินพร้อมกัน 2 request (TC5) | ส่ง APPROVE 2 request พร้อมกัน | ApplicationRepository.save (@Version) | 200 และ 409 | approvals ของ STAFF แค่ 1 แถว |
| IT-AD-11 | ฐานข้อมูลขัดข้องระหว่างบันทึก | ทำให้ NotificationRepository.save โยน error | @Transactional | 500 | ไม่มี approval ค้าง สถานะคำร้องไม่เปลี่ยน (rollback) |
| IT-AD-12 | อ่านแจ้งเตือน | PATCH /api/notifications/{id}/read | NotificationRepository.findById, save | 200 | is_read = true |
| IT-AD-13 | อาจารย์ที่ไม่ใช่อาจารย์ที่ปรึกษา (TC5) | POST decision APPROVE คำร้อง UNDER_REVIEW ของนักศึกษาคนอื่น | StudentRepository.findById | 403 | ไม่มีแถว approvals เพิ่ม สถานะไม่เปลี่ยน |
| IT-AD-14 | ปิดรับก่อนกำหนด (TC6) | POST /api/forms/{id}/close | FormTemplateRepository.findById, save | 200 | deadline = เวลา Clock · status ยัง PUBLISHED · นักศึกษา submit แล้วได้ 409 DEADLINE_PASSED |
| IT-AD-15 | แนบไฟล์ช่องที่ 2 (TC6) | POST /api/requests/{id}/documents?fieldId={id} | FormFieldRepository.findById, DocumentRepository.findByApplicationId, saveAndFlush | 201 | documents +1 แถว form_field_id = id ช่อง · ไฟล์ Transcript เดิมยังอยู่ |
| IT-AD-16 | จำนวนทุกสถานะ (TC7) | GET /api/applications/summary (เจ้าหน้าที่ / อาจารย์) | ApplicationRepository.countByStatus · (อาจารย์) findAll + StudentRepository | 200 | ตัวเลขตรงกับ `SELECT status, COUNT(*) FROM applications GROUP BY status` · อาจารย์นับเฉพาะนักศึกษาของตน |
| IT-AD-17 | ดาวน์โหลด CSV (TC7) | GET /api/applications/export?status=SUBMITTED | เหมือน IT-AD-06 + FormTemplateRepository.findById | 200 text/csv ขึ้นต้น UTF-8 BOM | แถวในไฟล์ = จำนวนคำร้อง SUBMITTED + หัวตาราง 1 แถว |
| IT-AD-18 | ส่งกลับพร้อมช่องที่ต้องแก้ (TC8) | POST decision RETURN + comment + fieldIds | FormFieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc, ApprovalRepository.save, FormResponseRepository.findByApplicationId, AnswerSnapshotRepository.save | 200 · fieldIds ที่ไม่อยู่ในแบบฟอร์ม → 400 | approvals.comment ลงท้าย "ช่องที่ต้องแก้: …" · answer_snapshots +1 แถวต่อคำตอบ |
| IT-AD-19 | อนุมัติหลายใบ (TC8) | POST /api/approvals/bulk {applicationIds: [SUBMITTED, UNDER_REVIEW]} | ApprovalService.decide ทีละใบ (transaction แยก) | 200 approved 1 · skipped 1 | ใบแรก UNDER_REVIEW · ใบที่สองไม่เปลี่ยน ไม่มี approval เพิ่ม |
| IT-AD-20 | แจ้งเจ้าหน้าที่ + เตือนก่อนหมดเขต (TC9) | นักศึกษา submit · แล้วเรียก `CorrectionReminderJob.remindPendingCorrections()` (Clock = 2 วันก่อนหมดเขต) 2 ครั้ง | UserRepository.findByRoleAndActiveTrue, NotificationRepository.existsByApplicationIdAndTypeAndCreatedAtAfter, save | – | notifications APPLICATION_SUBMITTED +1 ต่อเจ้าหน้าที่ · CORRECTION_REMINDER +1 ต่อคำร้อง NEED_CORRECTION (ครั้งที่สองไม่เพิ่ม) |

ความครอบคลุมกับ test case ของ U5
-
| เคส | TC ID |
|---|---|
| TC1 เจ้าหน้าที่สร้างแบบฟอร์มและเปิดใช้งานได้ นักศึกษาใช้ฉบับที่เปิดอยู่ยื่นคำร้อง | IT-AD-01, IT-AD-03, IT-AD-04 |
| TC2 แบบฟอร์มข้อมูลไม่ครบหรือผิด (ไม่มีชื่อ ไม่มีช่อง ชื่อช่องซ้ำ ไม่มีวันหมดเขต) ถูกปฏิเสธ | IT-AD-02, IT-AD-05 |
| TC3 เจ้าหน้าที่เห็นคำร้องที่ยื่นเข้ามาครบ: ข้อมูลนักศึกษา คำตอบทุกช่อง และไฟล์ PDF | IT-AD-06 |
| TC4 เจ้าหน้าที่อนุมัติส่งต่ออาจารย์ หรือส่งกลับแก้ไข/ปฏิเสธพร้อมเหตุผล และนักศึกษาได้รับแจ้งทุกครั้ง | IT-AD-07, IT-AD-08 |
| TC5 การตัดสินที่ไม่ถูกต้องถูกปฏิเสธ: ตัดสินซ้ำ ข้ามขั้น หรือผู้ใช้ที่ไม่มีสิทธิ์ | IT-AD-09, IT-AD-10, IT-AD-13 |
| TC6 แบบฟอร์ม: ช่องไฟล์หลายช่อง · ดูตัวอย่าง · ปิดรับก่อนกำหนด | IT-AD-14, IT-AD-15 |
| TC7 ภาพรวมคำร้อง: จำนวนทุกสถานะ · ป้ายรอนาน / ใกล้หมดเขต · Excel | IT-AD-16, IT-AD-17 |
| TC8 ตรวจเร็วขึ้น: เหตุผลที่ใช้บ่อย · ช่องที่ต้องแก้ · อนุมัติหลายใบ · ค่าเดิมของฉบับแก้ | IT-AD-18, IT-AD-19 |
| TC9 แจ้งเตือนเพิ่ม: แจ้งเจ้าหน้าที่ · เตือนนักศึกษาก่อนหมดเขต | IT-AD-20 |


# ส่วนที่ 3 · Design System Test

Sequence Diagram ที่ใช้
-
![.doc/UserStory/U5/U5-T6 API Sequence diagram.png](<U5-T6 API Sequence diagram.png>)

ทดสอบทั้งระบบตามเส้นทางใน Sequence Diagram: หน้าเว็บจริง + backend จริง + MySQL (`docker compose up -d --build` + Live Server) แล้วตรวจ 3 จุด คือ หน้าจอ, DevTools แท็บ Network และข้อมูลใน MySQL · TC1–TC5 ดู [Task1 · Requirement](T1.md)

เตรียมก่อนทดสอบ
-
1. `docker compose down -v` แล้ว `docker compose up -d --build` (ได้ข้อมูลตั้งต้นจาก `DemoDataSeeder`: แบบฟอร์มเปิดใช้งาน 1 ฉบับ, ใบสมัคร `SUBMITTED` ของสมชายและสมหญิง)
2. บัญชี รหัสผ่าน `1234`: `staff01` · `advisor01` · `6609650001` (สมชาย) · `6609650002` (สมหญิง)
3. ดูข้อมูลใน MySQL: `docker exec -it cs261-db mysql -uroot -proot1234 cs261db`

Test cases
-
| TC ID | เคส | เส้นทางใน Sequence Diagram | Input / ขั้นตอน | Expected result (หน้าจอ / API / ฐานข้อมูล) |
|---|---|---|---|---|
| ST-U5-01 | TC1 | ① สร้างแบบฟอร์ม → เปิดใช้งาน | `staff01` → แบบฟอร์มคำร้อง → สร้างใหม่ 3 ช่อง ใส่วันหมดเขตอนาคต → บันทึกและเปิดใช้งาน → ยืนยัน | `POST /api/forms` 201 แล้ว `POST /publish` 200 · `form_templates` ฉบับใหม่ PUBLISHED ฉบับเดิม ARCHIVED · `form_fields` +3 แถว |
| ST-U5-02 | TC2 | ① publish → alt 400 | สร้างฉบับร่างไม่ใส่วันหมดเขต แล้วกดเปิดใช้งาน | หน้าเว็บแจ้งให้กำหนดวันหมดเขต · ไม่มีแถว PUBLISHED ใหม่ |
| ST-U5-03 | TC2 | ① create → 400 | ยิง Postman `POST /api/forms` ชื่อช่องซ้ำ 2 ช่อง (`X-User-Id: 1`) | 400 VALIDATION_ERROR · `form_templates` ไม่เพิ่ม |
| ST-U5-04 | TC3 | ② รายการ → รายละเอียด | `staff01` เปิดหน้าตรวจใบสมัคร → กดตรวจของสมชาย | `GET ?status=SUBMITTED` 200 (2 รายการ เรียงตามเวลายื่น) · detail 200 คำตอบ 5 ช่อง ไฟล์ PDF |
| ST-U5-05 | TC4 | ③ APPROVE → แจ้งอาจารย์ + นักศึกษา | อนุมัติ ส่งต่ออาจารย์ ใบสมัครของสมชาย | 200 · `applications` = UNDER_REVIEW · `approvals` +1 (STAFF) · `notifications` +1 อาจารย์ +1 สมชาย |
| ST-U5-06 | TC4 | ③ RETURN พร้อมเหตุผล | ส่งกลับแก้ไขใบสมัครของสมหญิง เหตุผล "Transcript ไม่ชัด" | 200 · NEED_CORRECTION · แจ้งเตือนของสมหญิงมีเหตุผล |
| ST-U5-07 | TC4 | ③ alt 400 ไม่มีเหตุผล | เลือกปฏิเสธ ไม่กรอกเหตุผล | หน้าเว็บแจ้ง "ยังบันทึกผลไม่ได้ กรุณากรอกเหตุผล" ไม่มี request · Postman ได้ 400 |
| ST-U5-08 | TC4 | ④ อาจารย์ APPROVE | ออกจากระบบ → `advisor01` → อนุมัติใบสมัครของสมชาย | 200 APPROVED · `approvals` +1 (ADVISOR) · แจ้งเตือนสมชาย "ใบสมัครได้รับการอนุมัติ" |
| ST-U5-09 | TC4 | ⑤ นักศึกษาอ่านแจ้งเตือน | `6609650001` → การแจ้งเตือน → กดรายการบนสุด | ใหม่สุดอยู่บน · `PATCH /read` 200 · `is_read` = 1 |
| ST-U5-10 | TC5 | ③ alt 409 ตัดสินซ้ำ | เปิดใบสมัครเดียวกัน 2 แท็บ (staff01) บันทึกแท็บแรกแล้วบันทึกแท็บที่สอง | แท็บสองแสดงข้อความว่าใบสมัครไม่ได้อยู่ในขั้นที่พิจารณาได้ · `approvals` เพิ่มแค่ 1 แถว |
| ST-U5-11 | TC5 | ข้ามขั้น | Postman: `advisor01` (`X-User-Id: 2`) ตัดสินใบสมัคร `SUBMITTED` | 409 · ไม่มีแถวใหม่ |
| ST-U5-12 | TC5 | ไม่มีสิทธิ์ | `6609650001` พิมพ์ URL `staff-review.html` · Postman `X-User-Id: 3` เรียก `GET /api/applications?status=SUBMITTED` | หน้าเว็บแจ้งว่าหน้านี้สำหรับเจ้าหน้าที่และอาจารย์ · API 403 |
| ST-U5-13 | – | ระบบล่ม | `docker compose stop app` แล้วเปิดหน้าตรวจใบสมัคร (เสร็จแล้ว `docker compose start app`) | แจ้ง "ไม่สามารถเชื่อมต่อระบบได้" ไม่ค้าง |
| ST-U5-14 | TC5 | ④ อาจารย์คนอื่น → 403 | Postman: อาจารย์ที่ไม่ใช่อาจารย์ที่ปรึกษาของสมชาย ตัดสินใบสมัคร `UNDER_REVIEW` ของสมชาย | 403 · ไม่มีแถว approvals ใหม่ · หน้าตรวจของอาจารย์คนนั้นไม่เห็นใบสมัครนี้ |
| ST-U5-15 | TC6 | ① ปิดรับก่อนกำหนด → นักศึกษายื่นไม่ได้ | `staff01` → แบบฟอร์มคำร้อง → "ปิดรับก่อนกำหนด" (กด 2 ครั้ง) → login นักศึกษาที่ยังไม่ยื่น → หน้ายื่นคำร้อง | `POST /close` 200 · `form_templates.deadline` = เวลาที่กด · หน้ายื่นคำร้องขึ้นว่าหมดเขต · submit ได้ 409 DEADLINE_PASSED |
| ST-U5-16 | TC6 | U3 ยื่น → ② ช่องไฟล์หลายช่อง | สร้างแบบฟอร์มใหม่ที่มีช่องแนบไฟล์ 2 ช่อง → เปิดใช้งาน → นักศึกษาแนบไฟล์ทั้ง 2 ช่องแล้วส่ง → เจ้าหน้าที่เปิดรายละเอียด | `documents` +2 แถว (`form_field_id` ว่าง 1 แถว + id ช่องที่ 2) · หน้าตรวจเห็นชื่อไฟล์ทั้ง 2 ช่อง · ถ้าไม่แนบช่องที่ 2 ได้ 400 MISSING_DOCUMENT |
| ST-U5-17 | TC7 | ② รายการ + summary + export | `staff01` เปิดหน้าตรวจ → ดูตัวเลขบนแท็บ → "ดาวน์โหลด Excel" | `GET /summary` 200 ตัวเลขตรงกับ `SELECT status, COUNT(*) FROM applications GROUP BY status` · ไฟล์ CSV เปิดใน Excel ได้ จำนวนแถวตรงกับแท็บ |
| ST-U5-18 | TC7 | ② ป้ายเตือน | `UPDATE applications SET submitted_at = NOW() - INTERVAL 4 DAY WHERE id = <สมหญิง>` แล้วโหลดหน้าตรวจใหม่ | `attention = OVERDUE` · แถวสมหญิงอยู่บนสุดมีป้าย "รอนาน" |
| ST-U5-19 | TC8 | ③ RETURN + fieldIds → snapshot · U3 ส่งใหม่ → ② diff | ส่งกลับใบสมัครของสมหญิง เลือกเหตุผลที่ใช้บ่อย ติ๊ก "เบอร์โทรศัพท์" → login สมหญิง แก้เบอร์แล้วส่งใหม่ → `staff01` เปิดรายละเอียด | `approvals.comment` ลงท้าย "ช่องที่ต้องแก้: เบอร์โทรศัพท์" · `answer_snapshots` มีแถวของคำร้องนี้ · หน้าตรวจเห็นป้าย "แก้แล้ว" และเบอร์เดิมขีดฆ่า · เจ้าหน้าที่ได้แจ้งเตือน "มีคำร้องที่แก้ไขแล้วส่งกลับมา" |
| ST-U5-20 | TC8 | ③ bulk | ใบสมัคร SUBMITTED 2 ใบ → ติ๊กทั้งหมด → "อนุมัติที่เลือก" 2 ครั้ง | `POST /api/approvals/bulk` 200 · ทั้ง 2 ใบ UNDER_REVIEW · `approvals` +2 · แจ้งเตือนอาจารย์ที่ปรึกษาและนักศึกษาครบ |
| ST-U5-21 | TC9 | U3 submit → แจ้งเจ้าหน้าที่ · งาน 08:00 → เตือนนักศึกษา | นักศึกษายื่นคำร้องใหม่ → `staff01` เปิดการแจ้งเตือน · ตั้งวันหมดเขตให้เหลือ 1 วัน มีคำร้อง NEED_CORRECTION แล้วรอเวลา 08:00 (หรือรันด้วย `APP_REMINDER_CRON` ที่ใกล้เวลาปัจจุบัน) | เจ้าหน้าที่เห็น "มีคำร้องใหม่รอตรวจ" · นักศึกษาเห็น "ใกล้หมดเขตส่งคำร้องที่ต้องแก้ไข" 1 ครั้งต่อวัน · Email เข้า Mailpit |

ความครอบคลุม
-
| เคส | TC ID |
|---|---|
| TC1 | ST-U5-01 |
| TC2 | ST-U5-02, ST-U5-03 |
| TC3 | ST-U5-04 |
| TC4 | ST-U5-05, ST-U5-06, ST-U5-07, ST-U5-08, ST-U5-09 |
| TC5 | ST-U5-10, ST-U5-11, ST-U5-12, ST-U5-14 |
| TC6 | ST-U5-15, ST-U5-16 |
| TC7 | ST-U5-17, ST-U5-18 |
| TC8 | ST-U5-19, ST-U5-20 |
| TC9 | ST-U5-19, ST-U5-21 |

ผลการรันบันทึกใน ส่วน "ผล System Test" ในไฟล์นี้


# ส่วนที่ 4 · Design UAT

-
- **As a** เจ้าหน้าที่ฝ่ายสหกิจศึกษา
- **I want to** สร้าง template แบบฟอร์มคำร้อง และรับใบสมัครที่นักศึกษายื่นเข้ามา เพื่อตรวจ แล้วอนุมัติส่งต่ออาจารย์ ส่งกลับแก้ไข หรือปฏิเสธ
- **So that** ใบสมัครทุกใบถูกตรวจครบตามขั้นตอน อาจารย์พิจารณาต่อได้ทันที และนักศึกษารู้ผลทุกครั้งโดยไม่ต้องมาถามเอง

ผู้ทดสอบ
-
- ผู้รับบทเจ้าหน้าที่ 1–2 คน (บัญชี `staff01`) · ผู้รับบทอาจารย์ 1 คน (`advisor01`) · นักศึกษา 2–3 คน (`6609650001`, `6609650002`) รหัสผ่าน `1234`
- ทีมอธิบายแค่สถานการณ์ (Given) **ไม่บอกว่าต้องกดปุ่มไหน** แล้วสังเกตว่าผู้ทดสอบทำได้เองหรือไม่ จับเวลา และจดคำพูด / ปัญหาที่เจอ
- เริ่มทุกรอบด้วย `docker compose down -v` แล้ว `docker compose up -d --build`

Test cases
-
| TC ID | เคส | Given | When | Then | เกณฑ์ที่ user ยอมรับได้ |
|---|---|---|---|---|---|
| UAT-U5-01 | TC1 | เจ้าหน้าที่ต้องเปิดรับคำร้องรอบใหม่ | สร้างแบบฟอร์ม 3 ช่อง กำหนดวันหมดเขต แล้วเปิดใช้งาน | แบบฟอร์มใหม่เปิดใช้งาน ฉบับเดิมเลิกใช้ | ทำเองได้ภายใน 3 นาที ไม่ต้องถามทีม |
| UAT-U5-02 | TC2 | เจ้าหน้าที่ลืมใส่วันหมดเขต / ตั้งชื่อช่องซ้ำ | กดบันทึกหรือเปิดใช้งาน | ระบบบอกว่าผิดตรงไหน | อ่านข้อความแล้วแก้ได้ทันที ข้อมูลที่กรอกไม่หาย |
| UAT-U5-03 | TC3 | มีใบสมัครรอตรวจ 2 ใบ | เปิดดูใบสมัครของสมชาย | เห็นข้อมูลนักศึกษา คำตอบทุกช่อง และไฟล์ PDF ที่แนบ (กด "ดูไฟล์" เปิดได้) | ข้อมูลครบพอตัดสินได้ โดยไม่ต้องเปิดระบบอื่น |
| UAT-U5-04 | TC4 | ใบสมัครของสมชายถูกต้อง | อนุมัติส่งต่ออาจารย์ | เห็นว่าบันทึกสำเร็จและส่งต่อให้อาจารย์แล้ว ใบสมัครหายจากรายการรอตรวจ | มั่นใจว่าบันทึกแล้ว ไม่ต้องกดซ้ำ |
| UAT-U5-05 | TC4 | ใบสมัครของสมหญิงแนบไฟล์ไม่ชัด | ส่งกลับแก้ไขพร้อมเหตุผล | สมหญิงเห็นแจ้งเตือนพร้อมเหตุผล | นักศึกษาอ่านแล้วรู้ว่าต้องแก้อะไร |
| UAT-U5-06 | TC4 | อาจารย์มีใบสมัครรอพิจารณา | อาจารย์อนุมัติ | สมชายเห็นแจ้งเตือน "ใบสมัครได้รับการอนุมัติ" | อาจารย์หาใบสมัครที่ต้องพิจารณาเจอเอง ภายใน 1 นาที |
| UAT-U5-07 | TC5 | เจ้าหน้าที่จะส่งกลับแก้ไขแต่ลืมใส่เหตุผล | กดบันทึก | ระบบไม่ยอมบันทึก และชี้ช่องเหตุผล | เข้าใจทันทีว่าต้องกรอกเหตุผล |
| UAT-U5-08 | TC5 | เจ้าหน้าที่ 2 คนเปิดใบสมัครเดียวกัน | กดบันทึกทั้งคู่ | คนที่สองได้รับแจ้งว่ามีคนตัดสินไปแล้ว | ไม่มีผลซ้ำ / ขัดกันในระบบ |
| UAT-U5-09 | – | นักศึกษา login | เปิดเมนูการแจ้งเตือน | เห็นผลล่าสุดอยู่บนสุด รายการที่ยังไม่อ่านมีจุดสีส้ม | หาผลการพิจารณาของตัวเองเจอภายใน 30 วินาที |
| UAT-U5-10 | TC6 | เจ้าหน้าที่อยากให้นักศึกษาแนบหนังสือรับรองเพิ่มจาก Transcript | เพิ่มช่องแนบไฟล์ในแบบฟอร์ม แล้วดูตัวอย่างก่อนเปิดใช้งาน | เห็นหน้าตาแบบที่นักศึกษาจะเห็น มีช่องแนบไฟล์ 2 ช่อง | ดูตัวอย่างแล้วมั่นใจก่อนเปิดใช้งาน ไม่ต้องสลับไปบัญชีนักศึกษา |
| UAT-U5-11 | TC6 | ต้องหยุดรับคำร้องก่อนวันหมดเขตเพราะจำนวนเต็ม | ปิดรับก่อนกำหนด | นักศึกษายื่นไม่ได้อีก คำร้องที่ส่งแล้วยังตรวจต่อได้ | ทำเองได้ภายใน 30 วินาที |
| UAT-U5-12 | TC7 | มีคำร้องหลายสถานะ บางใบรอนาน | เปิดหน้าตรวจใบสมัคร | เห็นจำนวนทุกสถานะ และใบที่รอนาน / ใกล้หมดเขตอยู่บนสุดพร้อมป้าย | รู้ว่าควรตรวจใบไหนก่อนโดยไม่ต้องไล่ดูทีละใบ |
| UAT-U5-13 | TC7 | ต้องส่งรายชื่อผู้ยื่นให้หัวหน้า | ดาวน์โหลด Excel | ได้ไฟล์ที่เปิดใน Excel ภาษาไทยอ่านได้ | ส่งต่อได้ทันทีไม่ต้องพิมพ์ใหม่ |
| UAT-U5-14 | TC8 | ใบสมัครกรอกเบอร์โทรผิด | ส่งกลับแก้ไข เลือกเหตุผลที่ใช้บ่อย และติ๊กช่องเบอร์โทร | นักศึกษาเห็นว่าต้องแก้ช่องไหน · ส่งฉบับแก้มาแล้วเจ้าหน้าที่เห็นค่าเดิมเทียบกัน | ตรวจฉบับแก้ได้โดยไม่ต้องจำว่าเดิมผิดตรงไหน |
| UAT-U5-15 | TC8 | มีใบสมัครถูกต้องหลายใบ | ติ๊กเลือกแล้วอนุมัติพร้อมกัน | ทุกใบส่งต่ออาจารย์ · ถ้ามีใบที่อนุมัติไม่ได้ ระบบบอกเลขที่และเหตุผล | อนุมัติ 5 ใบได้ภายใน 1 นาที |
| UAT-U5-16 | TC9 | นักศึกษายื่นคำร้องใหม่ / นักศึกษาที่ถูกส่งกลับยังไม่แก้และใกล้หมดเขต | รอการแจ้งเตือน | เจ้าหน้าที่เห็นแจ้งเตือนคำร้องใหม่ · นักศึกษาได้รับเตือนก่อนหมดเขต | ไม่พลาดคำร้องใหม่ และนักศึกษาไม่ลืมส่งฉบับแก้ |

เกณฑ์ผ่าน UAT โดยรวม
-
- UAT-U5-01 ถึง UAT-U5-08 และ UAT-U5-10 ถึง UAT-U5-16 ต้องผ่านทุกข้อ (เคสบังคับตาม user story)
- ผู้ทดสอบอย่างน้อย 80% ทำ UAT-U5-03, 04, 06 ได้เองโดยไม่ต้องถามทีม
- ไม่มีปัญหาระดับสูง (ทำงานต่อไม่ได้ / ข้อมูลผิด) ค้างอยู่ · บันทึกผลใน ส่วน "ผล UAT" ในไฟล์นี้


# ส่วนที่ 5 · ผล Integration Test UI–API

Artefact: รายงานผล Integration Test · อ้างอิง test cases: ส่วน "Design Integration Test UI–API" ในไฟล์นี้

สภาพแวดล้อมที่ใช้รัน
-
| รายการ | ค่า |
|---|---|
| วันที่รัน | 27 ต.ค. 2569 00:04–00:06 |
| ผู้รัน | Claude (รันอัตโนมัติด้วย Playwright) |
| Branch / commit | `Feature-/-U5-Staff` / `61a6533` (โค้ด 13 ข้อของ US5 ยังไม่อยู่ใน `Develop`) |
| Backend | Spring Boot (docker compose) http://localhost:8080 |
| ฐานข้อมูล | MySQL 8.4 (docker compose) เริ่มจาก `docker compose down -v` ให้ `DemoDataSeeder` ใส่ข้อมูลตั้งต้น |
| Frontend | `frontend/login.html` เปิดด้วย Live Server (ไม่ใช้ `?demo=1`) |
| บัญชี | `staff01`, `advisor01`, `6609650001` รหัสผ่าน `1234` |
| เบราว์เซอร์ | Chromium 141 (Playwright 1.56) |

สภาพแวดล้อมจริงตอนรัน: รันบนเครื่องที่ไม่มี Docker: backend รันด้วย `java` (JDK 21) จากโค้ด commit นี้ · ฐานข้อมูล H2 2.4 โหมด MySQL แยกเป็นอีก process (แทน MySQL 8.4) เริ่มจากฐานข้อมูลว่างให้ `DemoDataSeeder` ใส่ข้อมูลตั้งต้น · หน้าเว็บเปิดผ่าน http server พอร์ต 8081 · Email ส่งเข้า SMTP จำลอง (aiosmtpd) แทน Mailpit · คลิกหน้าเว็บและเก็บ request ด้วย Playwright 1.56 (Chromium 141) · ข้อที่บอกว่า "Postman" ยิง request ตรงด้วย `fetch` จากสคริปต์ · IT-UA-12 หยุด process ของ backend แทน `docker compose stop app`

ขั้นตอนการรัน
-
1. `docker compose down -v` แล้ว `docker compose up -d --build`
2. เปิด `frontend/login.html` แล้ว login ตามผู้ใช้ที่ test case กำหนด
3. เปิด DevTools แท็บ Network ไว้ตลอด ดู method, URL, header `X-User-Id`, status และ response
4. ทำตาม Input ของแต่ละ test case แล้วบันทึกผลในตาราง (✅ ผ่าน / ❌ ไม่ผ่าน)
5. ถ้าไม่ผ่าน แคปหน้าเว็บและแท็บ Network เก็บไว้ แล้วจดใน "ปัญหาที่พบ"

รายงานผล
-
| TC ID | Scenario | Expected | Actual | ผล |
|---|---|---|---|---|
| IT-UA-01 | สร้างแบบฟอร์ม 3 ช่อง แล้วบันทึกฉบับร่าง (TC1) | `POST /api/forms` 201 DRAFT · กลับหน้ารายการ เห็นฉบับใหม่ | POST /api/forms → 201 · สถานะ DRAFT · ข้อความ "บันทึกฉบับร่าง "IT-UA แบบฟอร์ม 3 ช่อง" แล้ว" · รายการมีแถว "2 IT-UA แบบฟอร์ม 3 ช่อง 3 ช่อง 31 ธ.ค. 2569 23:59 ฉบับร่าง แก้ไข เปิดใช้งาน" | ✅ |
| IT-UA-02 | เปิดใช้งาน กด 2 ครั้ง (TC1) | `POST /api/forms/{id}/publish` 200 · ฉบับใหม่เปิดใช้งาน ฉบับเดิมเลิกใช้ | กดครั้งแรกส่ง POST 0 ครั้ง · ครั้งที่สอง POST /api/forms/2/publish → 200 · 2 เปิดใช้งาน · 1 เลิกใช้ | ✅ |
| IT-UA-03 | ชื่อช่องซ้ำหลุดไปถึง server (TC2) | 400 VALIDATION_ERROR · แสดงข้อความจาก server ข้อมูลที่กรอกไม่หาย | POST /api/forms → 400 · ข้อความ "ชื่อช่อง "อีเมล" ซ้ำกับช่องอื่น" · ชื่อแบบฟอร์ม "IT-UA-03 ชื่อช่องซ้ำ" และช่อง ["อีเมล","อีเมล"] ยังอยู่ | ✅ |
| IT-UA-04 | แก้ฉบับที่เปิดใช้งาน (TC2) | Postman `PUT /api/forms/{id}` ได้ 409 · หน้าเว็บไม่มีปุ่มแก้ไขฉบับนี้ | PUT /api/forms/2 (เหมือน Postman) → 409 INVALID_STATUS · ปุ่มของฉบับนี้บนหน้าเว็บ: ดู, สร้างฉบับใหม่จากฉบับนี้, ปิดรับก่อนกำหนด | ✅ |
| IT-UA-05 | เจ้าหน้าที่เปิดหน้าตรวจ (TC3) | `GET /api/applications?status=SUBMITTED` 200 · ตาราง 2 แถว ชื่อและเอกสารตรง | GET /api/applications?status=SUBMITTED → 200 X-User-Id 1 · ตาราง 2 แถว: 6609650001 สมชาย ใจดี · 1 ไฟล์ / 6609650002 สมหญิง รักเรียน · 1 ไฟล์ | ✅ |
| IT-UA-06 | กดตรวจใบสมัครแรก (TC3) | `GET /api/applications/{id}/detail` 200 · คำตอบครบทุกช่อง และไฟล์ PDF | GET /api/applications/1/detail → 200 · คำตอบ 5 ช่อง · เอกสาร "PDF transcript.pdf อัปโหลดแล้ว ดูไฟล์" | ✅ |
| IT-UA-07 | อนุมัติส่งต่ออาจารย์ (TC4) | `POST /api/approvals/{id}/decision` `{decision: APPROVE}` 200 UNDER_REVIEW · หน้าสำเร็จ ใบสมัครหายจากรายการ | POST /api/approvals/1/decision {"decision":"APPROVE","comment":null} → 200 · หน้าสำเร็จ "สถานะใหม่: รออาจารย์พิจารณา (UNDER_REVIEW) ระบบส่งการแจ้งเตือนถึงอาจารย์แล้ว" · รายการรอตรวจเหลือ 1 แถว (สมหญิง) | ✅ |
| IT-UA-08 | ส่งกลับแก้ไขพร้อมเหตุผล (TC4) | 200 NEED_CORRECTION · request มี `comment` | POST /api/approvals/2/decision {"decision":"RETURN","comment":"Transcript ไม่ชัด กรุณาแนบใหม่"} → 200 · หน้าสำเร็จ "สถานะใหม่: ส่งกลับแก้ไข (NEED_CORRECTION) ระบบส่งการแจ้งเตือนถึงนักศึกษาแล้ว" | ✅ |
| IT-UA-09 | นักศึกษาเปิดการแจ้งเตือน (TC4) | `GET /api/notifications` 200 · แจ้งเตือนล่าสุดอยู่บนสุด | GET /api/notifications → 200 · รายการบนสุด "ใบสมัครถูกส่งกลับให้แก้ไข กรุณาแก้ไขใบสมัครแล้วส่งใหม่ก่อนหมดเขต. เหตุผล: Transcript ไม่ชัด กรุณาแนบใหม่ 27 ต.ค. 2569 00:05" | ✅ |
| IT-UA-10 | ตัดสินซ้ำจาก 2 แท็บ (TC5) | ครั้งแรก 200 ครั้งที่สอง 409 · แท็บที่สองแสดงข้อความว่าตัดสินไปแล้ว | แท็บแรก 200 แท็บที่สอง 409 · แท็บที่สองแสดง "คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: APPROVED)" | ✅ |
| IT-UA-11 | อาจารย์อนุมัติ (TC4) | 200 APPROVED · สถานะใหม่ อนุมัติแล้ว | POST /api/approvals/1/decision → 200 X-User-Id 2 · "สถานะใหม่: อนุมัติแล้ว (APPROVED) ระบบส่งการแจ้งเตือนถึงนักศึกษาแล้ว" | ✅ |
| IT-UA-12 | ปิด backend (`docker compose stop app`) แล้วเปิดหน้า | แสดง "ไม่สามารถเชื่อมต่อระบบได้" | หน้าเว็บแสดง "ไม่สามารถเชื่อมต่อระบบได้ กรุณาเปิด backend (docker compose up -d) แล้วลองใหม่ดูด้วยข้อมูลตัวอย่าง" | ✅ |
| IT-UA-13 | ดูตัวอย่างหน้านักศึกษา (TC6) | ไม่มี request · เห็นช่องไฟล์เป็นปุ่มเลือกไฟล์ ช่องบังคับมี * | request ที่ส่งออก 0 · ช่องเลือกไฟล์ 1 ช่อง · ตัวอย่าง "ตัวอย่างหน้ายื่นคำร้องของนักศึกษา (ยังไม่บันทึก) IT-UA-13 ตัวอย่าง หมดเขตยื่นคำร้อง 31 ธ.ค. 2569 23:59 ชื่อ-นามสกุล * หน" | ✅ |
| IT-UA-14 | ปิดรับก่อนกำหนด กด 2 ครั้ง (TC6) | `POST /api/forms/{id}/close` 200 · หน้ายื่นคำร้องขึ้นว่าหมดเขต | กดครั้งแรก POST 0 · ครั้งที่สอง POST /api/forms/2/close → 200 · deadline 2026-10-27 00:05:44.921318 · "ปิดรับคำร้องของแบบฟอร์ม "IT-UA แบบฟอร์ม 3 ช่อง" แล้ว นักศึกษายื่นหรือส่งฉบับแก้ไม่ได้อีก" · ปุ่มปิดรับเหลือ 0 · หน้ายื่นคำร้องนักศึกษา "เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้ (หมดเขต 27 ต.ค. 2569 00:05)" | ✅ |
| IT-UA-15 | แนบไฟล์ 2 ช่องแล้วส่ง (TC6) | `POST …/documents` 201 · `POST …/documents?fieldId=` 201 · submit 200 · หน้าตรวจเห็นไฟล์ทั้ง 2 ช่อง | /api/requests/3/documents → 201 · /api/requests/3/documents?fieldId=14 → 201 · submit → 200 "ยื่นคำร้องสำเร็จ เลขที่คำร้อง 3 · สถานะ: ยื่นแล้ว รอเจ้าหน้าที่ตรวจสอบ" · หน้าตรวจเจ้าหน้าที่: "PDF transcript.pdf อัปโหลดแล้ว ดูไฟล์ PDF certificate.pdf อัปโหลดแล้ว ดูไฟล์" | ✅ |
| IT-UA-16 | แนบแค่ Transcript แล้วส่ง (TC6) | submit 400 MISSING_DOCUMENT · ข้อความบอกชื่อช่องที่ขาด | POST /api/requests/3/submit → 400 · หน้าเว็บ "ผลการตรวจสอบคำร้อง คำร้องไม่ผ่านการตรวจสอบ 1 รายการ กรุณาแก้ไขข้อมูล ✓ ข้อมูลส่วนตัวครบถ้วน ชื่อ เบอร์โทร อีเมล และที่อยู่ ✕ เอกสาร Transcript กรุณาแนบไฟล์ หนัง" | ✅ |
| IT-UA-17 | จำนวนทุกสถานะ (TC7) | `GET /api/applications/summary` 200 · ตัวเลขบนทุกแท็บตรงกับฐานข้อมูล | ลำดับ request: /api/applications?status=SUBMITTED → /api/applications/summary · ตัวเลขบนแท็บ {"SUBMITTED":"1","NEED_CORRECTION":"1","UNDER_REVIEW":"1","REJECTED":""} · DB {"NEED_CORRECTION":"1","SUBMITTED":"1","UNDER_REVIEW":"1"} | ✅ |
| IT-UA-18 | คำร้องรอนาน (TC7) | `attention: OVERDUE` · แถวอยู่บนสุดมีป้าย "รอนาน" | attention จาก API: สมปอง ตั้งใจ=OVERDUE · แถวบนสุด "6609650003 สมปอง ตั้งใจ 23 ต.ค. 2569 00:06 2 ไฟล์ รอตรวจรอนาน ตรวจ" ไฮไลต์ true | ✅ |
| IT-UA-19 | ดาวน์โหลด Excel (TC7) | `GET /api/applications/export?status=SUBMITTED` 200 text/csv · เปิดใน Excel ภาษาไทยอ่านได้ | GET /api/applications/export?status=SUBMITTED → 200 · ไฟล์ applications-SUBMITTED.csv · ขึ้นต้น UTF-8 BOM true · หัวตาราง "เลขที่คำร้อง,รหัสนักศึกษา,ชื่อ-นามสกุล,สถานะ,วันที่ส่ง,ผู้พิจารณาล่าสุด,วันที่พิจารณา,ไฟล์แนบ,หมายเหตุ" | ✅ |
| IT-UA-20 | ส่งกลับพร้อมช่องที่ต้องแก้ (TC8) | body มี `fieldIds` · 200 · ประวัติมี "ช่องที่ต้องแก้: …" | POST /api/approvals/3/decision {"decision":"RETURN","comment":"Transcript ไม่ชัดเจน กรุณาแนบไฟล์ใหม่","fieldIds":[10]} → 200 · ประวัติ "✓ ยื่นใบสมัคร 23 ต.ค. 2569 00:06 · สมปอง ตั้งใจ ✓ เจ้าหน้าที่: ส่งกลับแก้ไข 27 ต.ค. 2569 00:06 · สมศรี ทำงานดี · Transcript ไม่ชัดเจน กรุณาแนบไฟล์ใหม่ ช่องที่ต้องแก้: เบอร์โทรศัพท์" | ✅ |
| IT-UA-21 | อนุมัติหลายใบ (TC8) | `POST /api/approvals/bulk` 200 · แจ้งจำนวนที่อนุมัติ / ใบที่ถูกข้าม | เลือกแล้ว 2 รายการ · ระหว่างนั้นอีกแท็บส่งกลับใบ 2 (200) · POST /api/approvals/bulk {"applicationIds":[3,2]} → 200 · ข้อความ "อนุมัติแล้ว 1 รายการ · ข้าม 1 รายการ: เลขที่ 2 (คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: NEED_CORRECTION))" · โหลดรายการใหม่ true | ✅ |
| IT-UA-22 | ดูค่าเดิมของฉบับแก้ (TC8) | detail มี `previousValue` · ป้าย "แก้แล้ว" ค่าเดิมขีดฆ่า | GET /api/applications/3/detail → 200 · previousValue: เบอร์โทรศัพท์: 0891112222 → 0899998888 · หน้าเว็บ: "เบอร์โทรศัพท์ แก้แล้ว 0899998888 เดิม: 0891112222" | ✅ |
| IT-UA-23 | เจ้าหน้าที่เปิดการแจ้งเตือน (TC9) | เห็น "มีคำร้องที่แก้ไขแล้วส่งกลับมา" | GET /api/notifications → 200 · เจ้าหน้าที่เห็น: มีคำร้องที่แก้ไขแล้วส่งกลับมา คำร้องเลขที่ 3 ถูกแก้ไขตามที่ส่งกลับและส่งเข้ามาใหม่แล้ว 27 ต.ค. 2569 00:06 / มีคำร้องใหม่รอตรวจ คำร้องเลขที่ 3 ถูกส่งเข้ามาในระบบ รอเจ้าหน้าที่ตรวจสอบ 27 ต.ค. 2569 00:06 | ✅ |

สรุปผล
-
| ผ่าน | ไม่ผ่าน | ทั้งหมด |
|---|---|---|
| 23 | 0 | 23 |

ปัญหาที่พบ
-
| TC ID | อาการ | สาเหตุ | วิธีแก้ / commit | Retest |
|---|---|---|---|---|
| – | ไม่มีข้อที่ไม่ผ่าน | – | – | – |

ข้อสังเกตจากการรัน
-
- ลำดับที่รันจริง: 01–09 → 13 → 14 → 16 → 15 → 17–20 → 22 → 23 → 21 → 11 → 10 → 12 (ต้องมีคำร้องในสถานะที่แต่ละข้อใช้ · 16 ทำก่อน 15 เพราะใช้คำร้องเดียวกัน)
- IT-UA-21 ทำให้มีใบที่ถูกข้ามจริง: หลังติ๊ก 2 ใบ เจ้าหน้าที่อีกแท็บส่งกลับใบหนึ่งไปก่อน (`POST decision` 200) แล้วจึงกดอนุมัติที่เลือก
- IT-UA-10 ใช้อาจารย์ 2 แท็บกับคำร้องเดียวกัน (ตอนนั้นไม่มีคำร้อง SUBMITTED เหลือ) ผลแท็บที่สองตรงตาม Expected
- นักศึกษาที่ใช้ยื่นคำร้องคือ `6609650003` (สมปอง) เพราะ `6609650004` มีผลตรวจคุณสมบัติไม่ผ่านใน `DemoDataSeeder` จึงยื่นไม่ได้ (403 NOT_ELIGIBLE ตามที่ U3 ออกแบบ)


# ส่วนที่ 6 · ผล Integration Test API–DAO

Artefact: รายงานผล Integration Test · อ้างอิง test cases: ส่วน "Design Integration Test API–DAO" ในไฟล์นี้

สภาพแวดล้อมที่ใช้รัน
-
| รายการ | ค่า |
|---|---|
| วันที่รัน | 27 ต.ค. 2569 00:15–00:18 |
| ผู้รัน | Claude (รันอัตโนมัติด้วย Playwright) |
| Branch / commit | `Feature-/-U5-Staff` / `61a6533` (โค้ด 13 ข้อของ US5 ยังไม่อยู่ใน `Develop`) |
| Backend | Spring Boot (docker compose) http://localhost:8080 |
| ฐานข้อมูล | MySQL 8.4 (docker compose) เริ่มจาก `docker compose down -v` |
| เครื่องมือ | Postman: import `.doc/postman/U5-Staff.postman_collection.json` · ดูข้อมูล: `docker exec -it cs261-db mysql -uroot -proot1234 cs261db` |
| ผู้ใช้ (header `X-User-Id`) | 1 = staff01 · 2 = advisor01 · 3 = สมชาย · 4 = สมหญิง (จาก `DemoDataSeeder`) |

สภาพแวดล้อมจริงตอนรัน: รันบนเครื่องที่ไม่มี Docker: backend รันด้วย `java` (JDK 21) จากโค้ด commit นี้ · ฐานข้อมูล H2 2.4 โหมด MySQL แยกเป็นอีก process (แทน MySQL 8.4) เริ่มจากฐานข้อมูลว่างให้ `DemoDataSeeder` ใส่ข้อมูลตั้งต้น · หน้าเว็บเปิดผ่าน http server พอร์ต 8081 · Email ส่งเข้า SMTP จำลอง (aiosmtpd) แทน Mailpit · คลิกหน้าเว็บและเก็บ request ด้วย Playwright 1.56 (Chromium 141) · ใช้สคริปต์ Python ยิง request แทน Postman และอ่านฐานข้อมูลด้วย H2 Shell ก่อน/หลังทุกข้อ · IT-AD-11 หยุด process ของ H2 server แทน `docker compose stop db` · IT-AD-20 เปิด backend ด้วย `APP_REMINDER_CRON="0 * * * * *"` (รันทุกนาที)

ขั้นตอนการรัน
-
1. `docker compose down -v` แล้ว `docker compose up -d --build`
2. ก่อนแต่ละ test case รัน SQL "ก่อน" เพื่อจดจำนวนแถว แล้วส่ง request ใน Postman
3. รัน SQL "หลัง" แล้วเทียบกับ Expected ในฐานข้อมูล บันทึกผลในตาราง (✅ / ❌)

SQL ที่ใช้ตรวจ
-
```sql
SELECT id, name, version, status, deadline FROM form_templates ORDER BY id;
SELECT form_template_id, COUNT(*) FROM form_fields GROUP BY form_template_id;
SELECT id, status, version FROM applications;
SELECT application_id, approval_level, decision, comment FROM approvals ORDER BY id;
SELECT user_id, title, is_read FROM notifications ORDER BY id;
```

รายงานผล
-
| TC ID | Scenario | Request | Expected response | Expected ในฐานข้อมูล | Actual | ผล |
|---|---|---|---|---|---|---|
| IT-AD-01 | สร้างแบบฟอร์ม (TC1) | POST /api/forms (3 ช่อง) | 201 version = ล่าสุด+1 | form_templates +1 DRAFT · form_fields +3 (display_order 1–3) | 201 version=2 (เดิมสูงสุด 1) · form_templates +1 สถานะ DRAFT · form_fields 3 แถว display_order 1,2,3 | ✅ |
| IT-AD-02 | ชื่อช่องซ้ำ (TC2) | POST /api/forms | 400 | ไม่มีแถวใหม่ | 400 VALIDATION_ERROR · form_templates 2→2 | ✅ |
| IT-AD-03 | แก้ฉบับร่าง (TC1) | PUT /api/forms/{id} เหลือ 1 ช่อง | 200 | form_fields ของฉบับนี้เหลือ 1 แถว | 200 · form_fields ของฉบับนี้ 1 แถว | ✅ |
| IT-AD-04 | เปิดใช้งาน (TC1) | POST /api/forms/{id}/publish | 200 | PUBLISHED มีแถวเดียว · ฉบับเดิม ARCHIVED · published_at มีค่า | 200 · PUBLISHED 1 แถว (id 2) · ฉบับเดิม id 1 = ARCHIVED · published_at = 2026-10-27 00:16:29.592212 | ✅ |
| IT-AD-05 | เปิดใช้งานเมื่อหมดเขตแล้ว (TC2) | `UPDATE form_templates SET deadline = '2020-01-01' WHERE id = {ฉบับร่าง};` แล้ว publish | 400 | ยังเป็น DRAFT | 400 VALIDATION_ERROR · สถานะยัง DRAFT | ✅ |
| IT-AD-06 | รายการคำร้อง (TC3) | GET /api/applications?status=SUBMITTED | 200 เรียงตามเวลายื่น | ข้อมูลไม่เปลี่ยน | 200 · 2 รายการ เรียง submittedAt ['2026-10-25T00:16:26.864265', '2026-10-26T00:16:26.911661'] · ข้อมูลไม่เปลี่ยน | ✅ |
| IT-AD-07 | เจ้าหน้าที่อนุมัติ (TC4) | POST /api/approvals/{id}/decision APPROVE (`X-User-Id: 1`) | 200 | approvals +1 (STAFF) · applications = UNDER_REVIEW · notifications +1 อาจารย์ +1 นักศึกษา | 200 · approvals +1 (STAFF) · applications = UNDER_REVIEW · notifications อาจารย์ +1 นักศึกษา +1 | ✅ |
| IT-AD-08 | ส่งกลับแก้ไข (TC4) | POST decision RETURN + comment | 200 | NEED_CORRECTION · แจ้งเตือนนักศึกษามีเหตุผล | 200 · applications = NEED_CORRECTION · แจ้งเตือนนักศึกษา: "กรุณาแก้ไขใบสมัครแล้วส่งใหม่ก่อนหมดเขต. เหตุผล: Transcript ไม่ชัด กรุณาแนบใหม่" | ✅ |
| IT-AD-09 | ตัดสินซ้ำ (TC5) | POST decision ใบสมัคร UNDER_REVIEW โดยเจ้าหน้าที่ | 409 | approvals ไม่เพิ่ม | 409 INVALID_STATUS · approvals 2→2 | ✅ |
| IT-AD-10 | ตัดสินพร้อมกัน 2 request (TC5) | ส่ง APPROVE 2 request พร้อมกัน (คำสั่งด้านล่าง) | 200 และ 409 | approvals ของ STAFF แค่ 1 แถว | เตรียม: สมหญิงส่งฉบับแก้ (PUT form 200, submit 200) → SUBMITTED · ผล [200, 409] · approvals STAFF APPROVE ของใบนี้ 1 แถว | ✅ |
| IT-AD-11 | ฐานข้อมูลขัดข้องระหว่างบันทึก | `docker compose stop db` แล้ว POST decision จากนั้น `docker compose start db` | 500 | สถานะใบสมัครไม่เปลี่ยน ไม่มี approval ค้าง | ระหว่าง DB ปิดได้ 500 None · หลังเปิดคืน สถานะยัง UNDER_REVIEW · approvals 3→3 | ✅ |
| IT-AD-12 | อ่านแจ้งเตือน | PATCH /api/notifications/{id}/read (`X-User-Id` เจ้าของ) | 200 | is_read = 1 | 200 · is_read = TRUE | ✅ |
| IT-AD-13 | อาจารย์ที่ไม่ใช่อาจารย์ที่ปรึกษา (TC5) | `UPDATE students SET advisor_id = NULL WHERE id = {นักศึกษา};` แล้ว POST decision (`X-User-Id: 2`) | 403 | approvals ไม่เพิ่ม | 403 FORBIDDEN · approvals 3→3 (ตั้ง advisor_id คืนหลังทดสอบ) | ✅ |
| IT-AD-14 | ปิดรับก่อนกำหนด (TC6) | POST /api/forms/{id}/close | 200 | deadline = เวลาที่ยิง · status ยัง PUBLISHED | 200 · deadline = 2026-10-27 00:16:45.560559 (ยิงเวลา 2026-10-27 00:16) · status PUBLISHED | ✅ |
| IT-AD-15 | แนบไฟล์ช่องที่ 2 (TC6) | POST /api/requests/{id}/documents?fieldId={id ช่อง} (`X-User-Id` นักศึกษา) | 201 | documents +1 · form_field_id = id ช่อง · Transcript เดิมยังอยู่ | เตรียม: แบบฟอร์ม 4 มีช่องไฟล์ 2 ช่อง เปิดใช้งาน · สมปองสร้างคำร้อง 3 (201) แนบ Transcript (201) · แนบช่องที่ 2 → 201 · documents: null \| transcript.pdf / 16 \| certificate.pdf | ✅ |
| IT-AD-16 | จำนวนทุกสถานะ (TC7) | GET /api/applications/summary | 200 | ตรงกับ `SELECT status, COUNT(*) FROM applications GROUP BY status` | 200 · API {'DRAFT': 1, 'UNDER_REVIEW': 2} · DB {'DRAFT': 1, 'UNDER_REVIEW': 2} | ✅ |
| IT-AD-17 | ดาวน์โหลด CSV (TC7) | GET /api/applications/export?status=SUBMITTED | 200 text/csv | จำนวนแถวข้อมูล = คำร้อง SUBMITTED | เตรียม: สมปองส่งคำร้อง 3 (200) · 200 text/csv;charset=UTF-8 · แถวข้อมูล 1 = SUBMITTED 1 | ✅ |
| IT-AD-18 | ส่งกลับพร้อมช่องที่ต้องแก้ (TC8) | POST decision `{"decision":"RETURN","comment":"แก้เบอร์","fieldIds":[{id ช่อง}]}` | 200 (fieldIds ผิด → 400) | approvals.comment ลงท้าย "ช่องที่ต้องแก้: …" · answer_snapshots +1 ต่อคำตอบ | คำร้อง 3 สถานะ SUBMITTED · fieldIds ผิด → 400 · fieldIds ถูก → 200 · comment "แก้เบอร์ / ช่องที่ต้องแก้: เบอร์โทรศัพท์" · answer_snapshots +2 (คำตอบ 2 ช่อง) | ✅ |
| IT-AD-19 | อนุมัติหลายใบ (TC8) | POST /api/approvals/bulk `{"applicationIds":[{SUBMITTED},{UNDER_REVIEW}]}` | 200 approved 1 · skipped 1 | ใบแรก UNDER_REVIEW · ใบที่สองไม่เปลี่ยน | เตรียม: คำร้อง 3 ส่งฉบับแก้ (200) · 200 approved [3] skipped [{'applicationId': 1, 'reason': 'คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: UNDER_REVIEW)'}] · ใบ 3 = UNDER_REVIEW · ใบ 1 ยัง UNDER_REVIEW | ✅ |
| IT-AD-20 | แจ้งเจ้าหน้าที่ + เตือนก่อนหมดเขต (TC9) | นักศึกษา submit · ตั้ง `APP_REMINDER_CRON` ให้รันในอีก 1 นาที (มีคำร้อง NEED_CORRECTION และแบบฟอร์มหมดเขตภายใน 2 วัน) | – | APPLICATION_SUBMITTED +1 ต่อเจ้าหน้าที่ · CORRECTION_REMINDER +1 ต่อคำร้อง ไม่ซ้ำในวันเดียว | CORRECTION_REMINDER 0→1 (ภายใน 10 วินาที) → รอบถัดไปยัง 1 (ไม่ซ้ำในวันเดียว) · submit 200 → APPLICATION_SUBMITTED +1 (เจ้าหน้าที่ที่ใช้งานอยู่ 1 คน) | ✅ |

คำสั่งสำหรับ IT-AD-10 (ส่ง 2 request พร้อมกัน เปลี่ยน `{id}` เป็นใบสมัครที่ยัง SUBMITTED)
```bash
for i in 1 2; do curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/approvals/{id}/decision \
  -H "X-User-Id: 1" -H "Content-Type: application/json" -d '{"decision":"APPROVE"}' & done; wait
```

สรุปผล
-
| ผ่าน | ไม่ผ่าน | ทั้งหมด |
|---|---|---|
| 20 | 0 | 20 |

ปัญหาที่พบ
-
| TC ID | อาการ | สาเหตุ | วิธีแก้ / commit | Retest |
|---|---|---|---|---|
| – | ไม่มีข้อที่ไม่ผ่าน | – | – | – |

ข้อสังเกตจากการรัน
-
- IT-AD-10 ต้องมีคำร้อง SUBMITTED: ให้สมหญิงส่งฉบับแก้ผ่าน API ของ U3 ก่อน (PUT form 200, submit 200)
- IT-AD-11 ระหว่างฐานข้อมูลปิด API ตอบ 500 และหลังเปิดฐานข้อมูลคืน สถานะคำร้องและจำนวน approvals ไม่เปลี่ยน (ไม่มีข้อมูลค้าง)
- IT-AD-15 ใช้ `6609650003` เพราะ `6609650004` มีผลตรวจคุณสมบัติไม่ผ่านใน `DemoDataSeeder`
- IT-AD-17 ให้สมปองส่งคำร้องก่อน เพื่อให้ไฟล์ CSV มีแถวข้อมูลจริง (ถ้าไม่มีคำร้อง SUBMITTED เลย ไฟล์จะมีแค่หัวตาราง)
- IT-AD-20 เตือนครั้งแรกภายใน 1 นาทีหลังตั้งวันหมดเขตเหลือ 1 วัน และรอบถัดไปไม่เตือนซ้ำ (วันละครั้งต่อคำร้อง)


# ส่วนที่ 7 · ผล System Test

Artefact: รายงานผล System Test · อ้างอิง test cases: ส่วน "Design System Test" ในไฟล์นี้

สภาพแวดล้อมที่ใช้รัน
-
| รายการ | ค่า |
|---|---|
| วันที่รัน | 27 ต.ค. 2569 00:13–00:16 |
| ผู้รัน | Claude (รันอัตโนมัติด้วย Playwright) |
| Branch / commit | `Feature-/-U5-Staff` / `61a6533` (โค้ด 13 ข้อของ US5 ยังไม่อยู่ใน `Develop`) |
| ระบบ | `docker compose down -v` → `docker compose up -d --build` · Frontend เปิด `login.html` ด้วย Live Server |
| บัญชี | `staff01`, `advisor01`, `6609650001`, `6609650002` รหัสผ่าน `1234` |
| เบราว์เซอร์ | Chromium 141 (Playwright 1.56) |

สภาพแวดล้อมจริงตอนรัน: รันบนเครื่องที่ไม่มี Docker: backend รันด้วย `java` (JDK 21) จากโค้ด commit นี้ · ฐานข้อมูล H2 2.4 โหมด MySQL แยกเป็นอีก process (แทน MySQL 8.4) เริ่มจากฐานข้อมูลว่างให้ `DemoDataSeeder` ใส่ข้อมูลตั้งต้น · หน้าเว็บเปิดผ่าน http server พอร์ต 8081 · Email ส่งเข้า SMTP จำลอง (aiosmtpd) แทน Mailpit · คลิกหน้าเว็บและเก็บ request ด้วย Playwright 1.56 (Chromium 141) · backend เปิดด้วย `APP_REMINDER_CRON="0 * * * * *"` สำหรับ ST-U5-21 · ST-U5-13 หยุด process ของ backend แทน `docker compose stop app`

รัน test case ตามลำดับในตาราง (ข้อหลังใช้ข้อมูลจากข้อก่อน) ตรวจ 3 จุดทุกข้อ: หน้าจอ · DevTools แท็บ Network · MySQL

รายงานผล
-
| TC ID | เคส | ขั้นตอนโดยย่อ | Expected | Actual | ผล |
|---|---|---|---|---|---|
| ST-U5-01 | TC1 | staff01 สร้างแบบฟอร์ม 3 ช่อง → เปิดใช้งาน | 201 → 200 · ฉบับใหม่ PUBLISHED ฉบับเดิม ARCHIVED | POST /api/forms 201 → POST /api/forms/2/publish 200 · ฉบับ 2 PUBLISHED · ฉบับ 1 ARCHIVED · form_fields +3 | ✅ |
| ST-U5-02 | TC2 | ฉบับร่างไม่มีวันหมดเขต → เปิดใช้งาน | แจ้งให้กำหนดวันหมดเขต · ไม่มี PUBLISHED ใหม่ | หน้าเว็บ "กรุณากำหนดวันหมดเขตที่ยังไม่ผ่านไป ก่อนเปิดใช้งาน" · request ที่ส่ง 0 · PUBLISHED 1→1 (ยังเป็นฉบับ 2) | ✅ |
| ST-U5-03 | TC2 | Postman ชื่อช่องซ้ำ | 400 · form_templates ไม่เพิ่ม | POST /api/forms (X-User-Id: 1) → 400 VALIDATION_ERROR · form_templates 2→2 | ✅ |
| ST-U5-04 | TC3 | staff01 เปิดรายการ → ตรวจใบสมัครของสมชาย | 2 รายการ · คำตอบ 5 ช่อง ไฟล์ PDF | GET ?status=SUBMITTED 200 · 2 รายการ (สมชาย, สมหญิง เรียงตามวันที่ยื่น) · GET /api/applications/1/detail 200 คำตอบ 5 ช่อง · ไฟล์ "PDF transcript.pdf อัปโหลดแล้ว ดูไฟล์" | ✅ |
| ST-U5-05 | TC4 | อนุมัติ ส่งต่ออาจารย์ (สมชาย) | UNDER_REVIEW · approvals +1 · notifications +2 | POST /api/approvals/1/decision 200 · applications UNDER_REVIEW · approvals +1 (STAFF) · notifications อาจารย์ +1 สมชาย +1 | ✅ |
| ST-U5-06 | TC4 | ส่งกลับแก้ไขพร้อมเหตุผล (สมหญิง) | NEED_CORRECTION · แจ้งเตือนมีเหตุผล | POST /api/approvals/2/decision 200 · NEED_CORRECTION · แจ้งเตือนสมหญิง "กรุณาแก้ไขใบสมัครแล้วส่งใหม่ก่อนหมดเขต. เหตุผล: Transcript ไม่ชัด" | ✅ |
| ST-U5-07 | TC4 | ปฏิเสธไม่กรอกเหตุผล | หน้าเว็บไม่ส่ง request · Postman 400 | หน้าเว็บ "ยังบันทึกผลไม่ได้ กรุณากรอกเหตุผล" · request ที่ส่ง 0 · Postman REJECT ไม่มี comment → 400 VALIDATION_ERROR | ✅ |
| ST-U5-08 | TC4 | advisor01 อนุมัติ (สมชาย) | APPROVED · แจ้งเตือน "ใบสมัครได้รับการอนุมัติ" | POST /api/approvals/1/decision 200 · APPROVED · approvals ADVISOR +1 · แจ้งเตือนสมชาย "ใบสมัครได้รับการอนุมัติ" | ✅ |
| ST-U5-09 | TC4 | 6609650001 เปิดการแจ้งเตือน → กดอ่าน | ใหม่สุดอยู่บน · is_read = 1 | บนสุด "ใบสมัครได้รับการอนุมัติ อาจารย์อนุมัติใบสมัครฝึกสหกิจศึกษาของคุณแล้ว ขั้นตอนต่อไปคือจับคู่บริษัท 27 ต.ค. 2569 00:13" · PATCH /api/notifications/4/read 200 · is_read = TRUE | ✅ |
| ST-U5-10 | TC5 | ตัดสินใบสมัครเดียวกันจาก 2 แท็บ | แท็บสอง 409 · approvals เพิ่มแค่ 1 | เตรียม: สมหญิงส่งฉบับแก้ (200) · แท็บแรก 200 แท็บสอง 409 "คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: NEED_CORRECTION)" · approvals ของใบนี้ +1 | ✅ |
| ST-U5-11 | TC5 | Postman advisor01 ตัดสินใบสมัคร SUBMITTED | 409 | เตรียม: สมหญิงส่งฉบับแก้ (200) → SUBMITTED · advisor01 ตัดสิน → 409 INVALID_STATUS · approvals 4→4 | ✅ |
| ST-U5-12 | TC5 | นักศึกษาเข้า staff-review.html / เรียก API รายการ | หน้าแจ้งไม่มีสิทธิ์ · API 403 | หน้าเว็บ "หน้านี้สำหรับเจ้าหน้าที่และอาจารย์เท่านั้น ไปหน้าการแจ้งเตือน" · GET /api/applications?status=SUBMITTED (X-User-Id: 3) → 403 | ✅ |
| ST-U5-13 | – | `docker compose stop app` แล้วเปิดหน้า | "ไม่สามารถเชื่อมต่อระบบได้" | หยุด backend แล้วโหลดหน้าตรวจใบสมัครใหม่: "ไม่สามารถเชื่อมต่อระบบได้ กรุณาเปิด backend (docker compose up -d) แล้วลองใหม่ดูด้วยข้อมูล" · เปิด backend คืนแล้วหน้าใช้งานต่อได้ | ✅ |
| ST-U5-14 | TC5 | Postman อาจารย์คนอื่นตัดสินใบสมัครของสมชาย | 403 · approvals ไม่เพิ่ม | เตรียม: เพิ่มอาจารย์ advisor02 (id 7) ในตาราง users · ตัดสินใบ 4 ของสมปอง (UNDER_REVIEW) → 403 FORBIDDEN · approvals 7→7 · รายการ UNDER_REVIEW ของ advisor02 (API ที่หน้าตรวจเรียก) 200 0 รายการ | ✅ |
| ST-U5-15 | TC6 | ปิดรับก่อนกำหนด → นักศึกษายื่น | deadline = เวลาที่กด · submit 409 DEADLINE_PASSED | POST /api/forms/2/close 200 · deadline 2026-10-27 00:13:53.329878 (กดเวลา 00:13:53) · หน้ายื่นคำร้อง "เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้ (หมดเขต 27 ต.ค. 2569 00:13)" · Postman: สร้างคำร้อง 201 แล้ว submit → 409 DEADLINE_PASSED | ✅ |
| ST-U5-16 | TC6 | แบบฟอร์มช่องไฟล์ 2 ช่อง → นักศึกษาแนบครบแล้วส่ง | documents +2 · หน้าตรวจเห็นทั้ง 2 ไฟล์ · ขาดช่องที่ 2 ได้ 400 | ขาดช่องที่ 2 → 400 ✕ เอกสาร Transcript กรุณาแนบไฟล์ หนังสือรับรองการเป็นนักศึกษา ก่อนส่งคำร้อง · แนบครบ submit 200 · documents null transcript.pdf / 14 certificate.pdf · หน้าตรวจ "PDF transcript.pdf อัปโหลดแล้ว ดูไฟล์ PDF certificate.pdf อัปโหลดแล้ว ดูไฟล์" | ✅ |
| ST-U5-17 | TC7 | ดูตัวเลขบนแท็บ → ดาวน์โหลด Excel | summary ตรงกับฐานข้อมูล · CSV เปิดใน Excel ได้ | GET /summary 200 · แท็บ {"SUBMITTED":"2","NEED_CORRECTION":"","UNDER_REVIEW":"","REJECTED":""} · DB {"APPROVED":"1","DRAFT":"1","SUBMITTED":"2"} · CSV applications-SUBMITTED.csv 2 แถว = แท็บรอตรวจ 2 | ✅ |
| ST-U5-18 | TC7 | ตั้ง submitted_at ย้อน 4 วัน → โหลดหน้าใหม่ | ป้าย "รอนาน" อยู่บนสุด | attention: สมหญิง รักเรียน=OVERDUE, สมปอง ตั้งใจ=null · แถวบนสุด "6609650002 สมหญิง รักเรียน 23 ต.ค. 2569 00:14 1 ไฟล์ รอตรวจรอนาน ตรวจ" ไฮไลต์ true | ✅ |
| ST-U5-19 | TC8, TC9 | ส่งกลับพร้อมช่องที่ต้องแก้ → นักศึกษาแก้แล้วส่ง → เปิดรายละเอียด | comment มีช่องที่ต้องแก้ · answer_snapshots มีแถว · เห็นค่าเดิม · เจ้าหน้าที่ได้แจ้งเตือนฉบับแก้ | comment "Transcript ไม่ชัดเจน กรุณาแนบไฟล์ใหม่ / ช่องที่ต้องแก้: เบอร์โทรศัพท์" · answer_snapshots ของใบนี้ 15 แถว · หน้าตรวจ "เบอร์โทรศัพท์ แก้แล้ว 0811112222 เดิม: 0898765432" · แจ้งเตือนเจ้าหน้าที่ล่าสุด "มีคำร้องที่แก้ไขแล้วส่งกลับมา" | ✅ |
| ST-U5-20 | TC8 | ติ๊ก 2 ใบ → อนุมัติที่เลือก | ทั้ง 2 ใบ UNDER_REVIEW · approvals +2 | เลือก 2 ใบ · POST /api/approvals/bulk {"applicationIds":[4,2]} 200 "อนุมัติแล้ว 2 รายการ" · สถานะ UNDER_REVIEW, UNDER_REVIEW · approvals +2 · แจ้งเตือนอาจารย์ +2 สมหญิง +1 สมปอง +1 | ✅ |
| ST-U5-21 | TC9 | นักศึกษายื่นใหม่ · รอรอบเตือน 08:00 | เจ้าหน้าที่ได้ "มีคำร้องใหม่รอตรวจ" · นักศึกษาได้ "ใกล้หมดเขต…" วันละครั้ง · Email ใน Mailpit | เจ้าหน้าที่ได้ APPLICATION_SUBMITTED: มีคำร้องที่แก้ไขแล้วส่งกลับมา / มีคำร้องที่แก้ไขแล้วส่งกลับมา / มีคำร้องใหม่รอตรวจ / มีคำร้องที่แก้ไขแล้วส่งกลับมา · CORRECTION_REMINDER 0→1 (ภายใน 32 วินาที, cron ทุกนาที) → รอบถัดไป 1 · นักศึกษาเห็น "ใกล้หมดเขตส่งคำร้องที่ต้องแก้ไข คำร้องของคุณถูกส่งกลับให้แก้ไขและยังไม่ได้ส่งใหม่ กรุณาส่งภายใน 28/10/2026 00:14 น. 27 ต.ค. 2569 00:15" · Email ใหม่ในกล่องจำลอง: [ระบบสหกิจศึกษา]ใกล้หมดเขตส่งคำร้องที่ต้องแก้ไข | ✅ |

หลักฐาน
-
แคปหน้าจอของข้อที่สำคัญบันทึกในโฟลเดอร์นี้ชื่อ `U5-T19 ST-U5-xx.png` (ดูท้ายไฟล์)

สรุปผล
-
| ผ่าน | ไม่ผ่าน | ทั้งหมด |
|---|---|---|
| 21 | 0 | 21 |

ปัญหาที่พบ
-
| TC ID | อาการ | สาเหตุ | วิธีแก้ / commit | Retest |
|---|---|---|---|---|
| – | ไม่มีข้อที่ไม่ผ่าน | – | – | – |

ข้อสังเกตจากการรัน
-
- ลำดับที่รันจริง: 01–05 → 07 → 06 → 08–13 → 15–20 → 14 → 21 (07 ทำก่อน 06 เพราะต้องใช้ใบของสมหญิงที่ยัง SUBMITTED · 14 ทำหลัง 20 เพราะต้องมีใบ UNDER_REVIEW ของอาจารย์ advisor01)
- ST-U5-14 ในข้อมูลตั้งต้นมีอาจารย์คนเดียว จึงเพิ่ม `advisor02` ลงตาราง `users` ก่อนทดสอบ · บัญชีนี้ login หน้าเว็บไม่ได้ (ไม่อยู่ใน `app.auth.demo-users`) จึงตรวจ "หน้าตรวจไม่เห็นใบสมัคร" จาก API ที่หน้าตรวจเรียก
- ST-U5-15 พบว่า `POST /api/requests` บนแบบฟอร์มที่ปิดรับแล้วยังสร้างคำร้องฉบับร่างได้ (201) แต่ตอน submit ได้ 409 DEADLINE_PASSED ตาม Expected · หน้าเว็บของนักศึกษาไม่ให้กดส่งอยู่แล้ว
- ST-U5-21 Email แจ้งเตือน "[ระบบสหกิจศึกษา] ใกล้หมดเขตส่งคำร้องที่ต้องแก้ไข" เข้ากล่อง SMTP จำลอง 1 ฉบับ

หลักฐาน (แคปจากการรันจริง)
-
![.doc/UserStory/U5/U5-T19 ST-U5-01.png](<U5-T19 ST-U5-01.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-04.png](<U5-T19 ST-U5-04.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-05.png](<U5-T19 ST-U5-05.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-08.png](<U5-T19 ST-U5-08.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-13.png](<U5-T19 ST-U5-13.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-15.png](<U5-T19 ST-U5-15.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-16.png](<U5-T19 ST-U5-16.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-17.png](<U5-T19 ST-U5-17.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-18.png](<U5-T19 ST-U5-18.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-19.png](<U5-T19 ST-U5-19.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-20.png](<U5-T19 ST-U5-20.png>)
![.doc/UserStory/U5/U5-T19 ST-U5-21.png](<U5-T19 ST-U5-21.png>)


# ส่วนที่ 8 · ผล UAT

Artefact: รายงานผล UAT · อ้างอิง test cases: ส่วน "Design UAT" ในไฟล์นี้

ข้อมูลการทดสอบ
-
| รายการ | ค่า |
|---|---|
| วันที่ / สถานที่ | ____ |
| ผู้ดำเนินการ (ทีม) | ____ |
| ระบบ | `Develop` commit ____ · `docker compose up -d --build` (เริ่มทุกรอบด้วย `docker compose down -v`) |

ผู้ทดสอบ
-
| รหัส | บทบาท | บัญชีที่ใช้ |
|---|---|---|
| P1 | เจ้าหน้าที่ | staff01 |
| P2 | อาจารย์ | advisor01 |
| P3 | นักศึกษา | 6609650001 |
| P4 | นักศึกษา | 6609650002 |

ผลการทดสอบ
-
ผล: ✅ ทำได้เองตามเกณฑ์ · ⚠️ ทำได้แต่ต้องช่วย / เกินเวลา · ❌ ทำไม่ได้

| TC ID | ผู้ทดสอบ | เวลาที่ใช้ | ผล | สิ่งที่สังเกต / คำพูดของผู้ทดสอบ |
|---|---|---|---|---|
| UAT-U5-01 สร้างและเปิดใช้งานแบบฟอร์ม | P1 | | | |
| UAT-U5-02 กรอกแบบฟอร์มผิด | P1 | | | |
| UAT-U5-03 ดูใบสมัครครบ | P1 | | | |
| UAT-U5-04 อนุมัติส่งต่ออาจารย์ | P1 | | | |
| UAT-U5-05 ส่งกลับแก้ไขพร้อมเหตุผล | P1, P4 | | | |
| UAT-U5-06 อาจารย์อนุมัติ | P2, P3 | | | |
| UAT-U5-07 ลืมใส่เหตุผล | P1 | | | |
| UAT-U5-08 ตัดสินพร้อมกัน 2 คน | P1 + สมาชิกทีม | | | |
| UAT-U5-09 นักศึกษาดูการแจ้งเตือน | P3, P4 | | | |
| UAT-U5-10 เพิ่มช่องแนบไฟล์และดูตัวอย่าง | P1 | | | |
| UAT-U5-11 ปิดรับก่อนกำหนด | P1 | | | |
| UAT-U5-12 หาใบที่ควรตรวจก่อน (จำนวน / ป้ายเตือน) | P1 | | | |
| UAT-U5-13 ดาวน์โหลด Excel | P1 | | | |
| UAT-U5-14 ส่งกลับระบุช่อง แล้วตรวจฉบับแก้ | P1, P4 | | | |
| UAT-U5-15 อนุมัติหลายใบพร้อมกัน | P1 | | | |
| UAT-U5-16 แจ้งเตือนคำร้องใหม่ / เตือนก่อนหมดเขต | P1, P4 | | | |

Feedback จากผู้ทดสอบ
-
| ผู้ทดสอบ | ความเห็น | ระดับ (สูง / กลาง / ต่ำ) | ทีมจะทำอะไร |
|---|---|---|---|
| | | | |

สรุปผล UAT
-
- UAT-U5-01 ถึง 08 และ 10 ถึง 16 ผ่านทุกข้อ: ใช่ / ไม่ใช่
- ผู้ทดสอบที่ทำ UAT-U5-03, 04, 06 ได้เอง: ____ / ____ คน (เกณฑ์ ≥ 80%)
- ปัญหาระดับสูงที่ยังค้าง: ____
- **ผลรวม: ผ่าน / ไม่ผ่าน** (ผู้ทดสอบยืนยัน: ____)
