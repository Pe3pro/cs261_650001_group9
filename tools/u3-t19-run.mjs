/* =====================================================================
   U3:T19 รัน System Test ตาม Sequence Diagram (T15.md: ST-01 ถึง ST-04)

   ใช้: (รันจากโฟลเดอร์หลักของโปรเจกต์ บนฐานข้อมูลใหม่ที่มีแค่ข้อมูลจาก DemoDataSeeder)
     npm i -D playwright
     npx playwright install chromium
     docker compose down -v && docker compose up -d --build
     node tools/u3-t19-run.mjs

   - ทำตามขั้นบนหน้าเว็บจริงทุกขั้น (login.html → submit.html → staff-review.html) backend และฐานข้อมูลจริง
   - ตรวจผลบนหน้าจอ + ในฐานข้อมูล (ผ่าน API ที่อ่านจากตาราง) + ไฟล์ในที่เก็บ PDF (เปิดไฟล์กลับมาเทียบ byte)
     + Email (ระบบยังไม่มี mail server: LoggingEmailNotifier เขียนบรรทัด [EMAIL] ลง log ของ backend
       สคริปต์อ่าน log ด้วย U3_LOG_CMD ค่าเริ่มต้น "docker compose logs app")
   - บทบาทในตาราง T15 → บัญชี seed: บทบาท A = F 6609650006 · บทบาท B = B 6609650002 (อ่านอย่างเดียว) · ทดสอบหมดเขต = G 6609650007
     (ไม่ใช้ C 6609650003 / D 6609650004 ซึ่งเป็นข้อมูลของ U4)
   - ลำดับที่รัน: ST-01 → ST-02 → ST-04 → ST-03 (ST-03 ทำให้แบบฟอร์มหมดเขต จึงรันท้ายสุด)
   - ST-03 ขั้น 1 "ตั้งวันหมดเขตเป็นเมื่อวาน": API ของ U5 ไม่ยอมเปิดใช้ฟอร์มที่หมดเขตแล้ว
     จึงเปิดฟอร์มใหม่ที่หมดเขตในอีก ~15 วินาที (เวลา server) แล้วรอให้เลยเวลา
   - แคปจอไว้ที่ .doc/UserStory/U3/evidence/T19-*.png
   ===================================================================== */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
import {
   OUT, API, ACC, PDF, ANSWERS, sh, sleep, api, backendUp, publishForm, serverTime, addSeconds,
   ensureRanked, startStatic, newPage, loginStudent, loginStaff, fillForm, clickSend, text, apiLog, shot
} from './u3-common.mjs';

const PORT = Number(process.env.T19_PORT) || 5596;
const LOG_CMD = process.env.U3_LOG_CMD || 'docker compose logs app';
const emails = to => sh(LOG_CMD).split('\n').filter(l => l.includes('[EMAIL] to=' + to));

const results = [];
const arrows = [];
function step(st, no, ok, detail) {
   console.log(`   ${ok ? '✓' : '✗'} ${st} ขั้น ${no}: ${detail}`);
   if (!ok) throw Object.assign(new Error(`ขั้น ${no}: ${detail}`), { stepNo: no });
}
async function scenario(id, fn) {
   console.log(`▶ ${id}`);
   try {
      const actual = await fn();
      results.push({ id, ok: true, actual });
      console.log(`✅ ${id} ${actual}`);
   } catch (e) {
      results.push({ id, ok: false, failedStep: e.stepNo || '-', actual: e.message.split('\n')[0] });
      console.log(`❌ ${id} ${e.message.split('\n')[0]}`);
   }
}

