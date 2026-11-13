US6:TaskEND : Run Test (Integration Test + System Test + User Acceptance Test)
-
User story U6: นักศึกษาที่ใบสมัครได้รับอนุมัติเลือกตำแหน่งงานที่สนใจ (จัดอันดับ 1–5) --> เจ้าหน้าที่จับคู่นักศึกษากับบริษัทตามโควตาเป็นรอบ --> ประกาศผลรายอันดับ นักศึกษายืนยัน/สละสิทธิ์ และระบบแจ้งเตือน

> เอกสารของ Task นี้ตามเลข Task ของ Sprint 2 ใน Trello (การ์ด TaskEND:US5,US6,US7,US8:Design Test case และ Run Test) · TC1–TC7 ดู [Task1 · Requirement](T1.md)
>
> ลำดับของระบบทั้งหมด: U2 ตรวจคุณสมบัติ → U4 จัดอันดับบริษัท → U3 ยื่นคำร้อง → U5 เจ้าหน้าที่/อาจารย์อนุมัติ (`APPROVED`) → **U6 เลือกตำแหน่งและจับคู่**

ข้อมูลตั้งต้น (ทุกส่วนเริ่มจาก `docker compose down -v` แล้ว `docker compose up -d --build`)
-
`MatchingDemoSeeder` ใส่ข้อมูลของ U6 แยกจาก U3 / U4 / U5 · รหัสผ่านทุกบัญชี `1234`

| บัญชี | ชื่อ | ใบสมัคร | อันดับที่เลือกไว้ |
|---|---|---|---|
| `staff01` | เจ้าหน้าที่ | – | – |
| `6609650011` | ณัฐพล เลือกงาน | APPROVED (ยื่นก่อนสุด) | ยังไม่เลือก |
| `6609650012` | พิมพ์ชนก ตั้งใจ | APPROVED | SCB Backend → Agoda Software Engineer → KBTG QA |
| `6609650013` | ธนกร ขยันดี | APPROVED | SCB Backend → LINE MAN Frontend |
| `6609650014` | กมลชนก ใฝ่รู้ | APPROVED (ยื่นหลังสุด) | Bitkub Blockchain → True Data Engineer |
| `6609650001` | สมชาย ใจดี (U5) | ยังไม่ APPROVED | – |

ตำแหน่ง: SCB Backend, KBTG QA, LINE MAN Frontend, Bitkub Blockchain โควตา 1 · SCB Data Analyst, Agoda Software Engineer, True Data Engineer โควตา 2 · PTT Digital ปิดรับ


# ส่วนที่ 1 · Design Integration Test UI–API

ขอบเขต
-
- หน้า `matching.html`, `staff-matching.html`, `notifications.html` เรียก backend จริง (ฐานข้อมูลใหม่ + `MatchingDemoSeeder`)
- วิธีตรวจ: DevTools แท็บ Network ดู request/response และดูผลบนหน้าจอ · ข้อที่เขียนว่า Postman ใช้ `.doc/postman/U6-Matching.postman_collection.json`
- รันตามลำดับในตาราง (ข้อหลังใช้ข้อมูลจากข้อก่อน)

Design Integration Test ระหว่าง UI กับ API
-
| TC ID | Scenario | Input (บนหน้าเว็บ) | Request ที่ต้องเห็น | Response | Expected บนหน้าเว็บ |
|---|---|---|---|---|---|
| IT-UA-01 | เจ้าหน้าที่เปิดหน้า (TC3) | `staff01` → จับคู่บริษัท | GET /api/matching/rounds/current · GET /api/companies · GET /api/companies/{id} | 404, 200, 200 | "ยังไม่มีรอบ" ปุ่มจับคู่ / ประกาศผลซ่อน · รายการบริษัท ตำแหน่ง "ว่าง x/y" |
| IT-UA-02 | เพิ่มบริษัทและตำแหน่ง (TC3) | เพิ่มบริษัท "IT Test Co." แล้วเพิ่มตำแหน่ง "Tester" โควตา 2 | POST /api/companies · POST /api/companies/{id}/positions | 201, 201 | ขึ้นในรายการ "ว่าง 2/2" |
| IT-UA-03 | เปิดรอบ (TC4) | กดเปิดรอบโดยไม่ใส่วันหมดเขต → ใส่ +20 นาทีแล้วกด | ไม่มี request → POST /api/matching/rounds | 201 OPEN | "กรุณากำหนดวันหมดเขตยืนยันสิทธิ์" → "รอบที่ 1 เปิดจับคู่" ปุ่มจับคู่อัตโนมัติขึ้น |
| IT-UA-04 | จับคู่อัตโนมัติ (TC4) | กดจับคู่อัตโนมัติ | POST /api/matching/auto | 200 matched 3 · unmatched 1 | "จับคู่ได้ 3 คน · จับไม่ได้ 1 คน" ณัฐพล "ยังไม่ได้เลือกอันดับ" · พิมพ์ชนกได้ SCB (อันดับ 1) · ธนกรได้ LINE MAN (อันดับ 2 เพราะ SCB เต็ม) · กมลชนกได้ Bitkub (อันดับ 1) |
| IT-UA-05 | ใบสมัครยังไม่อนุมัติ (TC2) | `6609650001` เปิดเลือกตำแหน่ง / ผลจับคู่ | GET /api/requests/me | 200 (ไม่มีใบ APPROVED) | ข้อความรออนุมัติ · ไม่มี GET /api/companies |
| IT-UA-06 | นศ. ที่ยังไม่เลือกอันดับเปิดหน้า (TC1) | `6609650011` เปิดเลือกตำแหน่ง / ผลจับคู่ | GET /api/requests/me · GET /api/companies · GET /api/applications/{id}/preferences · GET /api/matches/me/summary | 200 · 200 · 200 [] · 404 | dropdown 5 อันดับ จัดกลุ่มตามบริษัท · ไม่มี PTT Digital (ปิดรับ) · ไม่แสดงผลจับคู่ |
| IT-UA-07 | ตำแหน่งโควตาเต็ม (TC2, UC3) | ณัฐพลเปิด dropdown | (ข้อมูลจาก IT-UA-06) | `availableSlots` 0 | "Backend Developer (โควตาเต็ม 0/1)" เลือกไม่ได้ · Postman PUT ด้วย id นี้ได้ 400 INVALID_PREFERENCE |
| IT-UA-08 | เลือกซ้ำ / เว้นอันดับ (TC2) | เลือกอันดับ 1 = อันดับ 2 / เลือกอันดับ 1 กับ 3 แล้วกดบันทึก | ไม่มี request | – | "เลือกตำแหน่งเดียวกันซ้ำหลายอันดับไม่ได้" / "กรุณาเลือกอันดับให้ต่อเนื่อง เริ่มจากอันดับ 1" |
| IT-UA-09 | บันทึกอันดับ (TC1) | เลือก Tester, KBTG QA แล้วกดบันทึก | PUT /api/applications/{id}/preferences | 200 | "บันทึก 2 อันดับแล้ว" · โหลดหน้าใหม่ค่ายังอยู่ |
| IT-UA-10 | จับคู่เองเข้าตำแหน่งเต็ม (TC4) | Postman: จับคู่ณัฐพลเข้า SCB Backend | POST /api/matches | 409 QUOTA_FULL | – |
| IT-UA-11 | จับคู่เอง (TC4) | `staff01` กดตำแหน่ง Tester → รายชื่อนักศึกษาที่เลือก → จับคู่ณัฐพล | GET /api/matching/candidates?positionId={id} · POST /api/matches | 200, 201 | ตารางผลเพิ่มณัฐพล · Tester ว่าง 1/2 |
| IT-UA-12 | ยกเลิกผล (TC4) | กดยกเลิกแถวกมลชนก (Bitkub) | DELETE /api/matches/{id} | 200 CANCELLED | สถานะ "ยกเลิก" · Bitkub ว่าง 1/1 (คืนโควตา) |
| IT-UA-13 | ลดโควตาต่ำกว่าที่จับคู่ (TC3) | กมลชนกบันทึกอันดับเป็น Tester → `staff01` จับคู่เข้า Tester (ครบ 2) → แก้โควตา Tester เป็น 1 | PUT /api/positions/{id} | 400 QUOTA_BELOW_MATCHED | "ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (2 คน) ไม่ได้" |
| IT-UA-14 | ก่อนประกาศ ดูผลไม่ได้ (TC6) | พิมพ์ชนกเปิดหน้า | GET /api/matches/me/summary | 404 | ไม่แสดงผลจับคู่ |
| IT-UA-15 | ประกาศผล (TC5) | `staff01` กดประกาศผลรอบนี้ | POST /api/matching/rounds/{id}/announce | 200 ANNOUNCED | ปุ่มจับคู่ / ประกาศหาย · สถานะ "ประกาศผลแล้ว" |
| IT-UA-16 | นศ. เห็นผลและแจ้งเตือน (TC5) | พิมพ์ชนกเปิดหน้าเลือกตำแหน่ง และหน้าการแจ้งเตือน | GET /api/matches/me/summary · GET /api/notifications | 200, 200 | ผลรายอันดับ "ได้อันดับนี้" · ปุ่มยืนยัน / สละ · แจ้งเตือน "ประกาศผลการจับคู่ รอบที่ 1" |
| IT-UA-17 | ยืนยันสิทธิ์ (TC6) | พิมพ์ชนกเลือกยืนยันแล้วกดส่ง | POST /api/matches/{id}/respond `{accept: true}` | 200 CONFIRMED | "ยืนยันสิทธิ์เรียบร้อยแล้ว" · `staff01` ได้แจ้งเตือน |
| IT-UA-18 | สละสิทธิ์ (TC6) | ธนกรเลือกสละ ไม่ใส่เหตุผล → ใส่เหตุผลแล้วส่ง | ไม่มี request → POST respond `{accept: false, reason}` | 200 DECLINED | "กรุณากรอกเหตุผลที่สละสิทธิ์" → "สละสิทธิ์เรียบร้อยแล้ว…" · LINE MAN ว่าง 1/1 |
| IT-UA-19 | เปิดรอบ 2 ก่อนหมดเขต (TC4) | `staff01` เปิดรอบใหม่ | POST /api/matching/rounds | 409 ROUND_NOT_CLOSED | "รอบที่ 1 ยังไม่จบ…" |
| IT-UA-20 | เลยกำหนด (TC6) | รอจนเลยวันหมดเขต → ณัฐพลกดยืนยัน | POST respond | 409 DEADLINE_PASSED | "เลยกำหนดยืนยันสิทธิ์แล้ว" |
| IT-UA-21 | เปิดรอบ 2 หลังหมดเขต (TC4) | `staff01` เปิดรอบใหม่ | POST /api/matching/rounds | 201 | รอบ 2 เปิด · ผลของณัฐพลกับกมลชนกที่ไม่ได้ตอบเป็น "สละสิทธิ์" (ไม่ตอบภายในกำหนด) · Tester ว่างกลับ 2/2 |
| IT-UA-22 | ไม่มีสิทธิ์ (TC7) | นศ. พิมพ์ URL `staff-matching.html` · Postman `X-User-Id` = นักศึกษา เรียก POST /api/matching/auto | – | 403 | หน้าเว็บบอกว่าเป็นหน้าของเจ้าหน้าที่ |
| IT-UA-23 | server ไม่ทำงาน | `docker compose stop app` แล้วเปิดหน้า (เสร็จแล้ว `docker compose start app`) | network error | – | "ไม่สามารถเชื่อมต่อระบบได้…" |

