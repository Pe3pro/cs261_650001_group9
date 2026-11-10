/* =====================================================================
   U3:T17 รัน Integration Test UI กับ API จริง ตาม T13.md
   (U3-INT-01, 02, 04, 05, 06, 07, 08)

   ใช้: (รันจากโฟลเดอร์หลักของโปรเจกต์ บนฐานข้อมูลใหม่ที่มีแค่ข้อมูลจาก DemoDataSeeder)
     npm i -D playwright
     npx playwright install chromium
     docker compose down -v && docker compose up -d --build      (ล้างฐานข้อมูลให้ seed ใหม่)
     node tools/u3-t17-run.mjs

   - หน้าเว็บเรียก backend จริงที่ http://localhost:8080 (ไม่จำลอง API)
   - ใช้บัญชีทดลองของ DemoDataSeeder login ผ่าน login.html (รหัสผ่านทดลอง U3_DEMO_PASS ค่าเริ่มต้น 1234)
     บทบาทในตาราง T13 → บัญชี seed ที่ใช้ (ตัวอักษรในวงเล็บคือชื่อใน DemoDataSeeder):
       บทบาท A (ยังไม่มีคำร้อง) = F 6609650006 (INT-01, 04) / G 6609650007 (INT-02, 06, 08)
       บทบาท B (มีคำร้อง SUBMITTED) = B 6609650002 (อ่านอย่างเดียว)
       บทบาท C (ถูกส่งกลับให้แก้) = F 6609650006 ใช้คำร้องที่ยื่นใน INT-01 ให้เจ้าหน้าที่ส่งกลับผ่าน API ของ U5
     ไม่แตะ C/D (ข้อมูล U4) และไม่เปลี่ยนคำร้องของ A/B (ข้อมูลร่วม U4/U5)
   - INT-06 สร้างแบบฟอร์ม F2 ที่หมดเขตในอีก ~20 วินาทีตามเวลา server แล้วรอให้เลยเวลา
   - INT-08 หยุด backend ด้วย U3_STOP_CMD แล้วเปิดกลับด้วย U3_START_CMD
     (ค่าเริ่มต้น docker compose stop app / start app)
   - แคปจอไว้ที่ .doc/UserStory/U3/evidence/T17-*.png พร้อมแถบ request /api/* ที่หน้าเว็บส่ง
   ===================================================================== */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
import {
   OUT, API, ACC, PDF, ANSWERS, STOP_CMD, START_CMD, sh, sleep, api, backendUp, publishForm, serverTime, addSeconds,
   ensureRanked, startStatic, newPage, loginStudent, loginStaff, fillForm, clickSend, text, apiLog, shot
} from './u3-common.mjs';

const PORT = Number(process.env.T17_PORT) || 5598;
const results = [];
function record(id, ok, actual) {
   results.push({ id, ok, actual });
   console.log(`${ok ? '✅' : '❌'} ${id}\n     ${actual}`);
}
async function run(id, fn) {
   try {
      record(id, true, await fn());
   } catch (e) {
      record(id, false, e.message.split('\n')[0]);
   }
}

