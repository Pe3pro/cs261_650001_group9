/* =====================================================================
   U3:T7 Unit test หน้า UI ยื่นคำร้อง (frontend/submit.html + js/script.js ส่วน initSubmit)
   ตาม T7.md (UI-01 ถึง UI-18) + เพิ่มเติม UI-19 ถึง UI-21 (เคสใน T11 / T13 ที่ตรวจระดับหน้าเว็บได้)

   ใช้: (รันจากโฟลเดอร์หลักของโปรเจกต์ ไม่ต้องเปิด backend)
     npm i -D playwright
     npx playwright install chromium
     node tools/u3-t7-run.mjs                    (ทุกเคส)
     node tools/u3-t7-run.mjs --only UI-01,UI-14 (เฉพาะบางเคส)

   - ทดสอบในเบราว์เซอร์จริง (Chromium) จำลอง API ด้วย page.route (mock server) ตาม T11.md
     ไม่ต้องมี backend / ฐานข้อมูล ทุกเคสใช้ browser context ใหม่ (sessionStorage ว่าง)
   - window.confirm ("ยืนยันการส่งคำร้อง?") กดตกลงให้อัตโนมัติ
   - จบแล้วพิมพ์สรุป ผ่าน/ไม่ผ่าน · exit code 1 ถ้ามีเคสไม่ผ่าน
   ===================================================================== */
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname, resolve } from 'node:path';
import assert from 'node:assert/strict';

const ROOT = resolve('.');
const FRONT = join(ROOT, 'frontend');
const PORT = Number(process.env.T7_PORT) || 5597;
const BASE = `http://localhost:${PORT}`;
const API = 'http://localhost:8080';

/* ---------- static server สำหรับ frontend ---------- */
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
const server = createServer(async (req, res) => {
   const path = decodeURIComponent(new URL(req.url, BASE).pathname);
   const file = resolve(FRONT, '.' + (path === '/' ? '/submit.html' : path));
   if (!file.startsWith(FRONT)) { res.writeHead(403).end(); return; }
   try {
      res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream' }).end(await readFile(file));
   } catch { res.writeHead(404).end('not found'); }
});

/* ---------- ข้อมูลจำลองของ API ---------- */
const FORM = {
   id: 10, name: 'คำร้องขอฝึกสหกิจศึกษา 2/2569', status: 'PUBLISHED', deadline: '2026-11-30T23:59:59',
   fields: [
      { id: 101, fieldName: 'field_1', label: 'ชื่อ-นามสกุล', fieldType: 'TEXT', required: true, displayOrder: 1 },
      { id: 102, fieldName: 'field_2', label: 'เบอร์โทรศัพท์', fieldType: 'PHONE', required: true, displayOrder: 2 },
      { id: 103, fieldName: 'field_3', label: 'อีเมล', fieldType: 'EMAIL', required: true, displayOrder: 3 },
      { id: 104, fieldName: 'field_4', label: 'ที่อยู่ปัจจุบัน', fieldType: 'TEXTAREA', required: true, displayOrder: 4 },
      { id: 105, fieldName: 'field_5', label: 'Transcript', fieldType: 'FILE', required: true, displayOrder: 5 }
   ]
};
const PUBLISHED = { form: FORM, serverTime: '2026-10-20T10:00:00', deadlinePassed: false };
const req = (status, extra = {}) => ({ id: 500, studentId: '6609650001', formTemplateId: 10, status, answers: {}, documents: [], ...extra });
const ERR = (code, message) => ({ code, message });
/* อันดับบริษัท (U4) ที่บันทึกแล้ว: ครบ 5 อันดับ = ยื่นได้ */
const RANKED = n => Array.from({ length: n }, (_, i) => ({ rank: i + 1, companyId: i + 1, companyName: 'บริษัท ' + (i + 1) }));
const RANKED5 = RANKED(5);
const PROFILE = { studentId: '6609650001', fullName: 'A arizona', email: 'arizona@dome.tu.ac.th',
   faculty: 'วิทยาศาสตร์และเทคโนโลยี', major: 'วิทยาการคอมพิวเตอร์', gpa: 3.1 };