ความครอบคลุมกับ test case ของ U6
-
| เคส | TC ID |
|---|---|
| TC1 นักศึกษาที่ใบสมัคร APPROVED เห็นตำแหน่งที่เปิดรับพร้อมที่ว่าง และบันทึกอันดับ 1–5 ได้ | IT-UA-06, IT-UA-09 |
| TC2 บันทึกอันดับไม่ได้: ใบสมัครยังไม่ APPROVED / มีผลแล้ว / 0, เกิน 5, ซ้ำ / ตำแหน่งเต็ม | IT-UA-05, IT-UA-07, IT-UA-08 |
| TC3 เจ้าหน้าที่เพิ่ม / แก้บริษัทและตำแหน่งพร้อมโควตา · ลดโควตาต่ำกว่าที่จับคู่แล้วไม่ได้ | IT-UA-01, IT-UA-02, IT-UA-13 |
| TC4 เปิดรอบ · จับคู่อัตโนมัติ / จับคู่เอง / ยกเลิก (คืนโควตา) · เปิดรอบถัดไป | IT-UA-03, IT-UA-04, IT-UA-10, IT-UA-11, IT-UA-12, IT-UA-19, IT-UA-21 |
| TC5 ประกาศผลแล้วนักศึกษาได้รับแจ้งเตือนและเห็นผลทุกอันดับ | IT-UA-15, IT-UA-16 |
| TC6 ยืนยัน / สละสิทธิ์ก่อนหมดเขต · เลยกำหนดตอบไม่ได้ · ก่อนประกาศดูผลไม่ได้ | IT-UA-14, IT-UA-17, IT-UA-18, IT-UA-20 |
| TC7 ผู้ใช้ที่ไม่มีสิทธิ์ถูกปฏิเสธ | IT-UA-22 |


# ส่วนที่ 2 · Design Integration Test API–DAO

ขอบเขต
-
- ทดสอบ Controller → Service → Repository → ฐานข้อมูล โดยไม่ใช้หน้าเว็บ ส่ง request ด้วย Postman แล้วตรวจข้อมูลใน MySQL ก่อน / หลังทุกข้อ
- ผู้ใช้ (header `X-User-Id`): `staff01` = 1 · ของนักศึกษาดูจาก `SELECT id, username FROM users WHERE username LIKE '66096500%'`
- รันตามลำดับในตาราง