async function main() {
   await mkdir(OUT, { recursive: true });
   if (!(await backendUp(5000))) {
      console.error('❌ เรียก backend ที่ ' + API + ' ไม่ได้ ให้เปิด backend ก่อน');
      process.exit(1);
   }
   await ensureRanked(ACC.s3, ACC.s4);   // flow ใหม่: จัดอันดับบริษัท (U4) ให้ครบก่อนยื่นคำร้อง
   const { server, base } = await startStatic(PORT);
   const browser = await chromium.launch();
   console.log(`U3:T17 Integration UI ↔ API · ${new Date().toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' })} · Chromium ${browser.version()} · API ${API}`);
   const ctx = {};

   /* ---------- U3-INT-06 ยื่นหลังหมดเขต (TC4) ---------- */
   await run('U3-INT-06', async () => {
      const now = await serverTime(ACC.s4.id);
      const deadline = addSeconds(now, 20);
      ctx.f2 = await publishForm('U3 T17 ฟอร์ม F2 (หมดเขตเร็ว)', deadline);
      const p = await newPage(browser, base);
      await loginStudent(p, ACC.s4.username);
      assert.equal(await p.page.locator('#btnSend').isDisabled(), false, 'ก่อนหมดเขตต้องกดส่งได้');
      await fillForm(p.page);
      await p.page.setInputFiles('#s_tr', { name: 'transcript.pdf', mimeType: 'application/pdf', buffer: PDF });
      while (!(await api('GET', '/api/forms/published', ACC.s4.id)).data.deadlinePassed) await sleep(1000);
      await clickSend(p.page);
      const alert = await text(p.page, '#sAlert');
      const calls = apiLog(p.log.filter(x => x.url.startsWith('/api/')  && !x.url.startsWith('/api/auth'))).filter(x => !x.startsWith('GET'));
      const submit = p.log.find(x => x.url.endsWith('/submit'));
      const id = Number(p.log.find(x => x.url.match(/^\/api\/requests\/\d+\/form$/)).url.split('/')[3]);
      const db = await api('GET', '/api/requests/' + id, ACC.s4.id);
      await shot(p.page, 'T17-INT-06-deadline.png', 'U3-INT-06 ยื่นหลังหมดเขต', p.log);
      await p.ctx.close();
      ctx.f3 = await publishForm('U3 T17 ฟอร์ม F3 (เปิดใหม่หลัง INT-06)', addSeconds(await serverTime(ACC.s4.id), 30 * 24 * 3600));
      assert.equal(submit.status, 409);
      assert.equal(alert, 'เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้');
      assert.equal(db.data.status, 'DRAFT');
      assert.equal(db.data.submittedAt, null);
      return `หมดเขต ${deadline} (เวลา server) · ${calls.join(' → ')} · หน้าเว็บแสดง "${alert}" · คำร้อง #${id} ในฐานข้อมูลยังเป็น ${db.data.status} submittedAt=null`;
   });

   /* ---------- U3-INT-08 server ไม่ทำงาน ---------- */
   await run('U3-INT-08', async () => {
      const p = await newPage(browser, base);
      await loginStudent(p, ACC.s4.username);
      await fillForm(p.page);
      await p.page.setInputFiles('#s_tr', { name: 'transcript.pdf', mimeType: 'application/pdf', buffer: PDF });
      sh(STOP_CMD);
      await sleep(2000);
      try {
         await clickSend(p.page);
         const err = await text(p.page, '#s_err');
         const kept = await p.page.inputValue('#s_name') === ANSWERS.s_name && await p.page.inputValue('#s_tel') === ANSWERS.s_tel;
         await shot(p.page, 'T17-INT-08-server-down.png', 'U3-INT-08 server ไม่ทำงาน', p.log);
         assert.equal(err, 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
         assert.ok(kept, 'ข้อมูลที่กรอกต้องไม่หาย');
         return `${apiLog(p.log.filter(x => x.url.startsWith('/api/')  && !x.url.startsWith('/api/auth'))).filter(x => !x.startsWith('GET')).join(' | ')} · หน้าเว็บแสดง "${err}" ข้อมูลที่กรอกยังอยู่ครบ`;
      } finally {
         await p.ctx.close();
         sh(START_CMD);
         if (!(await backendUp(180000))) throw new Error('เปิด backend กลับไม่ขึ้น');
      }
   });

   /* ---------- U3-INT-02 ส่งคำร้องที่ยังไม่แนบ PDF (TC2) ---------- */
   await run('U3-INT-02', async () => {
      const p = await newPage(browser, base);
      await loginStudent(p, ACC.s4.username);
      await fillForm(p.page);
      await clickSend(p.page);
      const submit = p.log.find(x => x.url.endsWith('/submit'));
      const id = Number(submit.url.split('/')[3]);
      const msg = await text(p.page, '#sTr small');
      const db = await api('GET', '/api/requests/' + id, ACC.s4.id);
      await shot(p.page, 'T17-INT-02-no-pdf.png', 'U3-INT-02 ส่งโดยไม่แนบ PDF', p.log);
      await p.ctx.close();
      assert.equal(submit.status, 400);
      assert.equal(db.data.status, 'DRAFT');
      return `${apiLog(p.log.filter(x => x.url.startsWith('/api/')  && !x.url.startsWith('/api/auth'))).filter(x => !x.startsWith('GET')).join(' → ')} · หน้าเว็บแถบสีแดง "${msg}" · คำร้อง #${id} ยังเป็น ${db.data.status}`;
   });

   /* ---------- U3-INT-01 สร้าง กรอก แนบ PDF และส่ง (TC1) ---------- */
   await run('U3-INT-01', async () => {
      const p = await newPage(browser, base);
      await loginStudent(p, ACC.s3.username);
      await fillForm(p.page);
      await p.page.setInputFiles('#s_tr', { name: 'transcript.pdf', mimeType: 'application/pdf', buffer: PDF });
      await clickSend(p.page);
      const posts = p.log.filter(x => x.method !== 'GET' && x.url.startsWith('/api/requests'));
      const alert = await text(p.page, '#sAlert');
      ctx.idC = Number(posts.at(-1).url.split('/')[3]);
      const db = await api('GET', '/api/requests/' + ctx.idC, ACC.s3.id);
      await shot(p.page, 'T17-INT-01-submit-ok.png', 'U3-INT-01 ยื่นคำร้องสำเร็จ', p.log);
      await p.ctx.close();
      assert.deepEqual(posts.map(x => x.status), [201, 200, 201, 200]);
      assert.deepEqual(posts.map(x => x.method + ' ' + x.url.replace(/\d+/, '{id}')), ['POST /api/requests',
         'PUT /api/requests/{id}/form', 'POST /api/requests/{id}/documents', 'POST /api/requests/{id}/submit']);
      assert.match(alert, /ยื่นคำร้องสำเร็จ/);
      assert.equal(db.data.status, 'SUBMITTED');
      assert.equal(Object.keys(db.data.answers).length, 4);
      assert.equal(db.data.documents.length, 1);
      return `${apiLog(posts).join(' → ')} · หน้าเว็บ "${alert}" · ฐานข้อมูล: คำร้อง #${ctx.idC} SUBMITTED คำตอบ 4 ช่อง + เอกสาร 1 ไฟล์ ผูกกับคำร้องเดียวกัน`;
   });

   /* ---------- U3-INT-04 เจ้าหน้าที่เปิดรายการคำร้อง (U5) ---------- */
   await run('U3-INT-04', async () => {
      const p = await newPage(browser, base);
      await loginStaff(p);
      await p.page.waitForSelector('#appRows tr');
      const row = p.page.locator('#appRows tr', { hasText: ACC.s3.username });
      const rowCount = await row.count();
      const rowText = (await row.textContent()).replace(/\s+/g, ' ').trim();
      const list = p.log.find(x => x.url.startsWith('/api/applications?status=SUBMITTED'));
      const detail = await api('GET', '/api/applications/' + ctx.idC + '/detail', ACC.staff.id);
      await shot(p.page, 'T17-INT-04-staff-list.png', 'U3-INT-04 เจ้าหน้าที่เห็นคำร้อง', p.log);
      await p.ctx.close();
      assert.equal(list.status, 200);
      assert.equal(rowCount, 1);
      assert.deepEqual(detail.data.answers.map(a => a.value), Object.values(ANSWERS).concat().slice(0, 4));
      return `GET /api/applications?status=SUBMITTED → ${list.status} · แถวในตาราง: "${rowText}" · detail: ${detail.data.answers.map(a => a.label + '=' + a.value).join(', ')} ตรงกับที่นักศึกษากรอก`;
   });

   /* ---------- U3-INT-05 ยื่นซ้ำ (TC3) ---------- */
   await run('U3-INT-05', async () => {
      const before = (await api('GET', '/api/requests/me', ACC.s2.id)).data;
      const p = await newPage(browser, base);
      await loginStudent(p, ACC.s2.username);
      const note = await text(p.page, '#s_note');
      const disabled = await p.page.locator('#btnSend').isDisabled();
      const me = p.log.find(x => x.url === '/api/requests/me');
      await shot(p.page, 'T17-INT-05-duplicate.png', 'U3-INT-05 ยื่นซ้ำ', p.log);
      await p.ctx.close();
      const post = await api('POST', '/api/requests', ACC.s2.id, {});
      const after = (await api('GET', '/api/requests/me', ACC.s2.id)).data;
      assert.equal(me.status, 200);
      assert.match(note, /คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว \(เลขที่คำร้อง \d+ · สถานะ/);
      assert.equal(disabled, true);
      assert.equal(post.status, 409);
      assert.equal(after.length, before.length);
      return `GET /api/requests/me → ${me.status} (${before[0].status}) · หน้าเว็บ "${note}" ปุ่มส่งกดไม่ได้ · POST /api/requests (ยิงตรง) → ${post.status} ${post.data.code} · จำนวนคำร้องของ B ${before.length} → ${after.length}`;
   });

   /* ---------- U3-INT-07 แก้ไขคำร้องที่ถูกส่งกลับ (TC2) ---------- */
   await run('U3-INT-07', async () => {
      const id = ctx.idC;   // คำร้องของ F ที่ยื่นใน U3-INT-01 (ไม่ใช้คำร้อง seed ของ A เพราะเป็นข้อมูลร่วม U4/U5)
      const ret = await api('POST', '/api/approvals/' + id + '/decision', ACC.staff.id,
         { decision: 'RETURN', comment: 'กรุณาแนบ Transcript ฉบับล่าสุด และตรวจเบอร์โทร' });
      assert.equal(ret.status, 200, 'เจ้าหน้าที่ส่งกลับไม่ได้ ' + JSON.stringify(ret.data));
      const p = await newPage(browser, base);
      await loginStudent(p, ACC.s3.username);
      const note = await text(p.page, '#s_note');
      await p.page.fill('#s_tel', '0899999999');
      await clickSend(p.page);
      const alert = await text(p.page, '#sAlert');
      const db = await api('GET', '/api/requests/' + id, ACC.s3.id);
      await shot(p.page, 'T17-INT-07-correction.png', 'U3-INT-07 แก้ไขคำร้องที่ถูกส่งกลับ', p.log);
      await p.ctx.close();
      const calls = p.log.filter(x => x.url.includes('/api/requests'));
      assert.match(note, /กรุณาแนบ Transcript ฉบับล่าสุด/);
      assert.ok(!calls.some(x => x.method === 'POST' && x.url === '/api/requests'), 'ต้องไม่สร้างคำร้องใหม่');
      assert.equal(db.data.status, 'SUBMITTED');
      assert.equal(db.data.answers.field_2, '0899999999');
      return `เจ้าหน้าที่ RETURN คำร้อง #${id} · หน้าเว็บแสดง "${note}" · ${apiLog(calls).join(' → ')} · "${alert}" · ฐานข้อมูล: #${id} (เลขเดิม) SUBMITTED เบอร์โทรใหม่ 0899999999`;
   });

   const pass = results.filter(r => r.ok).length;
   console.log(`\nสรุป: ทั้งหมด ${results.length} ข้อ · ผ่าน ${pass} · ไม่ผ่าน ${results.length - pass}`);
   await browser.close();
   server.close();
   process.exit(pass === results.length ? 0 : 1);
}

main().catch(e => { console.error(e); process.exit(1); });
