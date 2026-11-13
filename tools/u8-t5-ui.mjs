/* =====================================================================
   U8:T5 ส่วนที่ 5 · รัน Integration Test UI–API ตาม T5.md ส่วนที่ 1 (IT-UA-01 ถึง IT-UA-17)
   รันผ่าน tools/u8-t5-run.ps1 (ใน container Playwright บน docker network ของ compose)
     node u8-t5-ui.mjs main   → IT-UA-01 ถึง 16 (ฐานข้อมูลใหม่ + seed ของ U8)
     node u8-t5-ui.mjs down   → IT-UA-17 ขณะ docker compose stop app
     node u8-t5-ui.mjs up     → IT-UA-17 (ต่อ) หลัง docker compose start app ใช้งานต่อได้
   - หน้าเว็บ frontend/ จริง เรียก backend จริง · บัญชี A = 6609650031, C = 6609650033, อาจารย์ = advisor01
   - ฝั่งอาจารย์ยังไม่มีหน้าเว็บ (T4 หัวข้อ 3) จึงยิง API ตรงแทน Postman
   - ผล: .doc/UserStory/U8/evidence/T5-ui-api-*-output.txt · *-result.json · T5-IT-UA-*.png
   ===================================================================== */
import { chromium } from 'playwright';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import {
   OUT, PDF, api, logBody, backendUp, users, startStatic, newPage, login, openWorklog, settle, fill, val, txt, shown,
   histText, openRow, shot, reporter, check, apiLog, serverToday, thaiToday, addDays, weekdaysBefore, weekdayAtLeast, sleep, sql, closeDb
} from './u8-common.mjs';

const phase = process.argv[2] || 'main';
const R = reporter('T5-ui-api-' + phase);

const { server, base } = await startStatic();
const browser = await chromium.launch();
try {
   if (phase === 'main') {
      if (!(await backendUp())) throw new Error('backend ไม่ทำงาน');
      await main();
   } else {
      if (phase === 'up' && !(await backendUp())) throw new Error('backend ไม่กลับมา');
      await serverDown(phase);
   }
} catch (e) {
   R.say('หยุดกลางคัน: ' + e.stack);
} finally {
   await R.save({ phase });
   await browser.close(); server.close(); await closeDb();
}