Design Integration Test ระหว่าง API กับ DAO
-
| TC ID | Scenario | Request | DAO ที่ถูกเรียก | Expected response | Expected ในฐานข้อมูล |
|---|---|---|---|---|---|
| IT-AD-01 | สร้างบริษัท (TC3) | POST /api/companies | CompanyRepository.save | 201 OPEN | `companies` +1 แถว |
| IT-AD-02 | สร้างตำแหน่ง (TC3) | POST /api/companies/{id}/positions โควตา 2 | CompanyRepository.findById · CompanyPositionRepository.save | 201 | `company_positions` +1 แถว `quota` = 2 `available_slots` = 2 |
| IT-AD-03 | โควตาไม่ถูกต้อง (TC3) | POST /api/companies/{id}/positions โควตา 0 | ไม่ถูกเรียก | 400 | ไม่มีแถวใหม่ |
| IT-AD-04 | เปิดรอบ (TC4) | POST /api/matching/rounds (+20 นาที) | MatchingRoundRepository.findFirstByOrderByRoundNoDesc, save | 201 | `matching_rounds` round_no 1 OPEN |
| IT-AD-05 | ดูผลก่อนประกาศ (TC6) | GET /api/matches/me/summary (นศ.) | MatchRepository.findFirstByApplicationIdOrderByIdDesc · MatchingRoundRepository.findFirstByStatusInOrderByRoundNoDesc | 404 | – |
| IT-AD-06 | จับคู่อัตโนมัติ (TC4) | POST /api/matching/auto | MatchingRoundRepository.findFirstByStatus · ApplicationRepository.findApprovedWithoutActiveMatch · CompanyPreferenceRepository.findByApplicationIdOrderByRankNoAsc · CompanyPositionRepository.findByIdForUpdate, save · MatchRepository.save | 200 matched 3 · unmatched 1 | `matches` +3 PROPOSED `rank_no` 1, 2, 1 · `available_slots` ของ SCB Backend / LINE MAN / Bitkub = 0 |
| IT-AD-07 | จับคู่เองตำแหน่งเต็ม (TC4) | POST /api/matches (ณัฐพล → SCB Backend) | CompanyPositionRepository.findByIdForUpdate | 409 QUOTA_FULL | ไม่มีแถวใหม่ |
| IT-AD-08 | ยกเลิกผล (TC4) | DELETE /api/matches/{id} (กมลชนก) | MatchRepository.findById, save · CompanyPositionRepository.findByIdForUpdate, save | 200 | `matches.status` = CANCELLED · Bitkub `available_slots` +1 |
| IT-AD-09 | ประกาศผล (TC5) | POST /api/matching/rounds/{id}/announce | MatchingRoundRepository.findById, save · MatchRepository.findByRoundIdOrderByIdAsc · NotificationRepository.save | 200 | รอบเป็น ANNOUNCED มี `announced_at` · `notifications` MATCH_ANNOUNCED +1 ต่อนักศึกษาที่ได้ผล และที่เลือกอันดับไว้แต่ไม่ได้ (ณัฐพลไม่ได้เลือกอันดับจึงไม่ได้แจ้งเตือน) |
| IT-AD-10 | ประกาศซ้ำ / จับคู่หลังประกาศ (TC4, TC5) | announce อีกครั้ง · POST /api/matching/auto | MatchingRoundRepository.findById / findFirstByStatus | 409 · 409 NO_OPEN_ROUND | ไม่มีแจ้งเตือนหรือผลเพิ่ม |
| IT-AD-11 | บันทึกอันดับ (TC1) | PUT /api/applications/{id}/preferences (ณัฐพล 2 อันดับ) แล้วส่งใหม่ 1 อันดับ | ApplicationRepository.findById · StudentRepository.findByUserId · MatchRepository.findFirstByApplicationIdAndStatusIn · CompanyPositionRepository.findAllById · CompanyPreferenceRepository.deleteByApplicationId, saveAll | 200 · 200 | `company_preferences` ของใบนี้มี 2 แถว (rank 1, 2) แล้วเหลือ 1 แถว |
| IT-AD-12 | อันดับไม่ถูกต้อง (TC2) | PUT ตำแหน่งเต็ม / ปิดรับ / ซ้ำ / 6 อันดับ | CompanyPositionRepository.findAllById | 400 INVALID_PREFERENCE | แถวเดิมไม่เปลี่ยน |
| IT-AD-13 | ใบสมัครยังไม่อนุมัติ / มีผลแล้ว (TC2) | PUT ของ 6609650001 · PUT ของพิมพ์ชนก (PROPOSED) | ApplicationRepository.findById · MatchRepository.findFirstByApplicationIdAndStatusIn | 409 APPLICATION_NOT_APPROVED · 409 ALREADY_MATCHED | ไม่เปลี่ยน |
| IT-AD-14 | แก้อันดับของคนอื่น (TC7) | `X-User-Id` ของพิมพ์ชนก PUT ใบของณัฐพล | StudentRepository.findByUserId | 403 | ไม่เปลี่ยน |
| IT-AD-15 | ยืนยันสิทธิ์ (TC6) | POST /api/matches/{id}/respond `{accept: true}` | MatchRepository.findById, save · NotificationRepository.save | 200 CONFIRMED | `responded_at` มีค่า · `notifications` MATCH_RESPONDED +1 ต่อเจ้าหน้าที่ |
| IT-AD-16 | สละสิทธิ์ (TC6) | respond `{accept: false}` ไม่มีเหตุผล → มีเหตุผล | MatchRepository.findById, save · CompanyPositionRepository.findByIdForUpdate, save | 400 REASON_REQUIRED → 200 DECLINED | `decline_reason` ตามที่ส่ง · LINE MAN `available_slots` +1 |
| IT-AD-17 | ตอบผลของคนอื่น (TC7) | respond ผลของธนกรด้วย `X-User-Id` ของพิมพ์ชนก | StudentRepository.findByUserId | 403 | ไม่เปลี่ยน |
| IT-AD-18 | นศ. เรียก API เจ้าหน้าที่ (TC7) | POST /api/matching/auto ด้วย `X-User-Id` ของนักศึกษา | AccessGuard (UserRepository.findById) | 403 | ไม่มีแถวใหม่ |
| IT-AD-19 | เปิดรอบ 2 ก่อนหมดเขต (TC4) | POST /api/matching/rounds | MatchingRoundRepository.findFirstByOrderByRoundNoDesc | 409 ROUND_NOT_CLOSED | ไม่มีรอบใหม่ |
| IT-AD-20 | ตอบหลังหมดเขต (TC6) | respond หลังเลย `confirm_deadline` | MatchingRoundRepository.findById | 409 DEADLINE_PASSED | ไม่เปลี่ยน |
| IT-AD-21 | เปิดรอบ 2 หลังหมดเขต (TC4) | POST /api/matching/rounds | MatchRepository.findByRoundIdAndStatus(PROPOSED), save · CompanyPositionRepository.findByIdForUpdate, save · MatchingRoundRepository.save | 201 | รอบ 1 = CLOSED · ผลที่ยัง PROPOSED เป็น DECLINED `decline_reason` = "ไม่ตอบภายในกำหนด" และคืนโควตา · รอบ 2 = OPEN |
| IT-AD-22 | ลดโควตาต่ำกว่าที่จับคู่ (TC3) | รอบ 2: จับคู่ 2 คนเข้า Tester แล้ว PUT /api/positions/{id} โควตา 1 | CompanyPositionRepository.findByIdForUpdate | 400 QUOTA_BELOW_MATCHED | `quota` ยังเป็น 2 |

SQL ที่ใช้ตรวจ (`docker exec -it cs261-db mysql -uroot -proot1234 cs261db`)
-
```sql
SELECT id, position_name, quota, available_slots, status FROM company_positions;
SELECT application_id, position_id, rank_no FROM company_preferences ORDER BY application_id, rank_no;
SELECT id, round_no, status, confirm_deadline, announced_at FROM matching_rounds;
SELECT id, application_id, position_id, round_id, rank_no, status, decline_reason FROM matches;
SELECT user_id, type, title FROM notifications WHERE type LIKE 'MATCH%' ORDER BY id;
```

ความครอบคลุมกับ test case ของ U6
-
| เคส | TC ID |
|---|---|
| TC1 | IT-AD-11 |
| TC2 | IT-AD-12, IT-AD-13 |
| TC3 | IT-AD-01, IT-AD-02, IT-AD-03, IT-AD-22 |
| TC4 | IT-AD-04, IT-AD-06, IT-AD-07, IT-AD-08, IT-AD-10, IT-AD-19, IT-AD-21 |
| TC5 | IT-AD-09, IT-AD-10 |
| TC6 | IT-AD-05, IT-AD-15, IT-AD-16, IT-AD-20 |
| TC7 | IT-AD-14, IT-AD-17, IT-AD-18 |


# ส่วนที่ 3 · Design System Test

Sequence Diagram ที่ใช้
-
![US6 Sequence 1 บันทึกอันดับ](<us6-07-sequence-1-preferences.png>)
![US6 Sequence 2 จับคู่อัตโนมัติ](<us6-07-sequence-2-auto-match.png>)
![US6 Sequence 3 เปิดรอบและประกาศผล](<us6-07-sequence-3-round-announce.png>)
![US6 Sequence 4 ยืนยัน / สละสิทธิ์](<us6-07-sequence-4-respond.png>)

ทดสอบทั้งระบบตามเส้นทางใน Sequence Diagram ① บันทึกอันดับ ② จับคู่อัตโนมัติ ③ เปิดรอบ / ประกาศผล ④ ยืนยัน / สละสิทธิ์ ด้วยหน้าเว็บจริง + backend จริง + MySQL แล้วตรวจ 3 จุดทุกข้อ: หน้าจอ · DevTools แท็บ Network · MySQL

เตรียมก่อนทดสอบ
-
1. `docker compose down -v` แล้ว `docker compose up -d --build`
2. เปิด `http://localhost:8081/login.html` · ดูอีเมลที่ Mailpit `http://localhost:8025`
3. ดูข้อมูลใน MySQL: `docker exec -it cs261-db mysql -uroot -proot1234 cs261db` (SQL ในส่วนที่ 2)

