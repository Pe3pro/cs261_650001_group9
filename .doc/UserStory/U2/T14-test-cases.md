# U2:T14 Design Integration Test ระหว่าง API กับ DAO

## 1. วัตถุประสงค์

ออกแบบ Integration Test Cases สำหรับตรวจสอบการทำงานร่วมกันระหว่าง REST API, Service และ DAO โดยตรวจว่า API สามารถรับข้อมูล ประมวลผล และสั่งให้ DAO บันทึกผลลงฐานข้อมูลได้อย่างถูกต้อง

รวมถึงตรวจสอบว่าข้อมูลที่กรอกไม่ครบหรือผิดรูปแบบจะไม่ถูกบันทึกลงฐานข้อมูล

## 2. ส่วนประกอบที่ทดสอบร่วมกัน

- API: `EligibilityController`
- Service: `EligibilityService`
- DAO: `EligibilityRecordDao`
- Entity: `EligibilityRecord`
- Database table: `eligibility_records`
- Endpoint: `POST /api/eligibility/check`

## 3. Scenario API เรียก DAO

ลำดับการทำงานที่ต้องทดสอบ:

1. Client ส่ง HTTP POST Request ไปยัง API
2. `EligibilityController` รับ JSON Request
3. Controller เรียก `EligibilityService`
4. Service ตรวจสอบความครบถ้วนและรูปแบบข้อมูล
5. ถ้าข้อมูลถูกต้อง Service ประเมินคุณสมบัติ
6. Service สร้าง `EligibilityRecord`
7. Service เรียก `EligibilityRecordDao.save()`
8. DAO บันทึกผลลงตาราง `eligibility_records`
9. API ส่ง Response กลับไปยัง Client

เงื่อนไขสำคัญ:

- ข้อมูลถูกต้องและผ่านคุณสมบัติ ต้องบันทึก `eligible = true`
- ข้อมูลถูกต้องแต่ไม่ผ่านคุณสมบัติ ต้องบันทึก `eligible = false`
- ข้อมูลไม่ครบ ต้องตอบ HTTP 400 และไม่เรียก DAO เพื่อบันทึก
- ข้อมูลผิดรูปแบบ ต้องตอบ HTTP 400 และไม่เรียก DAO เพื่อบันทึก

## 4. ข้อมูลมาตรฐานที่ใช้ทดสอบ

```json
{
  "studentId": "6410000001",
  "curriculum": "61",
  "gpax": "3.10",
  "grades": {
    "คพ.101": "B",
    "คพ.102": "B",
    "คพ.111": "B",
    "คพ.213": "B",
    "คพ.251": "B",
    "คพ.264": "B"
  },
  "courseStatuses": {
    "คพ.384": "กำลังศึกษา",
    "คพ.266": "กำลังศึกษา"
  },
  "acknowledgements": [
    true,
    true,
    true
  ]
}
```

---

## 5. Integration Test Cases

### API-DAO-01: ข้อมูลถูกต้องและผ่านคุณสมบัติ

| รายการ | รายละเอียด |
|---|---|
| Test Case ID | API-DAO-01 |
| Input | ข้อมูลมาตรฐาน โดย GPA เท่ากับ `3.10` |
| ขั้นตอน | บันทึกจำนวนแถวเดิม ส่ง POST Request แล้วค้นหารายการล่าสุดจาก DAO |
| Expected API Result | HTTP `200 OK`, `valid = true`, `eligible = true`, `message = ผ่านคุณสมบัติ` |
| Expected DAO Result | DAO บันทึกข้อมูลเพิ่ม 1 รายการ |
| Expected Database Result | `student_id = 6410000001`, `eligible = true`, `message = ผ่านคุณสมบัติ` และมีค่า `checked_at` |

### API-DAO-02: ข้อมูลถูกต้องแต่ไม่ผ่านคุณสมบัติ

| รายการ | รายละเอียด |
|---|---|
| Test Case ID | API-DAO-02 |
| Input | ข้อมูลมาตรฐาน แต่กำหนด GPA เป็น `2.74` |
| ขั้นตอน | ส่ง POST Request แล้วค้นหารายการล่าสุดจาก DAO |
| Expected API Result | HTTP `200 OK`, `valid = true`, `eligible = false`, `message = ไม่ผ่านคุณสมบัติ` และมีเหตุผลว่า GPA ต้องไม่ต่ำกว่า 2.75 |
| Expected DAO Result | DAO บันทึกข้อมูลเพิ่ม 1 รายการ |
| Expected Database Result | `student_id = 6410000001`, `eligible = false`, `message = ไม่ผ่านคุณสมบัติ` |

### API-DAO-03: กรอกข้อมูลไม่ครบ