async function main() {
   const U = await users();
   const today = serverToday();
   const [d1, d2, d3] = weekdaysBefore(today);
   const late = weekdayAtLeast(5, today);
   R.say(`ข้อมูล: A=${U.A} B=${U.B} C=${U.C} advisor=${U.advisor} · internship A id ${U.iA.id} เริ่ม ${U.iA.startDate} · วันนี้ของ backend ${today} · วันนี้ของเบราว์เซอร์ ${thaiToday()} · ใช้วันที่ ${d1}, ${d2}, ${d3}, ส่งล่าช้า ${late}`);
   const s = await newPage(browser, base);
   const pg = s.page;
   await login(s, '6609650031');
   const ids = {};

   await R.run('IT-UA-01', async () => {
      const i = s.mark();
      await openWorklog(s);
      const req = s.since(i);
      const sum = (await api('GET', `/api/internships/${U.iA.id}/work-logs/summary`, U.A)).data;
      const date = await val(pg, '#wDate'), hist = await txt(pg, '#wHist'), hours = await txt(pg, '#sumHours'), miss = await txt(pg, '#sumMissing'), due = await txt(pg, '#wDue');
      await shot(pg, 'T5-IT-UA-01-open.png', 'IT-UA-01 เปิดหน้าบันทึก', req);
      const got = req.map(x => `${x.method} ${x.url.split('?')[0]} ${x.status}`);
      check(got.some(x => x.includes('my-internship 200')) && got.some(x => / \/api\/internships\/\d+\/work-logs 200/.test(' ' + x.slice(4))) && got.some(x => x.includes('summary 200')), 'request ไม่ครบ ' + got.join(', '));
      check(date === thaiToday() && hist.includes('ยังไม่มีบันทึก') && hours === '0 ชม.' && miss === sum.missingDates.length + ' วัน' && due.includes('กำหนดส่งบันทึกวันที่'),
         `date ${date} hist "${hist}" hours ${hours} missing ${miss} (API ${sum.missingDates.length})`);
      return `${apiLog(req).join(' · ')} · วันที่ตั้งต้น ${date} (วันนี้) · ประวัติ "ยังไม่มีบันทึก" · ชั่วโมงสะสม ${hours} · ขาดบันทึก ${miss} = missingDates ${sum.missingDates.length} วัน · แถบ "${due.slice(0, 60)}"`;
   });

   await R.run('IT-UA-02', async () => {
      await fill(pg, { date: d1, start: '09:00', end: '12:00', task: 'ประชุมทีมและวางแผนงาน' });
      const hrs = await val(pg, '#wHours');
      const i = s.mark(); await pg.click('#wDraft'); await settle(pg);
      const req = s.since(i), post = req.find(x => x.method === 'POST');
      const saved = await txt(pg, '#wSaved'), row = await histText(pg, d1), sumH = await txt(pg, '#sumHours');
      await shot(pg, 'T5-IT-UA-02-draft.png', 'IT-UA-02 บันทึกแบบร่าง', req);
      const body = JSON.parse(post.body); ids.d1 = body.id;
      check(hrs === '3 ชั่วโมง' && post.status === 201 && body.status === 'DRAFT' && Number(body.hours) === 3 && saved.includes('บันทึกฉบับร่างแล้ว') && row.includes('ฉบับร่าง') && row.includes('3 ชม.') && sumH === '0 ชม.',
         `hours "${hrs}" ${post.status} ${post.body} saved "${saved}" row "${row}" sum ${sumH}`);
      return `ช่องชั่วโมง "${hrs}" ก่อนกด · POST /api/internships/${U.iA.id}/work-logs → 201 id ${body.id} DRAFT hours ${body.hours} · "${saved.slice(0, 40)}" · ประวัติ "${row}" · ชั่วโมงสะสม ${sumH}`;
   });

   await R.run('IT-UA-03', async () => {
      const cases = [
         ['เวลาสิ้นสุดก่อนเวลาเริ่ม', { date: d2, start: '12:00', end: '09:00', task: 'งาน A' }, 'เวลาสิ้นสุดต้องหลังเวลาเริ่ม'],
         ['วันที่พรุ่งนี้', { date: addDays(thaiToday(), 1), start: '09:00', end: '12:00', task: 'งาน B' }, 'ไม่สามารถบันทึกวันที่ในอนาคตได้'],
         ['06:00–19:00', { date: d2, start: '06:00', end: '19:00', task: 'งาน C' }, 'ชั่วโมงทำงานต้องอยู่ระหว่าง 0.5 ถึง 12 ชั่วโมง'],
         ['ไม่กรอกงานที่ทำ', { date: d2, start: '09:00', end: '12:00', task: '' }, 'กรุณากรอกงานที่ทำ']];
      const out = [];
      for (const [name, v, msg] of cases) {
         await pg.click('#wNew'); await fill(pg, v);
         const i = s.mark(); await pg.click('#wDraft'); await sleep(700);
         const err = await txt(pg, '#wErr'), req = s.since(i);
         const kept = [await val(pg, '#wDate'), await val(pg, '#wStart'), await val(pg, '#wEnd'), await val(pg, '#wTask') || '(ว่าง)'].join(' / ');
         if (name === 'ไม่กรอกงานที่ทำ') await shot(pg, 'T5-IT-UA-03-validate.png', 'IT-UA-03 ตรวจข้อมูลฝั่งหน้าเว็บ (ไม่กรอกงานที่ทำ)', req);
         check(req.length === 0 && err.includes(msg) && (await val(pg, '#wStart')) === v.start && (await val(pg, '#wTask')) === v.task, `${name}: req ${apiLog(req)} err "${err}" kept ${kept}`);
         out.push(`${name}: "${err}" ไม่มี request ค่าที่กรอกอยู่ (${kept})`);
      }
      return out.join(' · ');
   });

   await R.run('IT-UA-04', async () => {
      await pg.click('#wNew');
      await fill(pg, { date: d2, start: '09:00', end: '12:00', task: 'งานจากหน้าเว็บ' });
      const other = await api('POST', `/api/internships/${U.iA.id}/work-logs`, U.A, logBody(d2, '08:00', '10:00', 'บันทึกจาก client อื่น (Postman)'));
      ids.d2 = other.data.id;
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i), err = await txt(pg, '#wErr');
      const kept = [await val(pg, '#wDate'), await val(pg, '#wStart'), await val(pg, '#wEnd'), await val(pg, '#wTask')].join(' / ');
      await shot(pg, 'T5-IT-UA-04-duplicate.png', 'IT-UA-04 วันที่ซ้ำจาก server', req);
      const post = req.find(x => x.method === 'POST');
      check(other.status === 201 && post && post.status === 409 && post.body.includes('DUPLICATE_LOG_DATE') && err.includes('มีบันทึกของวันที่นี้แล้ว') && (await val(pg, '#wTask')) === 'งานจากหน้าเว็บ',
         `other ${other.status} page ${apiLog(req)} err "${err}" kept ${kept}`);
      // ประวัติโหลดแล้ว: เลือกวันที่ที่มีบันทึกแล้ว → หน้าเว็บเปิดบันทึกเดิมแทน ไม่สร้างซ้ำ
      await openWorklog(s);
      const j = s.mark(); await pg.click('#wNew'); await fill(pg, { date: d2 }); await sleep(800);
      const req2 = s.since(j), title = await txt(pg, '#wFormTitle');
      check(!req2.some(x => x.method === 'POST') && title.includes('ฉบับร่าง'), `เลือกวันซ้ำ: ${apiLog(req2)} title "${title}"`);
      return `client อื่น (แทน Postman) สร้าง ${d2} → 201 · หน้าเว็บกดส่ง: ${apiLog(req).join(' · ')} แสดง "${err}" ค่าที่กรอกไม่หาย (${kept}) · หลังโหลดประวัติ เลือกวันที่ ${d2} หน้าเว็บเปิดบันทึกเดิม "${title}" (${apiLog(req2).join(' · ')}) ไม่มี POST`;
   });

   await R.run('IT-UA-05', async () => {
      await openWorklog(s);
      const i = s.mark(); await openRow(s, d1);
      const req1 = s.since(i);
      const filled = [await val(pg, '#wDate'), await val(pg, '#wStart'), await val(pg, '#wEnd'), await val(pg, '#wTask')].join(' / ');
      const del = await shown(pg, '#wDelete');
      await pg.fill('#wEnd', '13:00'); const hrs = await val(pg, '#wHours');
      const j = s.mark(); await pg.click('#wDraft'); await settle(pg);
      const req2 = s.since(j), put = req2.find(x => x.method === 'PUT'), row = await histText(pg, d1);
      await shot(pg, 'T5-IT-UA-05-edit.png', 'IT-UA-05 เปิดฉบับร่างแล้วแก้ไข', [...req1, ...req2]);
      check(req1.some(x => x.method === 'GET' && x.url === `/api/work-logs/${ids.d1}` && x.status === 200) && filled.startsWith(d1 + ' / 09:00 / 12:00') && del && hrs === '4 ชั่วโมง' && put && put.status === 200 && Number(JSON.parse(put.body).hours) === 4 && row.includes('4 ชม.'),
         `${apiLog(req1)} filled ${filled} del ${del} hrs ${hrs} ${apiLog(req2)} row ${row}`);
      return `${apiLog(req1).join(' · ')} ฟอร์มเติม ${filled} มีปุ่ม "ลบบันทึกร่าง" · แก้เวลาสิ้นสุด 13:00 ช่องชั่วโมง "${hrs}" → ${apiLog(req2.filter(x => x.method !== 'GET')).join(' · ')} hours 4.00 · ประวัติ "${row}"`;
   });

   await R.run('IT-UA-06', async () => {
      await pg.setInputFiles('#wFile', { name: 'note.pdf', mimeType: 'application/pdf', buffer: PDF });
      const i = s.mark(); await pg.click('#wDraft'); await settle(pg);
      const req = s.since(i), att = req.find(x => x.url.endsWith('/attachments'));
      const files = await txt(pg, '#wFiles');
      await shot(pg, 'T5-IT-UA-06-attach.png', 'IT-UA-06 แนบไฟล์ PDF', req);
      check(req[0] && req[0].method === 'PUT' && att && att.status === 201 && att.body.includes('application/pdf') && files.includes('note.pdf') && files.includes('ลบไฟล์'), `${apiLog(req)} files "${files}"`);
      return `${apiLog(req.filter(x => x.method !== 'GET')).join(' · ')} contentType application/pdf · รายการไฟล์แนบ "${files}"`;
   });

   await R.run('IT-UA-07', async () => {
      const out = [];
      for (const [f, msg] of [[{ name: 'note.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') }, 'แนบได้เฉพาะไฟล์รูป (JPG, PNG) หรือ PDF'],
      [{ name: 'big.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(10 * 1024 * 1024 + 1) }, 'ไฟล์ต้องมีขนาดไม่เกิน 10 MB']]) {
         await pg.setInputFiles('#wFile', f);
         const i = s.mark(); await pg.click('#wDraft'); await sleep(1200);
         const att = s.since(i).filter(x => x.url.includes('/attachments')), err = await txt(pg, '#wErr');
         if (f.name === 'note.txt') await shot(pg, 'T5-IT-UA-07-file-type.png', 'IT-UA-07 ไฟล์ไม่ผ่านเงื่อนไข (.txt)', s.since(i));
         check(att.length === 0 && err.includes(msg), `${f.name}: ${apiLog(att)} err "${err}"`);
         out.push(`${f.name}: "${err}" ไม่มี request attachments`);
         await pg.setInputFiles('#wFile', []);
      }
      const direct = await api('POST', `/api/work-logs/${ids.d1}/attachments`, U.A, undefined, { name: 'note.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });
      check(direct.status === 400 && direct.data.code === 'INVALID_ATTACHMENT', 'ยิงตรง ' + direct.status);
      return out.join(' · ') + ` · ยิง .txt ตรง (แทน Postman) → ${direct.status} ${direct.data.code}`;
   });

   await R.run('IT-UA-08', async () => {
      await openWorklog(s); await openRow(s, d1);
      const before = await txt(pg, '#sumHours');
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i), sub = req.find(x => x.url.endsWith('/submit'));
      const ok = await txt(pg, '#wOk'), row = await histText(pg, d1), after = await txt(pg, '#sumHours');
      await shot(pg, 'T5-IT-UA-08-submit.png', 'IT-UA-08 ส่งบันทึก', req);
      await openRow(s, d1);
      const locked = await pg.evaluate(() => ({ inputs: [...document.querySelectorAll('#wForm input:not([type=file]), #wForm textarea')].every(e => e.disabled || e.readOnly), file: document.querySelector('#wFile').disabled, send: document.querySelector('#wSend').disabled, draft: document.querySelector('#wDraft').disabled, del: !!document.querySelector('#wDelete').offsetParent }));
      await shot(pg, 'T5-IT-UA-08-locked.png', 'IT-UA-08 เปิดบันทึกที่ส่งแล้ว แก้ไม่ได้', []);
      const b = JSON.parse(sub.body);
      check(sub.status === 200 && b.status === 'SUBMITTED' && b.lateSubmitted === false && ok.includes('ส่งบันทึกสำเร็จ') && row.includes('ส่งแล้ว') && before === '0 ชม.' && after === '4 ชม.' && locked.inputs && locked.file && locked.send && locked.draft && !locked.del,
         `${apiLog(req)} ok "${ok}" row ${row} sum ${before}->${after} locked ${JSON.stringify(locked)}`);
      return `${apiLog(req.filter(x => x.method !== 'GET')).join(' · ')} SUBMITTED lateSubmitted false · "${ok.slice(0, 30)}" · ประวัติ "${row}" · ชั่วโมงสะสม ${before} → ${after} · เปิดอีกครั้ง ช่องทั้งหมด / ไฟล์ / ปุ่มส่ง / ปุ่มร่างถูกปิด ไม่มีปุ่มลบ`;
   });

   await R.run('IT-UA-09', async () => {
      await pg.click('#wNew');
      await fill(pg, { date: d3, start: '09:00', end: '17:00', task: 'พัฒนา API' });
      const i = s.mark();
      const during = await pg.evaluate(() => { const b = document.querySelector('#wSend'); b.click(); const d = { text: b.textContent.trim(), disabled: b.disabled }; b.click(); return d; });
      await settle(pg); await sleep(500);
      const req = s.since(i).filter(x => x.method !== 'GET'), row = await histText(pg, d3);
      await shot(pg, 'T5-IT-UA-09-double-click.png', 'IT-UA-09 กดส่ง 2 ครั้งติดกัน', s.since(i));
      ids.d3 = JSON.parse(req[0].body).id;
      check(during.text === 'กำลังบันทึก...' && during.disabled && req.length === 2 && req[0].status === 201 && req[1].url.endsWith('/submit') && req[1].status === 200 && row.includes('ส่งแล้ว'), `${JSON.stringify(during)} ${apiLog(req)} row ${row}`);
      return `ระหว่างรอปุ่ม "${during.text}" disabled · ${apiLog(req).join(' · ')} (อย่างละ 1 ครั้ง) · ประวัติ "${row}"`;
   });

   await R.run('IT-UA-10', async () => {
      await openRow(s, d2);
      pg.dialogs.length = 0;
      pg.dialogAnswer = false; let i = s.mark(); await pg.click('#wDelete'); await sleep(800); const cancel = s.since(i);
      pg.dialogAnswer = true; i = s.mark(); await pg.click('#wDelete'); await settle(pg); const ok = s.since(i);
      const gone = !(await histText(pg, d2));
      await shot(pg, 'T5-IT-UA-10-delete.png', 'IT-UA-10 ลบฉบับร่าง (ยืนยัน)', ok);
      await openRow(s, d1); const delOnSubmitted = await shown(pg, '#wDelete');
      check(pg.dialogs[0]?.startsWith('ลบบันทึกร่างวันที่') && cancel.length === 0 && ok[0]?.method === 'DELETE' && ok[0].status === 204 && gone && !delOnSubmitted, `${pg.dialogs} cancel ${apiLog(cancel)} ok ${apiLog(ok)} gone ${gone}`);
      return `dialog "${pg.dialogs[0]}" · ยกเลิก: ไม่มี request · ยืนยัน: ${apiLog(ok).join(' · ')} แถว ${d2} หายจากประวัติ · บันทึกที่ส่งแล้ว (${d1}) ไม่มีปุ่มลบ`;
   });

   await R.run('IT-UA-11', async () => {
      await pg.click('#wNew');
      await fill(pg, { date: late, start: '09:00', end: '16:00', task: 'ทดสอบระบบ' });
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i).filter(x => x.method !== 'GET'), sub = req.find(x => x.url.endsWith('/submit'));
      const lateBox = (await shown(pg, '#wLate')) ? await txt(pg, '#wLate') : '', row = await histText(pg, late);
      await shot(pg, 'T5-IT-UA-11-late.png', 'IT-UA-11 ส่งล่าช้า', s.since(i));
      ids.late = JSON.parse(sub.body).id;
      check(sub.status === 200 && JSON.parse(sub.body).lateSubmitted === true && lateBox.includes('ส่งล่าช้า') && row.includes('ส่งล่าช้า'), `${apiLog(req)} ${sub.body} late "${lateBox}" row ${row}`);
      return `${late} → ${apiLog(req).join(' · ')} lateSubmitted true · กล่อง "${lateBox.slice(0, 60)}" · ประวัติ "${row}"`;
   });

   const adv = await newPage(browser, base);
   await login(adv, 'advisor01');
   await R.run('IT-UA-12', async () => {
      const i = adv.mark();
      await adv.page.goto(base + '/notifications.html');
      await adv.page.waitForSelector('#notifList .notif', { timeout: 15000 });
      const first = await txt(adv.page, '#notifList .notif'), req = adv.since(i).filter(x => x.url.startsWith('/api/notifications'));
      await shot(adv.page, 'T5-IT-UA-12-advisor-notif.png', 'IT-UA-12 อาจารย์ได้แจ้งเตือน', req);
      check(req[0]?.status === 200 && first.includes('มีบันทึกการทำงานรอตรวจ') && first.includes('IN_PROGRESS_31'), `${apiLog(req)} "${first}"`);
      return `${apiLog(req).join(' · ')} · บนสุด "${first.slice(0, 100)}"`;
   });

   await R.run('IT-UA-13', async () => {
      const rv = await api('POST', `/api/work-logs/${ids.d1}/review`, U.advisor, { decision: 'REVISE', comment: 'เพิ่มรายละเอียดงาน' });
      await openWorklog(s);
      const row = await histText(pg, d1);
      const i = s.mark(); await openRow(s, d1); const req = s.since(i);
      const review = await txt(pg, '#wReview'), editable = !(await pg.isDisabled('#wTask')) && !(await pg.isDisabled('#wSend')), del = await shown(pg, '#wDelete');
      await shot(pg, 'T5-IT-UA-13-revise.png', 'IT-UA-13 นักศึกษาเห็นความเห็นอาจารย์', req);
      check(rv.status === 200 && rv.data.status === 'NEED_REVISION' && row.includes('ขอให้แก้ไข') && req[0]?.status === 200 && req[0].body.includes('เพิ่มรายละเอียดงาน') && review.includes('เพิ่มรายละเอียดงาน') && editable && !del, `${rv.status} row ${row} ${apiLog(req)} review "${review}" editable ${editable} del ${del}`);
      return `อาจารย์ (แทน Postman) POST /api/work-logs/${ids.d1}/review REVISE → ${rv.status} ${rv.data.status} · ประวัติ "${row}" · ${apiLog(req).join(' · ')} มี reviewComment · กล่อง "${review}" · ฟอร์มแก้ได้ ไม่มีปุ่มลบ`;
   });

   await R.run('IT-UA-14', async () => {
      const before = (await api('GET', '/api/notifications', U.advisor)).data.length;
      await pg.fill('#wTask', 'ประชุมทีมและวางแผนงาน แบ่งงาน sprint 3 และสรุปงานที่ค้าง');
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i).filter(x => x.method !== 'GET'), row = await histText(pg, d1);
      const after = (await api('GET', '/api/notifications', U.advisor)).data;
      await shot(pg, 'T5-IT-UA-14-resubmit.png', 'IT-UA-14 แก้แล้วส่งใหม่', s.since(i));
      check(req[0]?.method === 'PUT' && req[0].status === 200 && req[1]?.status === 200 && JSON.parse(req[1].body).status === 'SUBMITTED' && row.includes('ส่ง') && after.length === before + 1, `${apiLog(req)} row ${row} notif ${before}->${after.length}`);
      return `${apiLog(req).join(' · ')} SUBMITTED · ประวัติ "${row}" · แจ้งเตือนอาจารย์ ${before} → ${after.length} ("${after[0].message}")`;
   });

   await R.run('IT-UA-15', async () => {
      const bulk = await api('POST', '/api/work-logs/review-bulk', U.advisor, { workLogIds: [ids.d1, ids.d3, ids.late, 9999], comment: 'เรียบร้อย' });
      const i = s.mark();
      await pg.goto(base + '/notifications.html'); await pg.waitForSelector('#notifList .notif', { timeout: 15000 });
      const notif = await txt(pg, '#notifList .notif');
      await shot(pg, 'T5-IT-UA-15-student-notif.png', 'IT-UA-15 นักศึกษาได้แจ้งเตือนอนุมัติ', s.since(i));
      await openWorklog(s);
      const rows = [await histText(pg, d1), await histText(pg, d3), await histText(pg, late)];
      await openRow(s, d1); const locked = (await pg.isDisabled('#wTask')) && (await pg.isDisabled('#wSend'));
      const sum = (await api('GET', `/api/internships/${U.iA.id}/work-logs/summary`, U.A)).data;
      await shot(pg, 'T5-IT-UA-15-approved.png', 'IT-UA-15 ประวัติ "อนุมัติแล้ว"', []);
      check(bulk.status === 200 && bulk.data.approved.length === 3 && bulk.data.skipped[0].workLogId === 9999 && notif.includes('ได้รับการอนุมัติ') && rows.every(r => r.includes('อนุมัติแล้ว')) && locked && sum.countByStatus.APPROVED === 3,
         `${bulk.status} ${JSON.stringify(bulk.data)} notif "${notif}" rows ${rows} locked ${locked} ${JSON.stringify(sum.countByStatus)}`);
      return `review-bulk (แทน Postman) → ${bulk.status} approved ${JSON.stringify(bulk.data.approved)} skipped 9999 "${bulk.data.skipped[0].reason}" · แจ้งเตือนบนสุด "${notif.slice(0, 70)}" · ประวัติ ${rows.join(' / ')} · เปิดแล้วแก้ไม่ได้ · countByStatus.APPROVED ${sum.countByStatus.APPROVED}`;
   });

   const c = await newPage(browser, base);
   await login(c, '6609650033');
   await R.run('IT-UA-16', async () => {
      const i = c.mark(); await openWorklog(c); const req = c.since(i);
      const err = await txt(c.page, '#wErr'), send = await c.page.isDisabled('#wSend'), draft = await c.page.isDisabled('#wDraft');
      await shot(c.page, 'T5-IT-UA-16-no-internship.png', 'IT-UA-16 นักศึกษาที่ไม่มีการฝึกงาน', req);
      check(req.some(x => x.url === '/api/work-logs/my-internship' && x.status === 404) && err.includes('ยังไม่มีข้อมูลการฝึกงาน') && send && draft, `${apiLog(req)} err "${err}" ${send} ${draft}`);
      return `${apiLog(req).join(' · ')} · "${err}" · ปุ่มส่งบันทึก / บันทึกแบบร่างถูกปิด`;
   });

   // เก็บ session ของ A ไว้ใช้ตอน backend หยุด (ผู้ใช้ที่ login ค้างไว้ก่อน server ล่ม)
   const session = await pg.evaluate(() => Object.fromEntries(Object.entries(sessionStorage)));
   await writeFile(join(OUT, 'T5-session-A.json'), JSON.stringify(session), 'utf8');
   const db = await sql('SELECT id, log_date, hours, status, submitted_at, reviewed_by FROM work_log ORDER BY id');
   R.say('\nwork_log หลังจบ: ' + JSON.stringify(db));
}