Test cases
-
| TC ID | เคส | เส้นทางใน Sequence Diagram | Input / ขั้นตอน | Expected result (หน้าจอ / API / ฐานข้อมูล) |
|---|---|---|---|---|
| ST-U6-01 | TC3 | (ก่อน ③) จัดการบริษัท | `staff01` → จับคู่บริษัท → เพิ่ม "IT Test Co." → เพิ่มตำแหน่ง "Tester" โควตา 2 | POST 201 ทั้ง 2 ครั้ง · `company_positions` มี Tester ว่าง 2/2 |
| ST-U6-02 | TC4 | ③ เปิดรอบ | เปิดรอบ วันหมดเขต +20 นาที | 201 · `matching_rounds` รอบ 1 OPEN · ปุ่มจับคู่อัตโนมัติขึ้น |
| ST-U6-03 | TC4 | ② จับคู่อัตโนมัติ → เลื่อนอันดับเมื่อเต็ม | กดจับคู่อัตโนมัติ | 200 จับคู่ได้ 3 / จับไม่ได้ 1 (ณัฐพล: ยังไม่ได้เลือกอันดับ) · ธนกรได้อันดับ 2 เพราะ SCB เต็ม · `matches` +3 PROPOSED · ที่ว่างของ 3 ตำแหน่งเป็น 0 |
| ST-U6-04 | TC6 | ④ ก่อนประกาศ → 404 | login พิมพ์ชนก → เลือกตำแหน่ง / ผลจับคู่ | summary 404 · ไม่แสดงผล (ถ้ากดบันทึกอันดับจะได้ 409 ALREADY_MATCHED · ข้อจำกัดที่ทีมรับรู้แล้ว ดู "ข้อสังเกต" ในส่วนที่ 7) |
| ST-U6-05 | TC4 | ② ยกเลิกผล → จับคู่อัตโนมัติอีกครั้ง | `staff01` ยกเลิกผลของกมลชนก → กดจับคู่อัตโนมัติอีกครั้ง | DELETE 200 · `matches` CANCELLED · Bitkub ว่าง 1/1 → auto 200 กมลชนกได้ Bitkub กลับมา (PROPOSED แถวใหม่) · ณัฐพลยังจับไม่ได้ |
| ST-U6-06 | TC5 | ③ ประกาศผล → แจ้งเตือน | กดประกาศผลรอบนี้ | 200 ANNOUNCED · `notifications` MATCH_ANNOUNCED ของพิมพ์ชนก ธนกร กมลชนก · อีเมลเข้า Mailpit |
| ST-U6-07 | TC5 | ④ ดูผล | login พิมพ์ชนก → เลือกตำแหน่ง / ผลจับคู่ → การแจ้งเตือน | summary 200: อันดับ 1 "ได้อันดับนี้" · ปุ่มยืนยัน / สละสิทธิ์ · แจ้งเตือน "ประกาศผลการจับคู่ รอบที่ 1" อยู่บนสุด |
| ST-U6-08 | TC6 | ④ ยืนยันสิทธิ์ | พิมพ์ชนกกดยืนยันสิทธิ์ | 200 CONFIRMED · `responded_at` มีค่า · `staff01` ได้แจ้งเตือน "นักศึกษายืนยันสิทธิ์" |
| ST-U6-09 | TC6 | ④ สละสิทธิ์ (alt ไม่มีเหตุผล) | ธนกรเลือกสละสิทธิ์ ไม่ใส่เหตุผล → ใส่ "ได้ที่ฝึกงานอื่นแล้ว" | ครั้งแรกไม่มี request · ครั้งที่สอง 200 DECLINED · LINE MAN ว่าง 1/1 · `staff01` ได้แจ้งเตือนพร้อมเหตุผล |
| ST-U6-10 | TC1, TC2 | ① บันทึกอันดับ (หลังประกาศ) | login ณัฐพล → เปิด dropdown → เลือก Tester, KBTG QA → บันทึก | SCB Backend ขึ้น "โควตาเต็ม" เลือกไม่ได้ · PUT 200 · `company_preferences` 2 แถว |
| ST-U6-11 | TC4 | ③ เปิดรอบ 2 ก่อนหมดเขต (alt 409) | `staff01` เปิดรอบใหม่ | 409 ROUND_NOT_CLOSED · ไม่มีรอบใหม่ |
| ST-U6-12 | TC6 | ④ ตอบหลังหมดเขต (alt 409) | รอให้เลยวันหมดเขต → กมลชนก (ยังไม่ได้ตอบ) กดยืนยัน | 409 DEADLINE_PASSED · หน้าเว็บ "เลยกำหนดยืนยันสิทธิ์แล้ว" |
| ST-U6-13 | TC4 | ③ เปิดรอบ 2 → ปิดรอบเก่า | `staff01` เปิดรอบใหม่ วันหมดเขต +1 วัน | 201 · รอบ 1 CLOSED · ผลของกมลชนกเป็น DECLINED "ไม่ตอบภายในกำหนด" และ Bitkub คืนโควตา · รอบ 2 OPEN |
| ST-U6-14 | TC4 | ② จับคู่อัตโนมัติรอบ 2 + ① + จับคู่เอง | กดจับคู่อัตโนมัติ → กมลชนกบันทึกอันดับเป็น Tester → `staff01` กดตำแหน่ง Tester → จับคู่กมลชนก | ณัฐพลได้ Tester (อันดับ 1) · candidates แสดงกมลชนกอันดับ 1 · POST /api/matches 201 · Tester ว่าง 0/2 |
| ST-U6-15 | TC3 | ลดโควตาต่ำกว่าที่จับคู่ | แก้โควตา Tester เป็น 1 | 400 QUOTA_BELOW_MATCHED · ข้อความบอกจำนวนที่จับคู่แล้ว (2 คน) · `quota` ยังเป็น 2 |
| ST-U6-16 | TC7 | ไม่มีสิทธิ์ | login นักศึกษาแล้วพิมพ์ URL `staff-matching.html` · Postman `X-User-Id` = นักศึกษา เรียก POST /api/matching/auto | หน้าเว็บบอกว่าเป็นหน้าของเจ้าหน้าที่ · API 403 |
| ST-U6-17 | – | ระบบล่ม | `docker compose stop app` แล้วเปิดหน้า (เสร็จแล้ว `docker compose start app`) | "ไม่สามารถเชื่อมต่อระบบได้…" หน้าไม่ค้าง |

ความครอบคลุม
-
| เคส | TC ID |
|---|---|
| TC1 | ST-U6-10, ST-U6-14 |
| TC2 | ST-U6-10 |
| TC3 | ST-U6-01, ST-U6-15 |
| TC4 | ST-U6-02, ST-U6-03, ST-U6-05, ST-U6-11, ST-U6-13, ST-U6-14 |
| TC5 | ST-U6-06, ST-U6-07 |
| TC6 | ST-U6-04, ST-U6-08, ST-U6-09, ST-U6-12 |
| TC7 | ST-U6-16 |

ผลการรันบันทึกใน ส่วน "ผล System Test" ในไฟล์นี้


# ส่วนที่ 4 · Design UAT

-
- **As a** นักศึกษา
- **I want to** ค้นหารายชื่อบริษัท MOU จัดลำดับความต้องการ (Ranking) และตรวจสอบโควตารับสมัคร
- **So that** สามารถสมัครเข้าฝึกงานในบริษัทที่ต้องการได้อย่างเป็นระบบ

ผู้ทดสอบ
-
- ผู้รับบทเจ้าหน้าที่ 1 คน (`staff01`) · นักศึกษา 2–4 คน (`6609650011`–`6609650014`) รหัสผ่าน `1234`
- ทีมอธิบายแค่สถานการณ์ (Given) **ไม่บอกว่าต้องกดปุ่มไหน** แล้วสังเกตว่าผู้ทดสอบทำได้เองหรือไม่ จับเวลา และจดคำพูด / ปัญหาที่เจอ
- เริ่มทุกรอบด้วย `docker compose down -v` แล้ว `docker compose up -d --build`

Test cases
-
| TC ID | เคส | Given | When | Then | เกณฑ์ที่ user ยอมรับได้ |
|---|---|---|---|---|---|
| UAT-U6-01 | TC1 | ใบสมัครของนักศึกษาได้รับอนุมัติแล้ว | หาตำแหน่งที่สนใจแล้วจัดอันดับ 3 ตำแหน่ง | บันทึกสำเร็จ เห็นอันดับที่เลือกไว้ | ทำเองได้ภายใน 3 นาที รู้ว่าแต่ละตำแหน่งเหลือกี่ที่ |
| UAT-U6-02 | TC2 | ตำแหน่งที่อยากได้โควตาเต็มแล้ว | พยายามเลือกตำแหน่งนั้น | เลือกไม่ได้ และเห็นว่าเพราะโควตาเต็ม | เข้าใจทันทีโดยไม่ต้องถามทีม แล้วเลือกตำแหน่งอื่นแทน |
| UAT-U6-03 | TC2 | นักศึกษาเลือกตำแหน่งซ้ำ หรือข้ามอันดับ | กดบันทึก | ระบบบอกว่าผิดตรงไหน อันดับที่เลือกไว้ไม่หาย | อ่านข้อความแล้วแก้ได้ทันที |
| UAT-U6-04 | TC3 | เจ้าหน้าที่ได้ MOU บริษัทใหม่ | เพิ่มบริษัทและตำแหน่งพร้อมโควตา | นักศึกษาเห็นตำแหน่งใหม่ในหน้าเลือก | ทำเองได้ภายใน 2 นาที |
| UAT-U6-05 | TC4 | นักศึกษาเลือกอันดับกันครบแล้ว | เปิดรอบแล้วจับคู่อัตโนมัติ | เห็นว่าใครได้ตำแหน่งไหน และใครจับไม่ได้เพราะอะไร | รู้ว่าต้องตามใครต่อโดยไม่ต้องเปิดดูทีละคน |
| UAT-U6-06 | TC4 | นักศึกษาคนหนึ่งจับคู่อัตโนมัติไม่ได้ | ดูรายชื่อคนที่เลือกตำแหน่งที่ยังว่าง แล้วจับคู่ให้เอง หรือยกเลิกผลที่ผิด | ผลและจำนวนที่ว่างอัปเดตทันที | จับคู่เองได้ภายใน 1 นาที |
| UAT-U6-07 | TC5 | จับคู่เสร็จแล้ว | ประกาศผล | นักศึกษาทุกคนในรอบได้แจ้งเตือนในเว็บและอีเมล | นักศึกษาหาผลของตัวเองเจอภายใน 30 วินาที |
| UAT-U6-08 | TC6 | นักศึกษาได้ตำแหน่งที่อยากได้ | ยืนยันสิทธิ์ | เห็นว่ายืนยันแล้ว เจ้าหน้าที่ได้แจ้งเตือน | มั่นใจว่ายืนยันแล้ว ไม่ต้องกดซ้ำ |
| UAT-U6-09 | TC6 | นักศึกษาได้ที่ฝึกที่อื่นแล้ว | สละสิทธิ์ | ระบบบังคับให้ใส่เหตุผล แล้วคืนที่ให้คนอื่น | เข้าใจว่าสละแล้วต้องรอรอบถัดไป |
| UAT-U6-10 | TC6 | เลยวันหมดเขตยืนยันสิทธิ์ | นักศึกษาพยายามยืนยัน / เจ้าหน้าที่เปิดรอบใหม่ | นักศึกษาตอบไม่ได้ · คนที่ไม่ตอบถูกนับเป็นสละสิทธิ์ ที่ว่างกลับมา | เจ้าหน้าที่ไม่ต้องไล่คืนโควตาเอง |
| UAT-U6-11 | TC7 | นักศึกษาอยากรู้ผลของเพื่อน / เข้าหน้าเจ้าหน้าที่ | พิมพ์ URL หน้าเจ้าหน้าที่ | ระบบไม่ให้เข้า | ข้อมูลของคนอื่นไม่หลุด |