const OK_ROUTES = {
   'GET /api/students/me': [200, PROFILE],
   'GET /api/forms/published': [200, PUBLISHED],
   'GET /api/requests/me': [200, []],
   'GET /api/students/me/company-ranking': [200, RANKED5],
   'POST /api/requests': [201, req('DRAFT')],
   'PUT /api/requests/500/form': [200, req('DRAFT')],
   'POST /api/requests/500/documents': [201, { id: 7, fileName: 'transcript.pdf', status: 'UPLOADED' }],
   'POST /api/requests/500/submit': [200, req('SUBMITTED', { submittedAt: '2026-10-20T10:00:00' })]
};
const SUBMIT_CALLS = ['POST /api/requests', 'PUT /api/requests/500/form', 'POST /api/requests/500/documents', 'POST /api/requests/500/submit'];
const PDF = Buffer.from('%PDF-1.4\n% U3 transcript\n%%EOF\n');

/* เปิด submit.html ใน context ใหม่ · routes: { 'METHOD /path': [status, body] | 'DOWN' } */
async function open(browser, overrides = {}, state = {}) {
   const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
   const routes = { ...OK_ROUTES, ...overrides };
   const calls = [];
   await ctx.addInitScript(([st]) => {
      if (!sessionStorage.getItem('coop-state')) {
         sessionStorage.setItem('coop-state', JSON.stringify(st));
         sessionStorage.setItem('u5-user', JSON.stringify({ id: 3, role: 'STUDENT', username: '6609650001' }));
      }
   }, [{
      user: '6609650001', unlocked: 2,
      ...state
   }]);
   await ctx.route(API + '/**', async route => {
      const rq = route.request();
      if (rq.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors() });
      const key = rq.method() + ' ' + new URL(rq.url()).pathname;
      calls.push({ key, userId: rq.headers()['x-user-id'], body: rq.postData() });
      const r = routes[key];
      if (r === 'DOWN') return route.abort('connectionrefused');
      const [status, body] = r || [404, ERR('NOT_FOUND', 'no route ' + key)];
      await new Promise(x => setTimeout(x, 60));   // ให้เห็นหน้ากำลังตรวจสอบ
      return route.fulfill({ status, headers: cors(), contentType: 'application/json', body: JSON.stringify(body) });
   });
   const page = await ctx.newPage();
   page.on('dialog', d => d.accept());
   await page.goto(BASE + '/submit.html');
   await page.evaluate(() => window.U3Submit && window.U3Submit.ready);
   const $ = s => page.locator(s);
   const p = {
      ctx, page, calls, routes, $,
      visible: s => $(s).isVisible(),
      text: async s => (await $(s).textContent()).trim(),
      cls: s => $(s).getAttribute('class'),
      set: (id, v) => $('#' + id).fill(v),
      fillAll: async () => {
         await p.set('s_name', 'A arizona');
         await p.set('s_tel', '0812345678');
         await p.set('s_mail', 'arizona@dome.tu.ac.th');
         await p.set('s_addr', '99 ม.ธ. รังสิต');
      },
      attach: (name, buffer = PDF) => $('#s_tr').setInputFiles({ name, mimeType: 'application/pdf', buffer }),
      send: async () => { await $('#btnSend').click(); await settle(page); },
      keys: () => calls.map(c => c.key),
      posts: () => calls.map(c => c.key).filter(k => !k.startsWith('GET')),
      state: () => page.evaluate(() => JSON.parse(sessionStorage.getItem('coop-state')))
   };
   return p;
}
const cors = () => ({ 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' });
const settle = page => page.waitForFunction(() => document.querySelector('#vLoad').hidden, null, { timeout: 5000 });

/* ---------- เคสทดสอบ ---------- */
const T = [];
const test = (id, name, fn) => T.push({ id, name, fn });

test('UI-01', '(TC1) กรอกครบ + PDF แล้วส่ง → หน้ากำลังตรวจสอบ → หน้าผลสีเขียว + ปุ่มดูอันดับบริษัท', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.$('#btnSend').click();
   assert.equal(await p.visible('#vLoad'), true, 'ต้องเห็นหน้ากำลังตรวจสอบ');
   await settle(p.page);
   assert.equal(await p.visible('#vResult'), true);
   assert.equal(await p.cls('#sAlert'), 'alert ok');
   assert.match(await p.text('#sAlert'), /ยื่นคำร้องสำเร็จ เลขที่คำร้อง 500/);
   assert.equal(await p.visible('#sNext'), true);
   assert.deepEqual(p.posts(), SUBMIT_CALLS);
   assert.ok(p.calls.every(c => c.userId === '3'), 'ทุก request ต้องส่ง X-User-Id');
   return 'หน้ากำลังตรวจสอบ → ผลสีเขียว "' + await p.text('#sAlert') + '" มีปุ่มดูอันดับบริษัท · เรียก ' + p.posts().join(' → ');
});

for (const [id, name, field] of [['UI-02', '(TC2) ไม่กรอกชื่อ', 's_name'], ['UI-03', 'ไม่กรอกเบอร์โทร', 's_tel'],
   ['UI-04', 'ไม่กรอกอีเมล', 's_mail'], ['UI-05', 'ไม่กรอกที่อยู่', 's_addr']]) {
   test(id, name + ' → แจ้ง "กรุณากรอกข้อมูลที่จำเป็นให้ครบ" อยู่หน้ากรอกข้อมูล', async b => {
      const p = await open(b);
      await p.fillAll();
      await p.set(field, '');
      await p.attach('transcript.pdf');
      await p.$('#btnSend').click();
      assert.equal(await p.visible('#s_err'), true);
      assert.equal(await p.text('#s_err'), 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ');
      assert.equal(await p.$('#' + field).getAttribute('aria-invalid'), 'true');
      assert.equal(await p.visible('#vForm'), true);
      assert.deepEqual(p.posts(), []);
      return 'แจ้ง "' + await p.text('#s_err') + '" ช่องว่างถูกไฮไลต์ ไม่เรียก API';
   });
}

test('UI-06', 'กรอกแต่ช่องว่าง → แจ้ง "กรุณากรอกข้อมูลที่จำเป็นให้ครบ"', async b => {
   const p = await open(b);
   for (const id of ['s_name', 's_tel', 's_mail', 's_addr']) await p.set(id, '   ');
   await p.$('#btnSend').click();
   assert.equal(await p.text('#s_err'), 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ');
   assert.deepEqual(p.posts(), []);
   return 'แจ้ง "' + await p.text('#s_err') + '" (ตัดช่องว่างก่อนตรวจ) ไม่เรียก API';
});

test('UI-07', 'อีเมลผิดรูปแบบ (arizona@) → แจ้งอีเมลไม่ถูกต้อง ไม่ส่งคำร้อง', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.set('s_mail', 'arizona@');
   await p.$('#btnSend').click();
   assert.equal(await p.text('#s_err'), 'รูปแบบอีเมลไม่ถูกต้อง');
   assert.equal(await p.$('#s_mail').getAttribute('aria-invalid'), 'true');
   assert.deepEqual(p.posts(), []);
   return 'แจ้ง "' + await p.text('#s_err') + '" ไม่เรียก API';
});

test('UI-08', 'เบอร์โทรผิดรูปแบบ (12ab) → แจ้งเบอร์โทรไม่ถูกต้อง ไม่ส่งคำร้อง', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.set('s_tel', '12ab');
   await p.$('#btnSend').click();
   assert.equal(await p.text('#s_err'), 'รูปแบบเบอร์โทรศัพท์ไม่ถูกต้อง');
   assert.deepEqual(p.posts(), []);
   return 'แจ้ง "' + await p.text('#s_err') + '" ไม่เรียก API';
});

test('UI-09', 'ไม่แนบ Transcript → ผลสีแดง แจ้งไม่พบไฟล์ มีปุ่มแก้ไข/ตรวจสอบอีกครั้ง ไม่มีปุ่มดูอันดับบริษัท', async b => {
   const p = await open(b, { 'POST /api/requests/500/submit': [400, ERR('MISSING_DOCUMENT', 'กรุณาแนบไฟล์ Transcript (PDF) ก่อนส่งคำร้อง')] });
   await p.fillAll();
   await p.send();
   assert.equal(await p.cls('#sAlert'), 'alert bad');
   assert.equal(await p.cls('#sTr'), 'res bad');
   assert.equal(await p.text('#sTr small'), 'ไม่พบไฟล์หรือไฟล์อ่านไม่ได้');
   assert.equal(await p.visible('#sEdit'), true);
   assert.equal(await p.visible('#sRetry'), true);
   assert.equal(await p.visible('#sNext'), false);
   return 'ผลสีแดง "' + await p.text('#sTr small') + '" มีปุ่มแก้ไขข้อมูล + ตรวจสอบอีกครั้ง ไม่มีปุ่มดูอันดับบริษัท';
});

test('UI-10', 'ไฟล์ไม่ใช่ PDF (photo.exe) → แจ้งต้องเป็น PDF ไม่มีปุ่มดูอันดับบริษัท', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.attach('photo.exe', Buffer.from('MZ fake exe'));
   await p.$('#btnSend').click();
   assert.equal(await p.cls('#sAlert'), 'alert bad');
   assert.equal(await p.text('#sTr small'), 'Transcript ต้องเป็นไฟล์ PDF เท่านั้น');
   assert.equal(await p.visible('#sNext'), false);
   assert.deepEqual(p.posts(), []);
   return 'ผลสีแดง "' + await p.text('#sTr small') + '" ไม่อัปโหลด ไม่มีปุ่มดูอันดับบริษัท';
});