async function main() {
   await mkdir(OUT, { recursive: true });
   if (!(await backendUp(5000))) {
      console.error('❌ เรียก backend ที่ ' + API + ' ไม่ได้');
      process.exit(1);
   }
   await ensureRanked(ACC.s3, ACC.s4);   // flow ใหม่: จัดอันดับบริษัท (U4) ให้ครบก่อนยื่นคำร้อง
   const { server, base } = await startStatic(PORT);
   const browser = await chromium.launch();
   console.log(`U3:T19 System test · ${new Date().toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' })} · Chromium ${browser.version()} · API ${API}`);
   const A = ACC.s3, B = ACC.s2, D = ACC.s4;
   const mailA = 'frankfurt@dome.tu.ac.th';
   let requestA;

   /* ---------- ST-01 ยื่นคำร้องสำเร็จครบ flow (TC1) ---------- */
   await scenario('ST-01', async () => {
      const p = await newPage(browser, base);
      await loginStudent(p, A.username, false);
      const login = p.log.find(x => x.url === '/api/auth/login');
      step('ST-01', 1, login?.status === 200 && p.page.url().endsWith('/check.html'),
         `POST /api/auth/login → ${login?.status} เข้าหน้า ${p.page.url().split('/').pop()}`);
      arrows.push(['Student → Frontend: Login', true], ['Frontend → Controller: POST /api/auth/login', login.status === 200],
         ['Service → Repository → PostgreSQL: SELECT user', login.status === 200]);

      await p.page.evaluate(() => { const s = JSON.parse(sessionStorage.getItem('coop-state')); s.unlocked = 2; sessionStorage.setItem('coop-state', JSON.stringify(s)); });
      await p.page.goto(base + '/submit.html');
      await p.page.evaluate(() => window.U3Submit.ready);
      const pre = { id: await p.page.inputValue('#s_id'), fac: await p.page.inputValue('#s_fac'), major: await p.page.inputValue('#s_major'), gpa: await p.page.inputValue('#s_gpax') };
      step('ST-01', 2, pre.id === A.username && pre.gpa === '3.25' && pre.fac !== '' && await p.page.locator('#s_id').isDisabled(),
         `เติมอัตโนมัติจากฐานข้อมูล (GET /api/students/me): ${pre.id} · ${pre.fac} · ${pre.major} · GPA ${pre.gpa}`);
      await shot(p.page, 'T19-ST-01-step2-prefill.png', 'ST-01 ขั้น 2 ข้อมูลเติมจากฐานข้อมูล', p.log);

      await fillForm(p.page);
      await p.page.setInputFiles('#s_tr', { name: 'transcript.pdf', mimeType: 'application/pdf', buffer: PDF });
      const mailsBefore = emails(mailA).length;
      await clickSend(p.page);
      const call = (m, re) => p.log.find(x => x.method === m && re.test(x.url));
      const create = call('POST', /^\/api\/requests$/), put = call('PUT', /\/form$/);
      requestA = Number(put.url.split('/')[3]);
      step('ST-01', 3, create.status === 201 && put.status === 200,
         `POST /api/requests → ${create.status} (DRAFT) · PUT /api/requests/${requestA}/form → ${put.status}`);
      arrows.push(['Frontend → Controller: POST /api/requests, PUT /api/requests/{id}/form', create.status === 201 && put.status === 200],
         ['Repository → PostgreSQL: INSERT request', create.status === 201]);

      const up = call('POST', /\/documents$/);
      const db = await api('GET', '/api/requests/' + requestA, A.id);
      const docId = db.data.documents[0]?.id;
      const file = await fetch(`${API}/api/documents/${docId}?userId=${ACC.staff.id}`);
      const bytes = Buffer.from(await file.arrayBuffer());
      step('ST-01', 4, up.status === 201 && file.status === 200 && bytes.equals(PDF),
         `POST /api/requests/${requestA}/documents → ${up.status} · เอกสาร #${docId} ในตาราง documents · ไฟล์ในที่เก็บ PDF เปิดกลับได้ ${bytes.length} bytes ตรงกับต้นฉบับ`);
      arrows.push(['Frontend → Controller: POST /api/requests/{id}/documents', up.status === 201],
         ['Controller → PDF Storage: Store PDF', bytes.equals(PDF)], ['Repository → PostgreSQL: INSERT document', !!docId]);

      const sub = call('POST', /\/submit$/);
      step('ST-01', 5, sub.status === 200 && db.data.status === 'SUBMITTED' && !!db.data.submittedAt,
         `POST /api/requests/${requestA}/submit → ${sub.status} · ฐานข้อมูล: ${db.data.status} submittedAt=${db.data.submittedAt}`);
      arrows.push(['Service → PostgreSQL: Update request status', db.data.status === 'SUBMITTED']);

      const alert = await text(p.page, '#sAlert');
      step('ST-01', 6, (await p.page.locator('#sAlert').getAttribute('class')) === 'alert ok' && alert.includes('เลขที่คำร้อง ' + requestA),
         `แถบผลสีเขียว "${alert}"`);
      await shot(p.page, 'T19-ST-01-step6-result.png', 'ST-01 ขั้น 6 ผลการยื่นคำร้อง', p.log);
      await p.ctx.close();

      await sleep(500);
      const mails = emails(mailA);
      step('ST-01', 7, mails.length === mailsBefore + 1, `Email ใน log: ${mails.at(-1)?.replace(/^.*\[EMAIL\]/, '[EMAIL]')}`);
      arrows.push(['Service → Email: Send notification', mails.length === mailsBefore + 1]);

      const s = await newPage(browser, base);
      await loginStaff(s);
      await s.page.waitForSelector('#appRows tr');
      const row = s.page.locator('#appRows tr', { hasText: A.username });
      await row.locator('[data-open]').click();
      await s.page.waitForSelector('[data-doc]');
      // เบราว์เซอร์แบบ headless ดาวน์โหลด PDF แทนการแสดง จึงตรวจจาก response ของแท็บที่เปิดขึ้น
      const [resp] = await Promise.all([
         s.ctx.waitForEvent('response', r => r.url().includes('/api/documents/')),
         s.page.locator('[data-doc]').first().click()
      ]);
      const popupUrl = resp.url();
      const check = { status: resp.status(), headers: new Map(Object.entries(await resp.allHeaders())) };
      await shot(s.page, 'T19-ST-01-step8-staff.png', 'ST-01 ขั้น 8 เจ้าหน้าที่เปิดคำร้อง', s.log);
      await s.ctx.close();
      step('ST-01', 8, check.status === 200 && check.headers.get('content-type') === 'application/pdf',
         `เจ้าหน้าที่เห็นคำร้องของ ${A.username} ในรายการรอตรวจ · กดดูไฟล์เปิด ${popupUrl.replace(API, '')} → ${check.status} ${check.headers.get('content-type')}`);
      return `ผ่านครบ 8 ขั้น คำร้อง #${requestA}`;
   });

   /* ---------- ST-02 ยื่นซ้ำ (TC3) ---------- */
   await scenario('ST-02', async () => {
      const before = (await api('GET', '/api/requests/me', B.id)).data;
      const mailsBefore = emails('berlin@dome.tu.ac.th').length;
      const p = await newPage(browser, base);
      await loginStudent(p, B.username);
      const note = await text(p.page, '#s_note');
      step('ST-02', 1, /คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว \(เลขที่คำร้อง \d+ · สถานะ/.test(note) && await p.page.locator('#btnSend').isDisabled(),
         `หน้าเว็บ "${note}" ปุ่มส่งกดไม่ได้`);
      await shot(p.page, 'T19-ST-02-duplicate.png', 'ST-02 ยื่นซ้ำ', p.log);
      await p.ctx.close();
      const direct = await api('POST', `/api/requests/${before[0].id}/submit`, B.id);
      step('ST-02', 2, direct.status === 409, `ยิง POST /api/requests/${before[0].id}/submit ตรง (แทน Postman) → ${direct.status} ${direct.data.code}`);
      const after = (await api('GET', '/api/requests/me', B.id)).data;
      step('ST-02', 3, after.length === 1 && after[0].status === 'SUBMITTED' && after[0].submittedAt === before[0].submittedAt,
         `ฐานข้อมูล: คำร้องของ B ${after.length} แถว สถานะ ${after[0].status} submittedAt ไม่เปลี่ยน`);
      const mails = emails('berlin@dome.tu.ac.th').length;
      step('ST-02', 4, after[0].documents.length === before[0].documents.length && mails === mailsBefore,
         `เอกสาร ${before[0].documents.length} → ${after[0].documents.length} ไฟล์ · Email ถึง B ${mailsBefore} → ${mails}`);
      return 'ผ่านครบ 4 ขั้น';
   });

   /* ---------- ST-04 เจ้าหน้าที่ส่งกลับให้แก้ไข แล้วนักศึกษาส่งใหม่ (TC2) ---------- */
   await scenario('ST-04', async () => {
      const reason = 'กรุณาแนบ Transcript ฉบับล่าสุด และตรวจเบอร์โทร';
      const mailsBefore = emails(mailA).length;
      const s = await newPage(browser, base);
      await loginStaff(s);
      await s.page.waitForSelector('#appRows tr');
      await s.page.locator('#appRows tr', { hasText: A.username }).locator('[data-open]').click();
      await s.page.check('input[name="decision"][value="RETURN"]');
      await s.page.fill('#comment', reason);
      await s.page.click('#btnDecide');
      await s.page.waitForSelector('#vDone:not([hidden])');
      await shot(s.page, 'T19-ST-04-step1-return.png', 'ST-04 ขั้น 1 เจ้าหน้าที่ส่งกลับ', s.log);
      await s.ctx.close();
      const st1 = await api('GET', '/api/requests/' + requestA, A.id);
      await sleep(500);
      const mails = emails(mailA);
      step('ST-04', 1, st1.data.status === 'NEED_CORRECTION' && mails.length === mailsBefore + 1,
         `สถานะ ${st1.data.status} · Email: ${mails.at(-1)?.replace(/^.*\[EMAIL\]/, '[EMAIL]')}`);

      const p = await newPage(browser, base);
      await loginStudent(p, A.username);
      const note = await text(p.page, '#s_note');
      step('ST-04', 2, note.includes(reason) && !(await p.page.locator('#btnSend').isDisabled()), `หน้าเว็บ "${note}" กรอกได้`);
      await p.page.fill('#s_tel', '0899999999');
      const NEW_PDF = Buffer.concat([PDF, Buffer.from('% ฉบับแก้ไข\n')]);
      await p.page.setInputFiles('#s_tr', { name: 'transcript-v2.pdf', mimeType: 'application/pdf', buffer: NEW_PDF });
      await clickSend(p.page);
      const posted = p.log.filter(x => x.method !== 'GET' && x.url.startsWith('/api/requests'));
      const st3 = await api('GET', '/api/requests/' + requestA, A.id);
      await shot(p.page, 'T19-ST-04-step3-resubmit.png', 'ST-04 ขั้น 3 ส่งใหม่', p.log);
      await p.ctx.close();
      step('ST-04', 3, !posted.some(x => x.url === '/api/requests') && st3.data.status === 'SUBMITTED' && st3.data.id === requestA,
         `${apiLog(posted).join(' → ')} · ใช้คำร้องเดิม #${st3.data.id} สถานะ ${st3.data.status}`);

      const detail = await api('GET', `/api/applications/${requestA}/detail`, ACC.staff.id);
      const doc = detail.data.documents;
      const file = Buffer.from(await (await fetch(`${API}/api/documents/${doc[0].id}?userId=${ACC.staff.id}`)).arrayBuffer());
      step('ST-04', 4, detail.data.answers.some(a => a.value === '0899999999') && doc.length === 1 && doc[0].fileName === 'transcript-v2.pdf' && file.equals(NEW_PDF),
         `เจ้าหน้าที่เห็นเบอร์โทร 0899999999 และไฟล์ ${doc.map(d => d.fileName).join(', ')} (ฉบับล่าสุด ${file.length} bytes)`);
      return 'ผ่านครบ 4 ขั้น';
   });

   /* ---------- ST-03 ยื่นหลังหมดเขต (TC4) ---------- */
   await scenario('ST-03', async () => {
      const deadline = addSeconds(await serverTime(D.id), 15);
      await publishForm('U3 T19 ฟอร์มหมดเขต', deadline);
      while (!(await api('GET', '/api/forms/published', D.id)).data.deadlinePassed) await sleep(1000);
      step('ST-03', 1, true, `เปิดฟอร์มที่หมดเขต ${deadline} (เวลา server) และรอจนเลยเวลาแล้ว`);

      const p = await newPage(browser, base);
      await loginStudent(p, D.username);
      const note = await text(p.page, '#s_note');
      step('ST-03', 2, note.startsWith('เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้ (หมดเขต') && await p.page.locator('#btnSend').isDisabled(),
         `หน้าเว็บ "${note}" ปุ่มส่งกดไม่ได้`);
      await shot(p.page, 'T19-ST-03-step2-closed.png', 'ST-03 ขั้น 2 หมดเขต', p.log);
      await p.ctx.close();

      const created = await api('POST', '/api/requests', D.id, {});
      const id = created.data.id;
      await api('PUT', `/api/requests/${id}/form`, D.id, { answers: { field_1: ANSWERS.s_name, field_2: ANSWERS.s_tel, field_3: ANSWERS.s_mail, field_4: ANSWERS.s_addr } });
      const fd = new FormData();
      fd.append('file', new Blob([PDF], { type: 'application/pdf' }), 'transcript.pdf');
      await fetch(`${API}/api/requests/${id}/documents`, { method: 'POST', headers: { 'X-User-Id': String(D.id) }, body: fd });
      const direct = await api('POST', `/api/requests/${id}/submit`, D.id);
      step('ST-03', 3, direct.status === 409 && direct.data.code === 'DEADLINE_PASSED',
         `ยิง POST /api/requests/${id}/submit ตรง → ${direct.status} ${direct.data.code}`);

      const q = await newPage(browser, base);
      const yesterday = new Date(Date.now() - 24 * 3600 * 1000);
      await q.page.clock.setFixedTime(yesterday);
      await loginStudent(q, D.username);
      const clientNow = await q.page.evaluate(() => new Date().toString());
      const still = await text(q.page, '#s_note');
      const fromBrowser = await q.page.evaluate(async ([api, id, uid]) => (await fetch(`${api}/api/requests/${id}/submit`, { method: 'POST', headers: { 'X-User-Id': uid } })).status, [API, id, String(D.id)]);
      await shot(q.page, 'T19-ST-03-step4-client-clock.png', 'ST-03 ขั้น 4 ย้อนนาฬิกาเครื่อง', q.log);
      await q.ctx.close();
      step('ST-03', 4, still.startsWith('เกินระยะเวลาที่กำหนด') && fromBrowser === 409,
         `นาฬิกาเบราว์เซอร์ = ${clientNow.slice(0, 24)} ยังแจ้งหมดเขต และส่งจากหน้าเว็บได้ ${fromBrowser} (ใช้เวลา server)`);

      const db = await api('GET', '/api/requests/' + id, D.id);
      const mails = emails('geneva@dome.tu.ac.th').length;
      step('ST-03', 5, db.data.status !== 'SUBMITTED' && mails === 0, `ฐานข้อมูล: สถานะ ${db.data.status} · Email ถึงนักศึกษา ${mails} ฉบับ`);
      return 'ผ่านครบ 5 ขั้น';
   });

   console.log('\nตรวจตามลูกศรใน Sequence Diagram (ST-01)');
   for (const [a, ok] of arrows) console.log(`   ${ok ? '✓' : '✗'} ${a}`);
   const pass = results.filter(r => r.ok).length;
   console.log(`\nสรุป: ทั้งหมด ${results.length} scenario · ผ่าน ${pass} · ไม่ผ่าน ${results.length - pass}`);
   await browser.close();
   server.close();
   process.exit(pass === results.length ? 0 : 1);
}

main().catch(e => { console.error(e); process.exit(1); });