เกณฑ์ผ่าน UAT โดยรวม
-
- UAT-U6-01 ถึง UAT-U6-11 ต้องผ่านทุกข้อ (เคสบังคับตาม user story)
- ผู้ทดสอบอย่างน้อย 80% ทำ UAT-U6-01, 05, 08 ได้เองโดยไม่ต้องถามทีม
- ไม่มีปัญหาระดับสูง (ทำงานต่อไม่ได้ / ข้อมูลผิด) ค้างอยู่ · บันทึกผลใน ส่วน "ผล UAT" ในไฟล์นี้


# ส่วนที่ 5 · ผล Integration Test UI–API

Artefact: รายงานผล Integration Test · อ้างอิง test cases: ส่วน "Design Integration Test UI–API" ในไฟล์นี้

สภาพแวดล้อม
-
- วันที่รัน: 14 พ.ย. 2569 เวลา 00:20–00:37 น. · branch `Develop` (`06c385f`) · เริ่มจาก `docker compose down -v` แล้ว `up -d --build`
- เปิดหน้าเว็บผ่าน container ทดสอบชั่วคราว `cs261-u6-proxy` (nginx พอร์ต 8082) ที่เสิร์ฟไฟล์ `frontend/` ชุดเดียวกับพอร์ต 8081 และส่ง `/api/` ต่อไปที่ backend พอร์ต 8080 เพราะ browser ที่ใช้ทดสอบไม่ยอมให้หน้า 8081 เรียก 8080 ข้ามพอร์ต โค้ดหน้าเว็บและ backend ไม่ได้แก้ (ตั้งค่าไว้ใน `u6-test-proxy.conf` นอก repo)
- ตรวจ request / response จากแท็บ Network และข้อความบนหน้าจอ

ผลการรัน
-
| TC ID | Request / Response ที่เห็น | ผลบนหน้าเว็บ | ผล |
|---|---|---|---|
| IT-UA-01 | GET rounds/current 404 · GET /api/companies 200 · GET /api/companies/{1–6} 200 | "ยังไม่มีรอบการจับคู่ เปิดรอบแรกได้ด้านล่าง" · ปุ่มจับคู่ / ประกาศผลซ่อน · บริษัท 6 แห่งพร้อม "ว่าง x/y" | ✅ |
| IT-UA-02 | POST /api/companies 201 · POST /api/companies/8/positions 201 | "เพิ่มบริษัท IT Test Co. แล้ว" → "เพิ่มตำแหน่ง Tester แล้ว" · Tester ว่าง 2/2 | ✅ |
| IT-UA-03 | ไม่ใส่วันหมดเขต: ไม่มี request → POST /api/matching/rounds 201 | "กรุณากำหนดวันหมดเขตยืนยันสิทธิ์" → "เปิดรอบที่ 1 แล้ว" · "รอบที่ 1 เปิดจับคู่" ปุ่มจับคู่อัตโนมัติขึ้น ² | ✅ |
| IT-UA-04 | POST /api/matching/auto 200 | "จับคู่ได้ 3 คน · จับไม่ได้ 1 คน" · "ณัฐพล เลือกงาน: ยังไม่ได้เลือกอันดับ" · พิมพ์ชนก SCB Backend อันดับ 1 · ธนกร LINE MAN อันดับ 2 · กมลชนก Bitkub อันดับ 1 | ✅ |
| IT-UA-05 | GET /api/requests/me 200 (ไม่มี GET /api/companies) | "ยังไม่มีใบสมัครที่ได้รับการอนุมัติ" | ✅ |
| IT-UA-06 | GET requests/me 200 · GET companies 200 · GET matches/me/summary 404 · GET applications/3/preferences 200 | dropdown 5 อันดับ จัดกลุ่มตามบริษัท 7 กลุ่ม · ไม่มี PTT Digital · ไม่แสดงผลจับคู่ | ✅ |
| IT-UA-07 | PUT /api/applications/3/preferences ด้วยตำแหน่งที่เต็ม → 400 INVALID_PREFERENCE | "Backend Developer (โควตาเต็ม 0/1)" เลือกไม่ได้ (LINE MAN, Bitkub ที่จับคู่แล้วก็ขึ้นโควตาเต็มเช่นกัน) | ✅ |
| IT-UA-08 | ไม่มี request | "เลือกตำแหน่งเดียวกันซ้ำหลายอันดับไม่ได้" / "กรุณาเลือกอันดับให้ต่อเนื่อง เริ่มจากอันดับ 1" | ✅ |
| IT-UA-09 | PUT /api/applications/3/preferences 200 | "บันทึก 2 อันดับแล้ว" · โหลดหน้าใหม่ยังเป็น Tester, QA Engineer | ✅ |
| IT-UA-10 | POST /api/matches (ณัฐพล → SCB Backend) 409 QUOTA_FULL | – | ✅ |
| IT-UA-11 | GET /api/matching/candidates?positionId=8 200 · POST /api/matches 201 | รายชื่อผู้เลือก Tester มีณัฐพล → "จับคู่ ณัฐพล เลือกงาน กับ Tester แล้ว" · ตารางผลเพิ่มณัฐพล · Tester ว่าง 1/2 | ✅ |
| IT-UA-12 | DELETE /api/matches/3 200 | "ยกเลิกผลการจับคู่แล้ว คืนโควตาให้ตำแหน่งแล้ว" · สถานะ "ยกเลิก" · Bitkub ว่าง 1/1 | ✅ |
| IT-UA-13 | PUT preferences (กมลชนก) 200 · POST /api/matches 201 · PUT /api/positions/8 400 | "ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (2 คน) ไม่ได้" ³ | ✅ |
| IT-UA-14 | GET /api/matches/me/summary 404 | ไม่แสดงผลจับคู่ แสดงเฉพาะส่วนเลือกอันดับ | ✅ |
| IT-UA-15 | POST /api/matching/rounds/1/announce 200 | "ประกาศผลรอบที่ 1 แล้ว ระบบแจ้งเตือนนักศึกษาในรอบนี้แล้ว" · ปุ่มจับคู่ / ประกาศหาย · "รอบที่ 1 ประกาศผลแล้ว" | ✅ |
| IT-UA-16 | GET /api/matches/me/summary 200 · GET /api/notifications 200 | "คุณได้รับการจับคู่กับ ตำแหน่ง Backend Developer บริษัท SCB TechX (อันดับ 1)" · อันดับ 1 "ได้อันดับนี้" อันดับอื่น "ไม่ได้อันดับนี้" · ปุ่มยืนยัน / สละ · แจ้งเตือน "ประกาศผลการจับคู่ รอบที่ 1" ² | ✅ |
| IT-UA-17 | POST /api/matches/1/respond 200 CONFIRMED | "ยืนยันสิทธิ์เรียบร้อยแล้ว" · staff01 ได้แจ้งเตือน "นักศึกษายืนยันสิทธิ์" | ✅ |
| IT-UA-18 | ไม่ใส่เหตุผล: ไม่มี request → POST /api/matches/2/respond 200 DECLINED | "กรุณากรอกเหตุผลที่สละสิทธิ์" → "สละสิทธิ์เรียบร้อยแล้ว คุณแก้อันดับเพื่อรอรอบถัดไปได้" · LINE MAN ว่าง 1/1 · staff01 ได้แจ้งเตือน "นักศึกษาสละสิทธิ์" | ✅ |
| IT-UA-19 | POST /api/matching/rounds 409 | "รอบที่ 1 ยังไม่จบ (ต้องประกาศผลและเลยวันหมดเขตยืนยันสิทธิ์ก่อน)" | ✅ |
| IT-UA-20 | POST respond 409 DEADLINE_PASSED (ณัฐพล หลังหมดเขต) | "เลยกำหนดยืนยันสิทธิ์แล้ว" | ✅ |
| IT-UA-21 | POST /api/matching/rounds 201 | "เปิดรอบที่ 2 แล้ว" · ผลของณัฐพลและกมลชนกเป็น DECLINED "ไม่ตอบภายในกำหนด" · แต่รายการบริษัทยังแสดง Tester "ว่าง 0" ต้องโหลดหน้าใหม่จึงเป็น 2/2 (API ตอบ 2/2 ถูกต้อง) · **Bug B2** | ⚠️ ผ่านเมื่อโหลดหน้าใหม่ |
| IT-UA-22 | POST /api/matching/auto (X-User-Id นักศึกษา) 403 | ธนกรเปิด `staff-matching.html` ขึ้น "หน้านี้สำหรับเจ้าหน้าที่เท่านั้น" | ✅ |
| IT-UA-23 | หยุด container `app` → ผ่าน proxy ทดสอบได้ 502 (ไม่ใช่ network error) | "เกิดข้อผิดพลาด (502)" หน้าไม่ค้าง · กรณี network error จริง (8081 → 8080) ยังไม่ได้ตรวจในรอบนี้ ⁴ | ⚠️ ตรวจได้บางส่วน |

