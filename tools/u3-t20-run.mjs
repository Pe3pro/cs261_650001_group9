/* =====================================================================
   U3:T20 รัน User Acceptance Test ตาม T16.md (UAT-01 ถึง UAT-05) แบบ dry-run ของทีม

   ใช้: (รันจากโฟลเดอร์หลักของโปรเจกต์ บนฐานข้อมูลใหม่ที่มีแค่ข้อมูลจาก DemoDataSeeder)
     docker compose down -v
     docker compose up -d --build
     npm i -D playwright            (ครั้งแรก)
     node tools/u3-t20-run.mjs

   - เบราว์เซอร์: ค่าเริ่มต้นใช้ Microsoft Edge ที่มีในเครื่อง (ไม่ต้อง playwright install)
     เปลี่ยนได้ด้วย U3_BROWSER=chromium (ต้อง npx playwright install chromium ก่อน)
   - สคริปต์ทำตาม scenario ใน T16 ผ่านหน้าเว็บจริงเหมือนผู้ทดสอบ (login.html → submit.html / staff-review.html)
     แล้วตรวจ "เกณฑ์ผ่านที่ user ยอมรับได้" เท่าที่ตรวจด้วยเครื่องได้ (ข้อความบนจอ ข้อมูลที่เจ้าหน้าที่เห็น ไฟล์ PDF)
   - ข้ามหน้าตรวจคุณสมบัติ U2 (ตั้ง unlocked = 2) เหมือนสคริปต์ T17/T19 · U2 มี UAT ของตัวเองแล้ว
   - บัญชี: P1 = F 6609650006 · P2 = G 6609650007 · P3 = H 6609650008 (สคริปต์ยื่นคำร้องให้ H ผ่าน API แล้วให้เจ้าหน้าที่ปฏิเสธก่อน)
     ไม่ใช้ C/D (ข้อมูล U4) และไม่ปฏิเสธคำร้อง seed ของ A (ข้อมูลร่วม U4/U5)
     UAT-03 ใช้ 6609650002 · เจ้าหน้าที่ P4 = staff01
   - ลำดับ: UAT-02a → UAT-01 (P1, P2, P3) → UAT-02b → UAT-03 → UAT-05 → UAT-04 (ทำให้ฟอร์มหมดเขต จึงรันท้ายสุด)
     จบแล้วเปิดแบบฟอร์มใหม่ที่ยังไม่หมดเขต (ตามขั้นเตรียมระบบข้อ 5 ใน T20.md)
   - ผล: console + .doc/UserStory/U3/evidence/T20-uat-output.txt (log) + T20-uat-result.json + T20-UAT-*.png
   ===================================================================== */
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import {
   OUT, API, ACC, PDF, ANSWERS, sh, sleep, api, backendUp, publishForm, serverTime, addSeconds, submitViaApi,
   ensureRanked, startStatic, newPage, loginStudent, loginStaff, fillForm, clickSend, text, shot
} from './u3-common.mjs';

const PORT = Number(process.env.T20_PORT) || 5597;
const LOG_CMD = process.env.U3_LOG_CMD || 'docker compose logs app';
const emails = to => { try { return sh(LOG_CMD).split('\n').filter(l => l.includes('[EMAIL] to=' + to)); } catch { return []; } };

const lines = [];
const log = s => { console.log(s); lines.push(s); };
const results = [];
function check(id, ok, detail) {
   log(`   ${ok ? '✓' : '✗'} ${id}: ${detail}`);
   if (!ok) throw new Error(detail);
}
async function uat(id, tester, fn) {
   log(`▶ ${id} (${tester})`);
   const t0 = Date.now();
   try {
      const actual = await fn();
      const sec = ((Date.now() - t0) / 1000).toFixed(1);
      results.push({ id, tester, ok: true, seconds: Number(sec), actual });
      log(`✅ ${id} ${tester} ผ่าน (${sec} วินาที) ${actual}`);
   } catch (e) {
      const sec = ((Date.now() - t0) / 1000).toFixed(1);
      results.push({ id, tester, ok: false, seconds: Number(sec), actual: e.message.split('\n')[0] });
      log(`❌ ${id} ${tester} ไม่ผ่าน (${sec} วินาที) ${e.message.split('\n')[0]}`);
   }
}