async function serverDown(ph) {
   const session = JSON.parse(await readFile(join(OUT, 'T5-session-A.json'), 'utf8'));
   const p = await newPage(browser, base);
   await p.page.goto(base + '/login.html');
   await p.page.evaluate(s => { for (const [k, v] of Object.entries(s)) sessionStorage.setItem(k, v); }, session);
   const i = p.mark();
   await p.page.goto(base + '/worklog.html');
   await sleep(4000);
   const req = p.since(i), err = await txt(p.page, '#wErr').catch(() => ''), hist = await txt(p.page, '#wHist').catch(() => '');
   if (ph === 'down') {
      await R.run('IT-UA-17', async () => {
         await shot(p.page, 'T5-IT-UA-17-server-down.png', 'IT-UA-17 server ไม่ทำงาน (docker compose stop app)', req);
         check(req.some(x => String(x.status).startsWith('FAILED')) && err.includes('ไม่สามารถเชื่อมต่อระบบได้') && (await p.page.isDisabled('#wSend')), `${apiLog(req)} err "${err}"`);
         return `docker compose stop app แล้วเปิดหน้าบันทึก: ${apiLog(req).join(' · ')} · "${err}" หน้าไม่ค้าง ปุ่มบันทึกถูกปิด`;
      });
   } else {
      await R.run('IT-UA-17 (เปิด app คืน)', async () => {
         await shot(p.page, 'T5-IT-UA-17-server-back.png', 'IT-UA-17 docker compose start app แล้วใช้งานต่อได้', req);
         check(req.length > 0 && req.every(x => x.status === 200) && hist.includes('อนุมัติแล้ว') && !(await shown(p.page, '#wErr')), `${apiLog(req)} "${err}"`);
         return `docker compose start app แล้วโหลดหน้าใหม่: ${apiLog(req).join(' · ')} · ประวัติกลับมาแสดง ("${hist.slice(0, 40)}...") ไม่มีข้อความ error`;
      });
   }
}