test('UI-11', 'กดแก้ไขข้อมูล → กลับหน้ากรอกข้อมูล ข้อมูลเดิมครบ แถบขั้นตอนกลับขั้นที่ 1', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.attach('photo.exe', Buffer.from('MZ'));
   await p.$('#btnSend').click();
   await p.$('#sEdit').click();
   assert.equal(await p.visible('#vForm'), true);
   assert.equal(await p.$('#s_name').inputValue(), 'A arizona');
   assert.equal(await p.$('#s_tel').inputValue(), '0812345678');
   assert.equal(await p.$('#s_mail').inputValue(), 'arizona@dome.tu.ac.th');
   assert.equal(await p.$('#s_addr').inputValue(), '99 ม.ธ. รังสิต');
   assert.equal(await p.$('.steps .st').first().getAttribute('class'), 'st cur');
   return 'กลับหน้ากรอกข้อมูล ข้อมูล 4 ช่องอยู่ครบ แถบขั้นตอนอยู่ขั้นที่ 1';
});

test('UI-12', 'กดตรวจสอบอีกครั้ง → ตรวจใหม่ตามไฟล์ล่าสุด', async b => {
   const p = await open(b, { 'POST /api/requests/500/submit': [400, ERR('MISSING_DOCUMENT', 'กรุณาแนบไฟล์')] });
   await p.fillAll();
   await p.send();
   assert.equal(await p.cls('#sAlert'), 'alert bad');
   p.routes['POST /api/requests/500/submit'] = OK_ROUTES['POST /api/requests/500/submit'];
   await p.attach('transcript.pdf');
   await p.$('#sRetry').click();
   await settle(p.page);
   assert.equal(await p.cls('#sAlert'), 'alert ok');
   assert.ok(p.keys().includes('POST /api/requests/500/documents'));
   assert.equal(p.keys().filter(k => k === 'POST /api/requests').length, 1, 'ต้องใช้คำร้องเดิม');
   return 'รอบแรกไม่ผ่าน (ไม่มีไฟล์) → แนบ PDF แล้วกดตรวจสอบอีกครั้ง → อัปโหลดไฟล์ล่าสุดและผ่าน (ใช้คำร้องเดิม)';
});