/* ข้อมูลที่ผู้ทดสอบแต่ละคนกรอก */
const DATA = {
   P1: { s_name: 'F frankfurt', s_tel: '0811111111', s_mail: 'frankfurt@dome.tu.ac.th', s_addr: '1 ม.ธ. รังสิต อ.คลองหลวง จ.ปทุมธานี' },
   P2: { s_name: 'G geneva', s_tel: '0822222222', s_mail: 'geneva@dome.tu.ac.th', s_addr: '2 ถ.พหลโยธิน อ.คลองหลวง จ.ปทุมธานี' },
   P3: { s_name: 'H hamburg', s_tel: '0833333333', s_mail: 'hamburg@dome.tu.ac.th', s_addr: '3 หอพักเชียงราก จ.ปทุมธานี' }
};

/* เจ้าหน้าที่เปิดคำร้องของนักศึกษาในหน้า staff-review แล้วบันทึกผล (RETURN / REJECT) */
async function staffDecide(browser, base, username, decision, comment, png) {
   const s = await newPage(browser, base);
   await loginStaff(s);
   await s.page.waitForSelector('#appRows tr');
   await s.page.locator('#appRows tr', { hasText: username }).first().locator('[data-open]').click();
   await s.page.check(`input[name="decision"][value="${decision}"]`);
   await s.page.fill('#comment', comment);
   await s.page.click('#btnDecide');
   await s.page.waitForSelector('#vDone:not([hidden])');
   if (png) await shot(s.page, png, `เจ้าหน้าที่ ${decision} คำร้องของ ${username}`, s.log);
   await s.ctx.close();
}

/* นักศึกษายื่นคำร้องผ่านหน้าเว็บ คืน { id, alert, ok } */
async function studentSubmit(p, values, fileName = 'transcript.pdf', buffer = PDF) {
   await fillForm(p.page, values);
   await p.page.setInputFiles('#s_tr', { name: fileName, mimeType: 'application/pdf', buffer });
   await clickSend(p.page);
   const alert = await text(p.page, '#sAlert');
   const ok = (await p.page.locator('#sAlert').getAttribute('class')) === 'alert ok';
   const m = alert.match(/เลขที่คำร้อง (\d+)/);
   return { id: m ? Number(m[1]) : null, alert, ok };
}

/* ข้อมูลที่เจ้าหน้าที่เห็น (GET /api/applications/{id}/detail ที่หน้า staff-review ใช้) เทียบกับที่กรอก */
async function staffSees(id, values) {
   const d = (await api('GET', `/api/applications/${id}/detail`, ACC.staff.id)).data;
   const vals = (d.answers || []).map(a => a.value);
   const missing = Object.entries(values).filter(([, v]) => !vals.includes(v)).map(([k]) => k);
   return { d, missing };
}