| รายการ | รายละเอียด |
|---|---|
| Test Case ID | API-DAO-03 |
| Input | `{}` หรือ Request ที่ไม่มี `studentId`, `gpax`, `grades`, `courseStatuses` และ `acknowledgements` |
| ขั้นตอน | บันทึกจำนวนแถวก่อนทดสอบ ส่ง POST Request แล้วตรวจจำนวนแถวหลังทดสอบ |
| Expected API Result | HTTP `400 Bad Request`, `valid = false`, `eligible = null`, `message = ข้อมูลไม่ถูกต้อง` และมีรายการ `errors` |
| Expected DAO Result | Service ไม่เรียก `EligibilityRecordDao.save()` |
| Expected Database Result | จำนวนแถวในฐานข้อมูลไม่เพิ่มขึ้น |

### API-DAO-04: กรอกข้อมูลผิดรูปแบบ

| รายการ | รายละเอียด |
|---|---|
| Test Case ID | API-DAO-04 |
| Input | `studentId = ABC123`, `gpax = 3.10abc`, เกรดหนึ่งวิชาเป็น `Z` หรือสถานะวิชาไม่อยู่ในค่าที่อนุญาต |
| ขั้นตอน | บันทึกจำนวนแถวก่อนทดสอบ ส่ง POST Request แล้วตรวจจำนวนแถวหลังทดสอบ |
| Expected API Result | HTTP `400 Bad Request`, `valid = false`, `eligible = null` และมีข้อความระบุฟิลด์ที่ผิดรูปแบบ |
| Expected DAO Result | Service ไม่เรียก `EligibilityRecordDao.save()` |
| Expected Database Result | จำนวนแถวในฐานข้อมูลไม่เพิ่มขึ้น |

### API-DAO-05: การเรียก API แต่ละครั้งบันทึกเพียงหนึ่งรายการ

| รายการ | รายละเอียด |
|---|---|
| Test Case ID | API-DAO-05 |
| Input | ข้อมูลมาตรฐานที่ถูกต้องจำนวน 1 Request |
| ขั้นตอน | ตรวจจำนวนแถวก่อนส่ง Request ส่ง Request หนึ่งครั้ง แล้วตรวจจำนวนแถวอีกครั้ง |
| Expected API Result | API ตอบกลับ HTTP `200 OK` หนึ่งครั้ง |
| Expected DAO Result | `save()` ถูกเรียกหนึ่งครั้ง |
| Expected Database Result | จำนวนแถวเพิ่มขึ้นเพียง 1 รายการ |

### API-DAO-06: ข้อมูลที่ DAO บันทึกตรงกับผลจาก API

| รายการ | รายละเอียด |
|---|---|
| Test Case ID | API-DAO-06 |
| Input | ข้อมูลมาตรฐานที่ถูกต้อง |
| ขั้นตอน | ส่ง POST Request แล้วใช้ DAO ค้นหารายการล่าสุดของ `studentId = 6410000001` |
| Expected API Result | API ส่งค่า `eligible` และ `message` กลับมาตามผลการประเมิน |
| Expected DAO Result | ค่า `studentId`, `eligible` และ `message` ใน Entity ตรงกับ API Response |
| Expected Database Result | รายการล่าสุดมีวันและเวลาตรวจสอบ และค้นหาด้วยรหัสนักศึกษาได้ |

## 6. เกณฑ์การผ่าน

Integration Test ผ่านเมื่อ:

- API เรียก Service และ DAO ตามลำดับที่กำหนด
- Request ที่มีข้อมูลถูกต้องถูกบันทึกเพียงหนึ่งรายการ
- ผลผ่านคุณสมบัติถูกบันทึกเป็น `eligible = true`
- ผลไม่ผ่านคุณสมบัติถูกบันทึกเป็น `eligible = false`
- กรณีกรอกไม่ครบไม่ถูกบันทึกลงฐานข้อมูล
- กรณีกรอกผิดรูปแบบไม่ถูกบันทึกลงฐานข้อมูล
- ข้อมูลที่ DAO บันทึกตรงกับ Response ของ API

## 7. สรุป

ออกแบบ Integration Test ระหว่าง API กับ DAO จำนวน 6 กรณี โดยระบุ Scenario API เรียก Service และ DAO อย่างชัดเจน พร้อมครอบคลุมกรณีผ่านคุณสมบัติ ไม่ผ่านคุณสมบัติ กรอกข้อมูลไม่ครบ กรอกข้อมูลผิดรูปแบบ และตรวจความถูกต้องของข้อมูลที่บันทึก

ผลเทส
-
![alt text](<U2-14-test API-DOA.png>)