test('UI-13', 'ข้อมูลจากระบบ: รหัส คณะ สาขา เกรดเฉลี่ย เติมไว้แล้วและแก้ไขไม่ได้', async b => {
   const p = await open(b);
   assert.ok(p.keys().includes('GET /api/students/me'), 'ต้องดึงข้อมูลนักศึกษาจาก API');
   for (const [id, v] of [['s_id', '6609650001'], ['s_fac', 'วิทยาศาสตร์และเทคโนโลยี'], ['s_major', 'วิทยาการคอมพิวเตอร์'], ['s_gpax', '3.10']]) {
      assert.equal(await p.$('#' + id).inputValue(), v);
      assert.equal(await p.$('#' + id).isDisabled(), true);
   }
   return 'GET /api/students/me → รหัส 6609650001 · คณะ · สาขา · GPA 3.10 เติมไว้แล้ว และเป็น disabled ทั้ง 4 ช่อง';
});

test('UI-14', '(TC3) ยื่นซ้ำ → ผลสีแดง "คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว" ไม่มีปุ่มไปต่อ/แก้ไข', async b => {
   const p = await open(b, { 'POST /api/requests': [409, ERR('DUPLICATE_REQUEST', 'คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว')] });
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.send();
   assert.equal(await p.cls('#sAlert'), 'alert bad');
   assert.equal(await p.text('#sAlert'), 'คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว');
   assert.equal(await p.visible('#sNext'), false);
   assert.equal(await p.visible('#sEdit'), false);
   assert.ok(!p.keys().includes('POST /api/requests/500/submit'));
   const q = await open(b, { 'GET /api/requests/me': [200, [req('SUBMITTED', { id: 200 })]] });
   assert.match(await q.text('#s_note'), /คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว \(เลขที่คำร้อง 200/);
   assert.equal(await q.$('#btnSend').isDisabled(), true);
   await q.ctx.close();
   return 'POST /api/requests ได้ 409 → ผลสีแดงข้อความตรง ไม่มีปุ่มไปต่อ/แก้ไข · เปิดหน้าโดยมีคำร้อง SUBMITTED: แจ้งเลขที่ 200 และปุ่มส่งกดไม่ได้';
});

test('UI-15', '(TC4) ยื่นหลังหมดเขต → ผลสีแดง "เกินระยะเวลาที่กำหนด..." ไม่มีปุ่มไปต่อ แก้ไข ตรวจสอบอีกครั้ง', async b => {
   const p = await open(b, { 'POST /api/requests/500/submit': [409, ERR('DEADLINE_PASSED', 'เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้')] });
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.send();
   assert.equal(await p.cls('#sAlert'), 'alert bad');
   assert.equal(await p.text('#sAlert'), 'เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้');
   for (const s of ['#sNext', '#sEdit', '#sRetry']) assert.equal(await p.visible(s), false, s);
   const q = await open(b, { 'GET /api/forms/published': [200, { ...PUBLISHED, deadlinePassed: true }] });
   assert.match(await q.text('#s_note'), /เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้ \(หมดเขต/);
   assert.equal(await q.$('#btnSend').isDisabled(), true);
   await q.ctx.close();
   return 'submit ได้ 409 DEADLINE_PASSED → ผลสีแดงข้อความตรง ไม่มีทั้ง 3 ปุ่ม · เปิดหน้าหลังหมดเขต: แจ้งพร้อมวันหมดเขต ปุ่มส่งกดไม่ได้';
});

test('UI-16', 'กดส่งซ้ำรัว ๆ → ส่งไปที่ API แค่ครั้งเดียว', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.$('#btnSend').click();
   await p.$('#btnSend').click({ force: true }).catch(() => { });
   await p.$('#btnSend').click({ force: true }).catch(() => { });
   await p.page.evaluate(() => { const bt = document.querySelector('#btnSend'); bt.onclick(); bt.onclick(); });
   await settle(p.page);
   assert.equal(p.keys().filter(k => k === 'POST /api/requests').length, 1);
   assert.equal(p.keys().filter(k => k === 'POST /api/requests/500/submit').length, 1);
   return 'กด 5 ครั้งติดกัน → POST /api/requests 1 ครั้ง และ POST /submit 1 ครั้ง (ปุ่มถูกปิดระหว่างรอ)';
});

test('UI-17', 'ยื่นสำเร็จ → กดดูอันดับบริษัท ไปหน้าจับคู่บริษัท (อันดับถูกล็อกแล้ว แก้ไม่ได้)', async b => {
   const p = await open(b);
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.send();
   assert.equal(await p.text('#sNext'), 'ดูอันดับบริษัทที่เลือก');
   await p.$('#sNext').click();
   await p.page.waitForURL('**/ranking.html');
   assert.equal((await p.state()).unlocked, 3);
   const link = p.page.locator('nav a[href="ranking.html"]');
   assert.equal(await link.getAttribute('aria-disabled'), null);
   return 'ปุ่ม "ดูอันดับบริษัทที่เลือก" → ไปที่ ranking.html · coop-state.unlocked = 3 · เมนูจับคู่บริษัทไม่ถูกปิด';
});

test('UI-18', 'กดยกเลิก → กลับไปหน้าตรวจสอบคุณสมบัติ', async b => {
   const p = await open(b);
   await p.page.getByRole('link', { name: 'ยกเลิก' }).click();
   await p.page.waitForURL('**/check.html');
   return 'ไปที่ ' + p.page.url().replace(BASE + '/', '');
});

test('UI-19', '(เพิ่มเติม · T13 INT-08) server ไม่ทำงาน → "ไม่สามารถเชื่อมต่อระบบได้" ข้อมูลไม่หาย', async b => {
   const p = await open(b, { 'POST /api/requests': 'DOWN' });
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.send();
   assert.equal(await p.visible('#vForm'), true);
   assert.equal(await p.text('#s_err'), 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
   assert.equal(await p.$('#s_name').inputValue(), 'A arizona');
   assert.equal(await p.$('#btnSend').isDisabled(), false);
   return 'แจ้ง "' + await p.text('#s_err') + '" ข้อมูลที่กรอกยังอยู่ กดส่งใหม่ได้';
});

test('UI-20', '(เพิ่มเติม · T11 UI-08) คำร้องถูกส่งกลับ (NEED_CORRECTION) → แสดงเหตุผล แก้แล้วส่งใหม่เลขที่เดิม', async b => {
   const back = req('NEED_CORRECTION', {
      correctionReason: 'กรุณาแนบ Transcript ฉบับล่าสุด',
      answers: { field_1: 'A arizona', field_2: '0812345678', field_3: 'arizona@dome.tu.ac.th', field_4: '99 ม.ธ. รังสิต' }
   });
   const p = await open(b, { 'GET /api/requests/me': [200, [back]] });
   assert.match(await p.text('#s_note'), /กรุณาแนบ Transcript ฉบับล่าสุด/);
   assert.equal(await p.$('#s_tel').inputValue(), '0812345678');
   await p.set('s_tel', '0899999999');
   await p.attach('transcript.pdf');
   await p.send();
   assert.equal(await p.cls('#sAlert'), 'alert ok');
   assert.ok(!p.keys().includes('POST /api/requests'));
   assert.equal(JSON.parse(p.calls.find(c => c.key === 'PUT /api/requests/500/form').body).answers.field_2, '0899999999');
   return 'แสดงเหตุผล "กรุณาแนบ Transcript ฉบับล่าสุด" เติมข้อมูลเดิม → แก้เบอร์โทรแล้วส่ง ใช้คำร้องเลขที่ 500 เดิม';
});

test('UI-21', '(เพิ่มเติม · T11 UI-04) บันทึกร่าง → ปิดหน้าแล้วเปิดใหม่ ข้อมูลยังอยู่', async b => {
   const p = await open(b);
   await p.set('s_name', 'A arizona');
   await p.set('s_tel', '0812345678');
   await p.page.reload();
   await p.page.evaluate(() => window.U3Submit.ready);
   assert.equal(await p.$('#s_name').inputValue(), 'A arizona');
   assert.equal(await p.$('#s_tel').inputValue(), '0812345678');
   return 'โหลดหน้าใหม่แล้วชื่อและเบอร์โทรที่กรอกไว้ยังอยู่ (เก็บใน sessionStorage)';
});

test('UI-22', '(Flow จัดอันดับก่อนยื่น) เข้าหน้ายื่นคำร้อง → เมนูจับคู่บริษัทกดได้ทันที และมีข้อความเตือนให้จัดอันดับก่อนส่ง', async b => {
   const p = await open(b);
   const link = p.page.locator('nav a[href="ranking.html"]');
   assert.equal(await link.getAttribute('aria-disabled'), null);
   assert.equal(await link.evaluate(a => a.classList.contains('disabled')), false);
   assert.equal((await p.state()).unlocked, 3);
   assert.equal(await p.visible('#s_rank'), true);
   assert.match(await p.text('#s_rank'), /จัดอันดับบริษัทให้ครบ 5 อันดับก่อนยื่น\s+ยื่นแล้วแก้ไม่ได้/);
   return 'เมนูจับคู่บริษัทไม่ถูกปิด · coop-state.unlocked = 3 · แสดง "' + await p.text('#s_rank') + '"';
});

test('UI-23', '(Flow จัดอันดับก่อนยื่น) มีคำร้องที่ยื่นแล้ว → ไม่แสดงข้อความเตือนให้จัดอันดับก่อนส่ง', async b => {
   const p = await open(b, { 'GET /api/requests/me': [200, [req('SUBMITTED', { submittedAt: '2026-10-20T10:00:00' })]] });
   assert.equal(await p.$('#btnSend').isDisabled(), true);
   assert.equal(await p.visible('#s_rank'), false);
   return 'ปุ่มส่งถูกปิด · ซ่อนข้อความเตือนเรื่องจัดอันดับ';
});

test('UI-24', '(Flow จัดอันดับก่อนยื่น) ยังจัดอันดับไม่ครบ 5 อันดับ → แจ้งเตือนพร้อมลิงก์ไปหน้าจัดอันดับ และไม่ส่งคำร้อง', async b => {
   const p = await open(b, { 'GET /api/students/me/company-ranking': [200, RANKED(3)] });
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.send();
   const err = await p.text('#s_err');
   assert.match(err, /กรุณาจัดอันดับบริษัทให้ครบ 5 อันดับก่อนยื่นคำร้อง/);
   assert.equal(await p.$('#s_err a[href="ranking.html"]').count(), 1);
   assert.ok(p.keys().includes('GET /api/students/me/company-ranking'));
   assert.deepEqual(p.posts(), []);
   assert.equal(await p.visible('#vForm'), true);
   return 'จัดอันดับไว้ 3 อันดับ → แจ้ง "' + err + '" มีลิงก์ไป ranking.html · ไม่เรียก POST/PUT ใด ๆ อยู่หน้ากรอกข้อมูล';
});

test('UI-25', '(Flow จัดอันดับก่อนยื่น) ยังไม่เคยจัดอันดับ → แจ้งเตือนและไม่ส่ง · จัดครบแล้วส่งได้ตามปกติ', async b => {
   const p = await open(b, { 'GET /api/students/me/company-ranking': [200, []] });
   await p.fillAll();
   await p.attach('transcript.pdf');
   await p.send();
   assert.match(await p.text('#s_err'), /จัดอันดับบริษัทให้ครบ 5 อันดับ/);
   assert.deepEqual(p.posts(), []);
   p.routes['GET /api/students/me/company-ranking'] = [200, RANKED5];
   await p.send();
   assert.equal(await p.cls('#sAlert'), 'alert ok');
   assert.deepEqual(p.posts(), SUBMIT_CALLS);
   return 'ไม่มีอันดับ → แจ้งเตือน ไม่ส่ง · จัดครบ 5 อันดับแล้วกดส่งอีกครั้ง → ' + p.posts().join(' → ') + ' สำเร็จ';
});

/* ---------- main ---------- */
async function main() {
   const ONLY = (() => { const i = process.argv.indexOf('--only'); return i > 0 ? process.argv[i + 1].toUpperCase().split(',') : null; })();
   await new Promise(r => server.listen(PORT, r));
   const browser = await chromium.launch();
   const started = new Date();
   console.log(`U3:T7 UI unit test · ${started.toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' })} · Chromium ${browser.version()}`);
   let pass = 0, fail = 0;
   for (const t of T) {
      if (ONLY && !ONLY.includes(t.id)) continue;
      const before = browser.contexts().length;
      try {
         const actual = await t.fn(browser);
         pass++;
         console.log(`✅ ${t.id} ${t.name}\n     Actual: ${actual}`);
      } catch (e) {
         fail++;
         console.log(`❌ ${t.id} ${t.name}\n     ${e.message.split('\n')[0]}`);
      } finally {
         for (const c of browser.contexts().slice(before)) await c.close().catch(() => { });
      }
   }
   console.log(`\nสรุป: ทั้งหมด ${pass + fail} ข้อ · ผ่าน ${pass} · ไม่ผ่าน ${fail}`);
   await browser.close();
   server.close();
   process.exit(fail ? 1 : 0);
}
main().catch(e => { console.error(e); process.exit(1); });