async function main() {
   await mkdir(OUT, { recursive: true });
   if (!(await backendUp(5000))) {
      console.error('❌ เรียก backend ที่ ' + API + ' ไม่ได้ (รัน docker compose up -d --build ก่อน)');
      process.exit(1);
   }
   await ensureRanked(ACC.s3, ACC.s4, ACC.s6);   // flow ใหม่: จัดอันดับบริษัท (U4) ให้ครบก่อนยื่นคำร้อง
   const { server, base } = await startStatic(PORT);
   const kind = process.env.U3_BROWSER || 'msedge';
   const browser = kind === 'chromium' ? await chromium.launch() : await chromium.launch({ channel: kind });
   const started = new Date();
   log(`U3:T20 UAT (dry-run) · ${started.toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' })} · ${kind} ${browser.version()} · API ${API} · Web ${base}`);
   const P1 = ACC.s3, P2 = ACC.s4, P3 = ACC.s6, B = ACC.s2;
   const ids = {};

   /* ---------- UAT-02a กรอกไม่ครบ: รู้ทันทีว่าต้องแก้ช่องไหน ข้อมูลเดิมไม่หาย ---------- */
   await uat('UAT-02a', 'P2 กรอกไม่ครบ', async () => {
      const p = await newPage(browser, base);
      await loginStudent(p, P2.username);
      const partial = { ...DATA.P2, s_addr: '' };
      await fillForm(p.page, partial);
      await p.page.click('#btnSend');
      await sleep(300);
      const err = (await p.page.locator('#s_err').isVisible()) ? await text(p.page, '#s_err') : '';
      const invalid = await p.page.$$eval('[aria-invalid="true"]', els => els.map(e => e.id));
      const kept = {};
      for (const k of ['s_name', 's_tel', 's_mail']) kept[k] = await p.page.inputValue('#' + k);
      const reqs = (await api('GET', '/api/requests/me', P2.id)).data;
      await shot(p.page, 'T20-UAT-02a-missing-field.png', 'UAT-02a กรอกไม่ครบ', p.log);
      await p.ctx.close();
      check('UAT-02a', err !== '' && invalid.join() === 's_addr', `ข้อความ "${err}" · ช่องที่ถูกไฮไลต์: ${invalid.join(', ') || '(ไม่มี)'}`);
      check('UAT-02a', ['s_name', 's_tel', 's_mail'].every(k => kept[k] === DATA.P2[k]), `ข้อมูลที่กรอกแล้วยังอยู่: ${Object.values(kept).join(' · ')}`);
      check('UAT-02a', !reqs.some(r => r.status === 'SUBMITTED'), `ยังไม่มีคำร้องที่ส่งออกไป (คำร้องของ P2 ${reqs.length} รายการ ไม่มีสถานะ SUBMITTED)`);
      return `แจ้ง "${err}" ไฮไลต์ช่องที่อยู่ ข้อมูลอื่นไม่หาย`;
   });

   /* ---------- UAT-01 ยื่นสำเร็จ (P1, P2, P3) ---------- */
   await submitViaApi(P3, DATA.P3);
   await staffDecide(browser, base, P3.username, 'REJECT', 'UAT: ปฏิเสธคำร้องเดิมเพื่อให้ P3 ยื่นใหม่');
   log('   (เตรียม) ยื่นคำร้องให้ ' + P3.username + ' ผ่าน API แล้วเจ้าหน้าที่ปฏิเสธแล้ว');
   for (const [tag, acc] of [['P1', P1], ['P2', P2], ['P3', P3]]) {
      await uat('UAT-01', tag, async () => {
         const t0 = Date.now();
         const p = await newPage(browser, base);
         await loginStudent(p, acc.username);
         const r = await studentSubmit(p, DATA[tag]);
         const sec = (Date.now() - t0) / 1000;
         await shot(p.page, `T20-UAT-01-${tag}-result.png`, `UAT-01 ${tag} ผลการยื่นคำร้อง`, p.log);
         await p.ctx.close();
         check('UAT-01', r.ok && !!r.id, `หน้าจอ "${r.alert}"`);
         ids[tag] = r.id;
         const { d, missing } = await staffSees(r.id, DATA[tag]);
         check('UAT-01', d.status === 'SUBMITTED' && missing.length === 0 && d.studentId === acc.username && d.documents.length === 1,
            `เจ้าหน้าที่เห็นคำร้อง #${r.id} ของ ${d.studentId} สถานะ ${d.status} ข้อมูลตรงทุกช่อง${missing.length ? ' (ไม่ตรง: ' + missing.join(', ') + ')' : ''} · Transcript ${d.documents.length} ไฟล์`);
         check('UAT-01', sec < 300, `ใช้เวลา login → ยื่นเสร็จ ${sec.toFixed(1)} วินาที (< 5 นาที)`);
         return `คำร้อง #${r.id}`;
      });
   }
   if (ids.P1) {
      const s = await newPage(browser, base);
      await loginStaff(s);
      await s.page.waitForSelector('#appRows tr');
      await shot(s.page, 'T20-UAT-01-staff-list.png', 'UAT-01 รายการคำร้องที่เจ้าหน้าที่เห็น', s.log);
      await s.ctx.close();
   }

   /* ---------- UAT-02b ถูกส่งกลับให้แก้ แก้แล้วส่งใหม่ ---------- */
   await uat('UAT-02b', 'P1 + P4', async () => {
      const reason = 'กรุณาตรวจเบอร์โทรศัพท์ และแนบ Transcript ฉบับล่าสุด';
      await staffDecide(browser, base, P1.username, 'RETURN', reason, 'T20-UAT-02b-staff-return.png');
      const p = await newPage(browser, base);
      await loginStudent(p, P1.username);
      const note = await text(p.page, '#s_note');
      const kept = {};
      for (const k of Object.keys(DATA.P1)) kept[k] = await p.page.inputValue('#' + k);
      await shot(p.page, 'T20-UAT-02b-student-sees-reason.png', 'UAT-02b นักศึกษาเห็นเหตุผลที่ต้องแก้', p.log);
      check('UAT-02b', note.includes(reason), `หน้าเว็บแจ้ง "${note}"`);
      check('UAT-02b', Object.keys(DATA.P1).every(k => kept[k] === DATA.P1[k]), 'ข้อมูลที่กรอกไว้แล้วยังอยู่ครบ ไม่ต้องกรอกใหม่');
      const fixed = { ...DATA.P1, s_tel: '0899999999' };
      const NEW_PDF = Buffer.concat([PDF, Buffer.from('% UAT ฉบับแก้ไข\n')]);
      const r = await studentSubmit(p, fixed, 'transcript-v2.pdf', NEW_PDF);
      await shot(p.page, 'T20-UAT-02b-resubmit.png', 'UAT-02b ส่งใหม่หลังแก้ไข', p.log);
      await p.ctx.close();
      check('UAT-02b', r.ok && r.id === ids.P1, `หน้าจอ "${r.alert}" (คำร้องเดิม #${ids.P1})`);
      const { d, missing } = await staffSees(r.id, fixed);
      const doc = d.documents;
      check('UAT-02b', missing.length === 0 && doc.length === 1 && doc[0].fileName === 'transcript-v2.pdf',
         `เจ้าหน้าที่เห็นฉบับล่าสุด: เบอร์ 0899999999 · ไฟล์ ${doc.map(x => x.fileName).join(', ')}`);
      return 'ส่งใหม่ได้ เจ้าหน้าที่เห็นข้อมูลฉบับล่าสุด';
   });

   /* ---------- UAT-03 ยื่นซ้ำ ---------- */
   await uat('UAT-03', 'นักศึกษา 6609650002', async () => {
      const before = (await api('GET', '/api/requests/me', B.id)).data;
      const p = await newPage(browser, base);
      await loginStudent(p, B.username);
      const note = await text(p.page, '#s_note');
      const disabled = await p.page.locator('#btnSend').isDisabled();
      await shot(p.page, 'T20-UAT-03-duplicate.png', 'UAT-03 ยื่นซ้ำ', p.log);
      await p.ctx.close();
      check('UAT-03', note.includes('คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว') && /เลขที่คำร้อง \d+ · สถานะ/.test(note) && disabled,
         `หน้าเว็บ "${note}" · ปุ่มส่ง${disabled ? 'กดไม่ได้' : 'ยังกดได้'}`);
      const s = await newPage(browser, base);
      await loginStaff(s);
      await s.page.waitForSelector('#appRows tr');
      const rows = await s.page.locator('#appRows tr', { hasText: B.username }).count();
      await s.ctx.close();
      const after = (await api('GET', '/api/requests/me', B.id)).data;
      check('UAT-03', rows === 1 && after.length === before.length, `เจ้าหน้าที่เห็นคำร้องของ ${B.username} ${rows} แถว · คำร้องในระบบ ${before.length} → ${after.length}`);
      return 'ระบบไม่ให้ยื่นซ้ำ เจ้าหน้าที่ไม่เห็นคำร้องซ้ำ';
   });

   /* ---------- UAT-05 เจ้าหน้าที่ได้รับคำร้องครบถ้วน ---------- */
   await uat('UAT-05', 'P4 เจ้าหน้าที่', async () => {
      const s = await newPage(browser, base);
      await loginStaff(s);
      await s.page.waitForSelector('#appRows tr');
      await s.page.locator('#appRows tr', { hasText: P2.username }).first().locator('[data-open]').click();
      await s.page.waitForSelector('[data-doc]');
      const body = await s.page.locator('body').innerText();
      const need = [P2.username, ...Object.values(DATA.P2)];
      const missing = need.filter(v => !body.includes(v));
      const [resp] = await Promise.all([
         s.ctx.waitForEvent('response', r => r.url().includes('/api/documents/')),
         s.page.locator('[data-doc]').first().click()
      ]);
      const type = (await resp.allHeaders())['content-type'];
      await shot(s.page, 'T20-UAT-05-staff-detail.png', 'UAT-05 เจ้าหน้าที่เปิดคำร้อง', s.log);
      await s.ctx.close();
      check('UAT-05', missing.length === 0, `หน้าตรวจแสดง รหัส ชื่อ เบอร์ อีเมล ที่อยู่ ครบ${missing.length ? ' (ขาด: ' + missing.join(', ') + ')' : ''}`);
      check('UAT-05', resp.status() === 200 && type === 'application/pdf', `กดดู Transcript → ${resp.status()} ${type}`);
      return `คำร้อง #${ids.P2} ข้อมูลครบ เปิด PDF ได้`;
   });

   /* ---------- UAT-04 ยื่นหลังหมดเขต (รันท้ายสุด) ---------- */
   await uat('UAT-04', 'P2 หลังหมดเขต', async () => {
      await staffDecide(browser, base, P2.username, 'REJECT', 'UAT-04: ปฏิเสธเพื่อให้ P2 ลองยื่นใหม่หลังหมดเขต');
      const deadline = addSeconds(await serverTime(P2.id), 15);
      await publishForm('U3 T20 ฟอร์มหมดเขต', deadline);
      while (!(await api('GET', '/api/forms/published', P2.id)).data.deadlinePassed) await sleep(1000);
      log(`   (เตรียม) เปิดแบบฟอร์มที่หมดเขต ${deadline} (เวลา server) และรอจนเลยเวลาแล้ว`);
      const mailsBefore = emails('geneva@dome.tu.ac.th').length;
      const p = await newPage(browser, base);
      await loginStudent(p, P2.username);
      const note = await text(p.page, '#s_note');
      const disabled = await p.page.locator('#btnSend').isDisabled();
      await fillForm(p.page, DATA.P2).catch(() => { });
      await p.page.setInputFiles('#s_tr', { name: 'transcript.pdf', mimeType: 'application/pdf', buffer: PDF }).catch(() => { });
      // ลองกดส่ง 3 ครั้ง (ถ้าปุ่มถูกปิดก็ปลดออกก่อน เหมือนผู้ใช้ที่พยายามหาทางส่ง)
      for (let i = 0; i < 3; i++) {
         await p.page.evaluate(() => document.querySelector('#btnSend').removeAttribute('disabled'));
         await p.page.click('#btnSend').catch(() => { });
         await sleep(1500);
      }
      await shot(p.page, 'T20-UAT-04-deadline.png', 'UAT-04 ยื่นหลังหมดเขต', p.log);
      const tries = p.log.filter(x => x.method === 'POST' && /\/submit$/.test(x.url)).map(x => x.status);
      await p.ctx.close();
      check('UAT-04', note.startsWith('เกินระยะเวลาที่กำหนด') && note.includes('หมดเขต') && disabled, `หน้าเว็บ "${note}" · ปุ่มส่ง${disabled ? 'กดไม่ได้' : 'ยังกดได้'}`);
      const mine = (await api('GET', '/api/requests/me', P2.id)).data;
      const active = mine.filter(r => r.status === 'SUBMITTED');
      const mails = emails('geneva@dome.tu.ac.th').length;
      check('UAT-04', active.length === 0 && tries.every(s => s === 409) && mails === mailsBefore,
         `ลองส่ง 3 ครั้ง: POST submit ${tries.length ? tries.join(', ') : 'ไม่ถูกส่งจากหน้าเว็บ'} · ไม่มีคำร้อง SUBMITTED ของ P2 · Email ใหม่ ${mails - mailsBefore} ฉบับ`);
      return 'ส่งไม่ได้ทุกครั้ง แจ้งวันหมดเขตชัดเจน';
   });

   /* คืนสภาพระบบ: เปิดแบบฟอร์มใหม่ที่ยังไม่หมดเขต (+7 วัน) */
   try {
      const open = addSeconds(await serverTime(ACC.staff.id), 7 * 24 * 3600);
      await publishForm('แบบฟอร์มขอฝึกสหกิจศึกษา (หลัง UAT)', open);
      log(`   (คืนสภาพ) เปิดแบบฟอร์มใหม่ หมดเขต ${open}`);
   } catch (e) { log('   (คืนสภาพ) เปิดแบบฟอร์มใหม่ไม่ได้: ' + e.message); }

   const pass = results.filter(r => r.ok).length;
   log(`\nสรุป: ทั้งหมด ${results.length} รายการ · ผ่าน ${pass} · ไม่ผ่าน ${results.length - pass}`);
   await writeFile(join(OUT, 'T20-uat-output.txt'), lines.join('\n') + '\n');
   await writeFile(join(OUT, 'T20-uat-result.json'), JSON.stringify({ started: started.toISOString(), browser: `${kind} ${browser.version()}`, results }, null, 2));
   await browser.close();
   server.close();
   process.exit(pass === results.length ? 0 : 1);
}

main().catch(e => { console.error(e); process.exit(1); });