**สรุป: ผ่าน 21 / 23 ข้อ · ผ่านเมื่อโหลดหน้าใหม่ 1 ข้อ (IT-UA-21) · ตรวจได้บางส่วน 1 ข้อ (IT-UA-23)**

Bug / ข้อสังเกตที่พบ
-
| ID | ระดับ | รายละเอียด | ทางแก้ที่เสนอ |
|---|---|---|---|
| B1 | สูง | **เขตเวลาไม่ตรงกัน:** container `app` ใช้เวลา UTC แต่หน้าเว็บส่งวันหมดเขตจากช่อง `datetime-local` เป็นเวลาไทย และแสดงเวลาที่ได้จาก server ตรง ๆ เช่น เจ้าหน้าที่กรอกหมดเขต 18:00 น. ระบบจะนับเป็น 18:00 UTC (= 01:00 น. วันถัดไป) · เวลาบนหน้าแจ้งเตือน / ใบสมัครเร็วกว่าเวลาจริง 7 ชั่วโมง (แจ้งเตือนที่สร้างตอน 00:24 น. แสดง 17:24) | ตั้ง `TZ: Asia/Bangkok` ใน environment ของ service `app` ใน `docker-compose.yml` (หรือ `-Duser.timezone=Asia/Bangkok`) |
| B2 | ต่ำ | หลังเปิดรอบใหม่ หน้า `staff-matching.html` ไม่โหลดรายการบริษัทใหม่ ที่ว่างที่คืนจากผลที่ไม่ตอบจึงยังไม่ขึ้นจนกว่าจะโหลดหน้าใหม่ | ใน `openRound()` ของ `u6.js` เรียก `loadCompanies()` ต่อจาก `loadRound()` |
| O1 | ข้อสังเกต | IT-UA-13: บันทึกโควตาไม่สำเร็จแล้ว ช่องโควตายังแสดงค่าที่พิมพ์ (1) ไม่กลับเป็นค่าจริง (2) | ตั้งค่าช่องกลับเมื่อ API ตอบ error |

² เพราะ B1 ตอนทดสอบจึงกรอกวันหมดเขตเป็นเวลา UTC (+12 นาที) เพื่อให้หมดเขตจริงภายในรอบทดสอบ · หน้าเว็บจึงแสดง "หมดเขตยืนยันสิทธิ์ 13 พ.ย. 2569 17:33" (= 00:33 น. เวลาไทย)
³ ก่อนแก้โควตา กมลชนกบันทึกอันดับเป็น Tester แล้วเจ้าหน้าที่จับคู่เข้า Tester (Tester ว่าง 0/2)
⁴ proxy ทดสอบตอบ 502 เมื่อ backend ไม่ทำงาน หน้าเว็บจึงขึ้น "เกิดข้อผิดพลาด (502)" แทน "ไม่สามารถเชื่อมต่อระบบได้…" ควรตรวจซ้ำที่ `localhost:8081` (หยุด `app` แล้วเปิดหน้า) · ข้อสังเกตเพิ่ม: ตอน backend ไม่ทำงาน หน้าเจ้าหน้าที่ยังแสดง "ยังไม่มีรอบการจับคู่ เปิดรอบแรกได้ด้านล่าง" ซึ่งอาจทำให้เข้าใจผิด


# ส่วนที่ 6 · ผล Integration Test API–DAO

Artefact: รายงานผล Integration Test · อ้างอิง test cases: ส่วน "Design Integration Test API–DAO" ในไฟล์นี้

สภาพแวดล้อม
-
- วันที่รัน: 13 พ.ย. 2569 เวลา 23:48–00:01 น. · branch `Develop` (`06c385f`)
- `docker compose down -v` แล้ว `docker compose up -d --build` (MySQL 8.4 + `MatchingDemoSeeder`)
- ส่ง request ตรงไปที่ `http://localhost:8080` พร้อม header `X-User-Id` (staff01 = 1 · 6609650001 = 3 · 6609650011–14 = 11–14) · ตรวจข้อมูลหลังทุกข้อด้วย GET API ของระบบ แล้วยืนยันด้วย SQL ท้ายส่วนนี้
- เวลาในฐานข้อมูลเป็น UTC (เวลาไทย = UTC + 7)

ผลการรัน
-
| TC ID | Actual response | Actual ในฐานข้อมูล | ผล |
|---|---|---|---|
| IT-AD-01 | 201 `status` OPEN (id 8 "IT Test Co.") | `companies` 6 → 7 แถว | ✅ ผ่าน |
| IT-AD-02 | 201 Tester โควตา 2 | `quota` = 2 · `available_slots` = 2 | ✅ ผ่าน |
| IT-AD-03 | 400 VALIDATION_ERROR "โควตาต้องเป็นจำนวนเต็ม 1–999" | ตำแหน่งของบริษัท 1 → 1 (ไม่มีแถวใหม่) | ✅ ผ่าน |
| IT-AD-04 | 201 รอบ 1 OPEN | `matching_rounds` round_no 1 OPEN · `confirm_deadline` 2026-11-13 16:56:42 UTC (23:56 น.) | ✅ ผ่าน |
| IT-AD-05 | 404 NOT_FOUND "ยังไม่ประกาศผลการจับคู่" | – | ✅ ผ่าน |
| IT-AD-06 | 200 matched 3 · unmatched 1 (ณัฐพล: ยังไม่ได้เลือกอันดับ) | `matches` +3 PROPOSED: พิมพ์ชนก → SCB Backend `rank_no` 1 · ธนกร → LINE MAN `rank_no` 2 · กมลชนก → Bitkub `rank_no` 1 · `available_slots` SCB Backend / LINE MAN / Bitkub = 0 / 0 / 0 | ✅ ผ่าน |
| IT-AD-07 | 409 QUOTA_FULL "ตำแหน่งนี้เต็มหรือปิดรับแล้ว" | `matches` 3 → 3 | ✅ ผ่าน |
| IT-AD-08 | 200 CANCELLED | Bitkub `available_slots` 0 → 1 | ✅ ผ่าน |
| IT-AD-09 | 200 ANNOUNCED | `announced_at` มีค่า · แจ้งเตือน "ประกาศผลการจับคู่ รอบที่ 1" พิมพ์ชนก 1 · ธนกร 1 · กมลชนก 1 ("รอบนี้คุณยังไม่ได้รับการจับคู่") · ณัฐพล 0 | ✅ ผ่าน |
| IT-AD-10 | announce ซ้ำ 409 INVALID_STATUS · auto 409 NO_OPEN_ROUND | แจ้งเตือนและ `matches` ไม่เพิ่ม | ✅ ผ่าน |
| IT-AD-11 | 200 · 200 | `company_preferences` ของใบ 3: [Tester, SCB Data Analyst] → [Tester] | ✅ ผ่าน |
| IT-AD-12 | 400 INVALID_PREFERENCE ทั้ง 4 แบบ: เต็ม "ตำแหน่ง Backend Developer โควตาเต็มแล้ว เลือกไม่ได้" · ปิดรับ "ตำแหน่ง Closed Role ปิดรับแล้ว" · ซ้ำ "เลือกตำแหน่งเดียวกันซ้ำหลายอันดับไม่ได้" · 6 อันดับ "กรุณาเลือกตำแหน่ง 1–5 อันดับ" | อันดับเดิม [Tester] ไม่เปลี่ยน | ✅ ผ่าน |
| IT-AD-13 | 6609650001: 409 APPLICATION_NOT_APPROVED · พิมพ์ชนก: 409 ALREADY_MATCHED | ไม่เปลี่ยน | ✅ ผ่าน |
| IT-AD-14 | 403 FORBIDDEN | อันดับของณัฐพลยังเป็น [Tester] | ✅ ผ่าน |
| IT-AD-15 | 200 CONFIRMED | `responded_at` มีค่า · แจ้งเตือนของ staff01 0 → 1 | ✅ ผ่าน |
| IT-AD-16 | ไม่มีเหตุผล 400 REASON_REQUIRED → มีเหตุผล 200 DECLINED | `decline_reason` = "ได้ที่ฝึกงานที่อื่นแล้ว" · LINE MAN `available_slots` 0 → 1 | ✅ ผ่าน |
| IT-AD-17 | 403 FORBIDDEN | ไม่เปลี่ยน | ✅ ผ่าน |
| IT-AD-18 | 403 FORBIDDEN | `matches` 3 → 3 | ✅ ผ่าน |
| IT-AD-19 | 409 ROUND_NOT_CLOSED "รอบที่ 1 ยังไม่จบ (ต้องประกาศผลและเลยวันหมดเขตยืนยันสิทธิ์ก่อน)" | ยังเป็นรอบ 1 ANNOUNCED | ✅ ผ่าน |
| IT-AD-20 | 409 DEADLINE_PASSED "เลยกำหนดยืนยันสิทธิ์แล้ว" (ณัฐพลยืนยันผลรอบ 2 หลัง 17:00:12 UTC) ¹ | ไม่เปลี่ยน | ✅ ผ่าน |
| IT-AD-21 | 201 · 201 ¹ | เปิดรอบ 2 หลังรอบ 1 หมดเขต: รอบ 2 OPEN · เปิดรอบ 3 หลังรอบ 2 หมดเขต: ผลรอบ 2 ที่ยัง PROPOSED (ณัฐพล, กมลชนก) เป็น DECLINED `decline_reason` = "ไม่ตอบภายในกำหนด" · Tester `available_slots` 0 → 2 · รอบ 3 OPEN | ✅ ผ่าน |
| IT-AD-22 | 400 QUOTA_BELOW_MATCHED "ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (2 คน) ไม่ได้" | รอบ 2 จับคู่ 2 คนเข้า Tester แล้ว `quota` ยังเป็น 2 | ✅ ผ่าน |

**สรุป: ผ่าน 22 / 22 ข้อ · ไม่พบ bug**

หมายเหตุ / สิ่งที่ต่างจากที่ออกแบบไว้
-
- IT-AD-04 ตั้ง `confirm_deadline` +8 นาที (แทน +20 นาที) เพื่อลดเวลารอ IT-AD-19 ยังรันก่อนหมดเขต และ IT-AD-20, 21 รันหลังหมดเขตตามลำดับเดิม
- IT-AD-12 กรณี "ปิดรับ": PTT Digital ไม่มีตำแหน่งให้เลือก จึงสร้างตำแหน่ง "Closed Role" สถานะ CLOSED ใน IT Test Co. แล้วใช้ทดสอบแทน
- ¹ IT-AD-20, 21: ในรอบ 1 ไม่มีผล PROPOSED เหลือ (หลัง IT-AD-15 / 16: พิมพ์ชนก CONFIRMED · ธนกร DECLINED · กมลชนก CANCELLED) การตอบผลรอบ 1 หลังหมดเขตได้ 409 INVALID_STATUS เพราะระบบตรวจสถานะก่อนวันหมดเขต จึงทดสอบเพิ่มในรอบ 2 ที่มีผล PROPOSED ค้างอยู่ (ณัฐพลและกมลชนกจาก IT-AD-22) และให้รอบ 2 หมดเขต 17:00:12 UTC · ควรปรับ Design ของ IT-AD-20 / 21 ให้มีผลที่ไม่ตอบค้างอยู่ในรอบ 1

ข้อมูลในฐานข้อมูลหลังรันครบ (SQL ในส่วนที่ 2)
-
รันเมื่อ 14 พ.ย. 2569 00:03 น. หลังรันครบ 22 ข้อ (ข้อความภาษาไทยขึ้นเป็น `?` เพราะ console ของ mysql ไม่ได้ตั้ง charset ข้อมูลจริงเป็นภาษาไทยปกติ ตามที่ API ตอบกลับในตารางผลข้างบน)

```text
id      position_name   quota   available_slots status
1       Backend Developer       1       0       OPEN
2       Data Analyst    2       2       OPEN
3       Software Engineer       2       2       OPEN
4       QA Engineer     1       1       OPEN
5       Frontend Developer      1       1       OPEN
6       Blockchain Developer Intern     1       1       OPEN
7       Data Engineer   2       2       OPEN
8       Tester  2       2       OPEN
9       Closed Role     1       1       CLOSED

application_id  position_id     rank_no
3       8       1
4       1       1
4       3       2
4       4       3
5       1       1
5       5       2
6       6       1
6       7       2

id      round_no        status  confirm_deadline        announced_at
1       1       CLOSED  2026-11-13 16:56:42.000000      2026-11-13 16:49:01.244435
2       2       CLOSED  2026-11-13 17:00:12.000000      2026-11-13 16:57:12.580432
3       3       OPEN    2026-11-13 18:00:29.000000      NULL

id      application_id  position_id     round_id        rank_no status  decline_reason
1       4       1       1       1       CONFIRMED       NULL
2       5       5       1       2       DECLINED        (ได้ที่ฝึกงานที่อื่นแล้ว)
3       6       6       1       1       CANCELLED       NULL
4       3       8       2       1       DECLINED        (ไม่ตอบภายในกำหนด)
5       6       8       2       NULL    DECLINED        (ไม่ตอบภายในกำหนด)

user_id type    title
12      MATCH_ANNOUNCED (ประกาศผลการจับคู่ รอบที่ 1)
13      MATCH_ANNOUNCED (ประกาศผลการจับคู่ รอบที่ 1)
14      MATCH_ANNOUNCED (ประกาศผลการจับคู่ รอบที่ 1)
1       MATCH_RESPONDED (นักศึกษายืนยันสิทธิ์)
1       MATCH_RESPONDED (นักศึกษาสละสิทธิ์)
11      MATCH_ANNOUNCED (ประกาศผลการจับคู่ รอบที่ 2)
14      MATCH_ANNOUNCED (ประกาศผลการจับคู่ รอบที่ 2)
13      MATCH_ANNOUNCED (ประกาศผลการจับคู่ รอบที่ 2)
```

ตรวจกับผลที่คาดไว้
-
- `company_positions`: ทุกตำแหน่งได้โควตาคืนครบ ยกเว้น SCB Backend (`available_slots` 0) ที่พิมพ์ชนก CONFIRMED · Tester `quota` ยัง 2 (IT-AD-22) · Closed Role เป็น CLOSED
- `company_preferences`: ใบ 3 (ณัฐพล) เหลือ 1 อันดับ (IT-AD-11 · 12 · 14 ไม่ทำให้เปลี่ยน) · ใบ 4 (พิมพ์ชนก) ไม่ถูกแก้ (IT-AD-13)
- `matching_rounds`: รอบ 1, 2 CLOSED มี `announced_at` · รอบ 3 OPEN (IT-AD-04, 09, 21)
- `matches`: 1 CONFIRMED (IT-AD-15) · 2 DECLINED พร้อมเหตุผล (IT-AD-16) · 3 CANCELLED (IT-AD-08) · 4, 5 DECLINED "ไม่ตอบภายในกำหนด" (IT-AD-21) · `rank_no` ของ 5 เป็น NULL เพราะเจ้าหน้าที่จับคู่เองกับตำแหน่งที่กมลชนกไม่ได้เลือกไว้
- `notifications`: รอบ 1 แจ้ง 3 คน ณัฐพลไม่ได้แจ้งเพราะไม่ได้เลือกอันดับ (IT-AD-09) · staff01 ได้ MATCH_RESPONDED 2 ครั้ง (IT-AD-15, 16) · รอบ 2 แจ้ง 3 คนที่มีผลหรือเลือกอันดับไว้

> ข้อความใน ( ) คือค่าที่ API ตอบกลับ แทนตัว `?` ที่ console แสดง · ครั้งต่อไปเพิ่ม `--default-character-set=utf8mb4` ในคำสั่ง `mysql` จะเห็นภาษาไทย


# ส่วนที่ 7 · ผล System Test

Artefact: รายงานผล System Test · อ้างอิง test cases: ส่วน "Design System Test" ในไฟล์นี้

สภาพแวดล้อม
-
- วันที่รัน: 14 พ.ย. 2569 เวลา 00:38–00:54 น. · branch `Develop` (`06c385f`) · เริ่มจาก `docker compose down -v` แล้ว `up -d --build` อีกครั้ง (แยกจากส่วนที่ 5)
- หน้าเว็บผ่าน container ทดสอบ `cs261-u6-proxy` (พอร์ต 8082) แบบเดียวกับส่วนที่ 5 · ตรวจ 3 จุด: ข้อความบนหน้าจอ · แท็บ Network · ข้อมูลในระบบผ่าน GET API · อีเมลดูที่ Mailpit (`localhost:8025`)
- ลบ container `cs261-u6-proxy` แล้วหลังรันเสร็จ

ผลการรัน
-
| TC ID | หน้าจอ | API (Network) | ข้อมูลในระบบ | ผล |
|---|---|---|---|---|
| ST-U6-01 | "เพิ่มบริษัท IT Test Co. แล้ว" · "เพิ่มตำแหน่ง Tester แล้ว" | POST /api/companies 201 · POST /api/companies/8/positions 201 | Tester 2/2 | ✅ |
| ST-U6-02 | "เปิดรอบที่ 1 แล้ว" · "รอบที่ 1 เปิดจับคู่" · ปุ่มจับคู่อัตโนมัติขึ้น | POST /api/matching/rounds 201 | รอบ 1 OPEN หมดเขต 17:51 UTC ² | ✅ |
| ST-U6-03 | "จับคู่ได้ 3 คน · จับไม่ได้ 1 คน" · "ณัฐพล เลือกงาน: ยังไม่ได้เลือกอันดับ" · ธนกรได้ LINE MAN อันดับ 2 | POST /api/matching/auto 200 | PROPOSED 3 แถว · SCB Backend / LINE MAN / Bitkub ว่าง 0/1 | ✅ |
| ST-U6-04 | ไม่แสดงผลจับคู่ · กดบันทึกอันดับขึ้น "คุณได้รับการจับคู่แล้ว แก้อันดับไม่ได้" | GET /api/matches/me/summary 404 · PUT preferences 409 ALREADY_MATCHED | – | ✅ |
| ST-U6-05 | "ยกเลิกผลการจับคู่แล้ว คืนโควตาให้ตำแหน่งแล้ว" · Bitkub ว่าง 1 → "จับคู่ได้ 1 คน · จับไม่ได้ 1 คน" (ณัฐพล) | DELETE /api/matches/3 200 · POST /api/matching/auto 200 | แถวเดิม CANCELLED · กมลชนก → Bitkub PROPOSED แถวใหม่ | ✅ |
| ST-U6-06 | "ประกาศผลรอบที่ 1 แล้ว ระบบแจ้งเตือนนักศึกษาในรอบนี้แล้ว" | POST /api/matching/rounds/1/announce 200 | แจ้งเตือน "ประกาศผลการจับคู่ รอบที่ 1" ของพิมพ์ชนก ธนกร กมลชนก (ณัฐพลไม่มี) · Mailpit ได้อีเมล 3 ฉบับ (pimchanok@, thanakorn@, kamonchanok@) | ✅ |
| ST-U6-07 | "คุณได้รับการจับคู่กับ ตำแหน่ง Backend Developer บริษัท SCB TechX (อันดับ 1)" · อันดับ 1 "ได้อันดับนี้" อันดับ 2–3 "ไม่ได้อันดับนี้" · ปุ่มยืนยัน / สละ | GET /api/matches/me/summary 200 · GET /api/notifications 200 | แจ้งเตือนบนสุด "ประกาศผลการจับคู่ รอบที่ 1" | ✅ |
| ST-U6-08 | "ยืนยันสิทธิ์เรียบร้อยแล้ว" | POST /api/matches/1/respond 200 | CONFIRMED · staff01 ได้แจ้งเตือน "นักศึกษายืนยันสิทธิ์" | ✅ |
| ST-U6-09 | "กรุณากรอกเหตุผลที่สละสิทธิ์" → "สละสิทธิ์เรียบร้อยแล้ว คุณแก้อันดับเพื่อรอรอบถัดไปได้" | ครั้งแรกไม่มี request · POST /api/matches/2/respond 200 | DECLINED · LINE MAN 1/1 · staff01 ได้แจ้งเตือน "…เหตุผล: ได้ที่ฝึกงานอื่นแล้ว" | ✅ |
| ST-U6-10 | Backend Developer "(โควตาเต็ม 0/1)" เลือกไม่ได้ · "บันทึก 2 อันดับแล้ว" | PUT /api/applications/3/preferences 200 | อันดับ 1 Tester · 2 QA Engineer | ✅ |
| ST-U6-11 | "รอบที่ 1 ยังไม่จบ (ต้องประกาศผลและเลยวันหมดเขตยืนยันสิทธิ์ก่อน)" | POST /api/matching/rounds 409 | ยังเป็นรอบ 1 | ✅ |
| ST-U6-12 | "เลยกำหนดยืนยันสิทธิ์แล้ว" | POST /api/matches/4/respond 409 | ไม่เปลี่ยน | ✅ |
| ST-U6-13 | "เปิดรอบที่ 2 แล้ว" | POST /api/matching/rounds 201 | รอบ 1 ปิด · กมลชนก DECLINED "ไม่ตอบภายในกำหนด" · Bitkub คืนเป็น 1/1 · รอบ 2 OPEN | ✅ |
| ST-U6-14 | auto รอบ 2: "จับคู่ได้ 3 คน · จับไม่ได้ 0 คน" (ณัฐพล → Tester อันดับ 1) · candidates ของ Tester มีกมลชนกอันดับ 1 → "จับคู่ กมลชนก ใฝ่รู้ กับ Tester แล้ว" ⁵ | POST /api/matching/auto 200 · GET candidates 200 · POST /api/matches 201 | Tester 0/2 | ⚠️ ผ่านเมื่อปรับขั้นตอน |
| ST-U6-15 | "ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (2 คน) ไม่ได้" | PUT /api/positions/8 400 | `quota` ยังเป็น 2 | ✅ |
| ST-U6-16 | ณัฐพลเปิด `staff-matching.html` ขึ้น "หน้านี้สำหรับเจ้าหน้าที่เท่านั้น" | POST /api/matching/auto (X-User-Id นักศึกษา) 403 | ไม่มีแถวใหม่ | ✅ |
| ST-U6-17 | "เกิดข้อผิดพลาด (502)" หน้าไม่ค้าง ⁴ | proxy ทดสอบตอบ 502 | – | ⚠️ ตรวจได้บางส่วน |

**สรุป: ผ่าน 15 / 17 ข้อ · ผ่านเมื่อปรับขั้นตอน 1 ข้อ (ST-U6-14) · ตรวจได้บางส่วน 1 ข้อ (ST-U6-17)** · Bug ที่พบซ้ำกับส่วนที่ 5: B1 (เขตเวลา)

⁵ จับคู่อัตโนมัติรอบ 2 จับกมลชนกเข้า Bitkub อีกครั้ง (อันดับเดิมของเธอ) จึงบันทึกอันดับเป็น Tester ไม่ได้ (409 ALREADY_MATCHED) ตามที่ Design คาด · ปรับขั้นตอนโดยให้เจ้าหน้าที่ยกเลิกผล Bitkub ของกมลชนกก่อน แล้วจึงบันทึกอันดับ Tester และจับคู่เอง · ควรแก้ Design ST-U6-14 ให้ตรงกับพฤติกรรมนี้

ข้อสังเกต
-
- **O2:** รอบ 2 จับคู่อัตโนมัติให้ธนกรกลับไป LINE MAN Frontend ตำแหน่งเดิมที่เพิ่งสละสิทธิ์ไปพร้อมเหตุผล "ได้ที่ฝึกงานอื่นแล้ว" เพราะอันดับเดิมยังอยู่ · ควรตกลงกับทีม / PO ว่าคนที่สละสิทธิ์ควรถูกข้าม หรือต้องเลือกอันดับใหม่ก่อนจึงจะจับคู่ได้อีก
- ข้อจำกัดที่ทีมรับรู้แล้ว: ก่อนประกาศผล นักศึกษาที่ถูกจับคู่แล้วกดบันทึกอันดับจะเห็น "คุณได้รับการจับคู่แล้ว แก้อันดับไม่ได้" (ST-U6-04)


# ส่วนที่ 8 · ผล UAT

Artefact: รายงานผล UAT พร้อม feedback · อ้างอิง test cases: ส่วน "Design UAT" ในไฟล์นี้

> ยังไม่ได้รัน
