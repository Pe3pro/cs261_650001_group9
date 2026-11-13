/* =====================================================================
   U8:T5 ส่วนที่ 7 · รัน System Test ตาม T5.md ส่วนที่ 3 (ST-U8-01 ถึง ST-U8-18)
   ทดสอบทั้งระบบตามเส้นทางใน Sequence Diagram (us8-07, us8-08, us8-09) แล้วตรวจ 3 จุด:
   หน้าจอ (แคปจอ) · Network (request /api/* ที่หน้าเว็บส่ง) · MySQL (query ตรง)
   รันผ่าน tools/u8-t5-run.ps1 บนฐานข้อมูลใหม่ (reset ก่อนรัน)
     node u8-t5-st.mjs main   → ST-U8-01 ถึง 17
     node u8-t5-st.mjs down   → ST-U8-18 ขณะ docker compose stop app
     node u8-t5-st.mjs up     → ST-U8-18 (ต่อ) หลัง docker compose start app
   ผล: .doc/UserStory/U8/evidence/T5-system-*-output.txt · *-result.json · T5-ST-U8-*.png · T5-ST-U8-14-export.csv
   ===================================================================== */
import { chromium } from 'playwright';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import {
   OUT, PDF, api, logBody, backendUp, users, startStatic, newPage, login, openWorklog, settle, fill, val, txt, shown,
   histText, openRow, shot, reporter, check, apiLog, serverToday, thaiToday, addDays, weekdaysBefore, weekdayAtLeast, sleep,
   sql, one, fileInVolume, closeDb
} from './u8-common.mjs';

const phase = process.argv[2] || 'main';
const R = reporter('T5-system-' + phase);
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

const count = () => one('SELECT COUNT(*) FROM work_log');
const notifCount = (uid, type) => one('SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ?', [uid, type]);
const row = id => sql('SELECT id, log_date, start_time, end_time, hours, status, submitted_at, reviewed_by, reviewed_at, review_comment FROM work_log WHERE id = ?', [id]).then(r => r[0]);

async function main() {
   const U = await users();
   const today = serverToday();
   const [d1, d2] = weekdaysBefore(today);
   const late = weekdayAtLeast(5, today);
   R.say(`ข้อมูล: A=${U.A} B=${U.B} C=${U.C} advisor=${U.advisor} · internship A ${U.iA.id} B ${U.iB.id} · วันนี้ของ backend ${today} · ของเบราว์เซอร์ ${thaiToday()} · วันทำงานล่าสุด ${d1} · ร่าง ${d2} · ส่งล่าช้า ${late}`);
   const s = await newPage(browser, base);
   const pg = s.page;
   await login(s, '6609650031');
   await openWorklog(s);
   const ids = {};

   await R.run('ST-U8-01', async () => {
      const n0 = await count();
      await fill(pg, { date: d1, start: '09:00', end: '17:30', task: 'ออกแบบหน้าจอรายงาน', skill: 'การใช้ Figma' });
      const hrs = await val(pg, '#wHours');
      const i = s.mark(); await pg.click('#wDraft'); await settle(pg);
      const req = s.since(i), post = req.find(x => x.method === 'POST');
      ids.log = JSON.parse(post.body).id;
      const r = await row(ids.log), hist = await histText(pg, d1);
      await shot(pg, 'T5-ST-U8-01-draft.png', 'ST-U8-01 ① create → save(DRAFT)', req);
      check(hrs === '8.5 ชั่วโมง' && post.status === 201 && (await count()) === n0 + 1 && r.status === 'DRAFT' && r.hours === '8.50' && hist.includes('ฉบับร่าง'), `${hrs} ${apiLog(req)} ${JSON.stringify(r)} ${hist}`);
      return `ช่องชั่วโมง "${hrs}" · ${apiLog(req.filter(x => x.method !== 'GET')).join(' · ')} · DB work_log ${n0} → ${n0 + 1} แถว (id ${r.id} ${r.status} hours ${r.hours}) · ประวัติ "${hist}"`;
   });

   await R.run('ST-U8-02', async () => {
      const n0 = await count();
      const dup = await api('POST', `/api/internships/${U.iA.id}/work-logs`, U.A, logBody(d1));
      check(dup.status === 409 && dup.data.code === 'DUPLICATE_LOG_DATE' && (await count()) === n0, `${dup.status} ${JSON.stringify(dup.data)}`);
      return `ยิงวันเดียวกัน (แทน Postman) → ${dup.status} ${dup.data.code} "${dup.data.message}" · DB work_log ${n0} → ${await count()}`;
   });

   await R.run('ST-U8-03', async () => {
      const n0 = await count();
      const a = await api('POST', `/api/internships/${U.iA.id}/work-logs`, U.A, logBody(addDays(today, 1)));
      const c = await api('POST', `/api/internships/${U.iA.id}/work-logs`, U.C, logBody(d2));
      await sql("UPDATE internships SET status = 'COMPLETED' WHERE id = ?", [U.iB.id]);
      const b = await api('POST', `/api/internships/${U.iB.id}/work-logs`, U.B, logBody(d2));
      await sql("UPDATE internships SET status = 'IN_PROGRESS' WHERE id = ?", [U.iB.id]);
      const back = await one('SELECT status FROM internships WHERE id = ?', [U.iB.id]);
      check(a.status === 400 && a.data.code === 'INVALID_WORK_LOG' && c.status === 403 && b.status === 409 && b.data.code === 'INTERNSHIP_NOT_IN_PROGRESS' && (await count()) === n0 && back === 'IN_PROGRESS',
         `${a.status} ${c.status} ${b.status} ${back}`);
      return `วันพรุ่งนี้ ${addDays(today, 1)} → ${a.status} ${a.data.code} · {C} บันทึก {iA} → ${c.status} ${c.data.code} · UPDATE internships SET status='COMPLETED' แล้ว {B} บันทึก → ${b.status} ${b.data.code} · DB work_log ${n0} → ${await count()} · คืน status ${back}`;
   });

   await R.run('ST-U8-04', async () => {
      await openRow(s, d1);
      await pg.setInputFiles('#wFile', { name: 'note.pdf', mimeType: 'application/pdf', buffer: PDF });
      const i = s.mark(); await pg.click('#wDraft'); await settle(pg);
      const req = s.since(i), att = req.find(x => x.url.endsWith('/attachments'));
      const rows = await sql('SELECT id, file_name, content_type, size, file_path FROM work_log_attachment WHERE work_log_id = ?', [ids.log]);
      const inVol = rows[0] && fileInVolume(rows[0].file_path);
      const j = s.mark(); await pg.locator('#wFiles button', { hasText: 'note.pdf' }).click(); await sleep(1000);
      const dl = s.since(j);
      await shot(pg, 'T5-ST-U8-04-attach.png', 'ST-U8-04 ① addAttachment → store → save', [...req, ...dl]);
      check(att?.status === 201 && rows.length === 1 && rows[0].content_type === 'application/pdf' && inVol && (await txt(pg, '#wFiles')).includes('note.pdf') && dl.some(x => x.url.includes('/attachments/') && x.status === 200),
         `${apiLog(req)} ${JSON.stringify(rows)} vol ${inVol} ${apiLog(dl)}`);
      return `${apiLog(req.filter(x => x.method !== 'GET')).join(' · ')} · DB work_log_attachment +1 (${rows[0].file_name} ${rows[0].content_type} ${rows[0].size} bytes) · ไฟล์ uploads/${rows[0].file_path} อยู่ใน volume · หน้าเว็บแสดง note.pdf กดเปิด → ${apiLog(dl).join(' · ')}`;
   });

   await R.run('ST-U8-05', async () => {
      const a0 = await one('SELECT COUNT(*) FROM work_log_attachment');
      await pg.setInputFiles('#wFile', { name: 'note.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });
      const i = s.mark(); await pg.click('#wDraft'); await sleep(1200);
      const att = s.since(i).filter(x => x.url.includes('/attachments')), err = await txt(pg, '#wErr');
      await shot(pg, 'T5-ST-U8-05-txt.png', 'ST-U8-05 ① alt 400 INVALID_ATTACHMENT (หน้าเว็บกันไว้)', s.since(i));
      await pg.setInputFiles('#wFile', []);
      const d = await api('POST', `/api/work-logs/${ids.log}/attachments`, U.A, undefined, { name: 'note.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });
      check(att.length === 0 && err.includes('แนบได้เฉพาะไฟล์รูป') && d.status === 400 && d.data.code === 'INVALID_ATTACHMENT' && (await one('SELECT COUNT(*) FROM work_log_attachment')) === a0, `${apiLog(att)} ${err} ${d.status}`);
      return `หน้าเว็บ "${err}" ไม่มี request · ยิงตรง → ${d.status} ${d.data.code} · DB work_log_attachment ${a0} → ${a0}`;
   });

   await R.run('ST-U8-06', async () => {
      await openWorklog(s); await openRow(s, d1);
      const n0 = await notifCount(U.advisor, 'WORK_LOG_SUBMITTED');
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i), r = await row(ids.log), n1 = await notifCount(U.advisor, 'WORK_LOG_SUBMITTED');
      const ok = await txt(pg, '#wOk'), hist = await histText(pg, d1);
      await shot(pg, 'T5-ST-U8-06-submit.png', 'ST-U8-06 ① submit → save(SUBMITTED) → notifyWorkLogSubmitted', req);
      check(req.some(x => x.url.endsWith('/submit') && x.status === 200) && r.status === 'SUBMITTED' && r.submitted_at && n1 === n0 + 1 && ok.includes('ส่งบันทึกสำเร็จ') && hist.includes('ส่งแล้ว'), `${apiLog(req)} ${JSON.stringify(r)} notif ${n0}->${n1}`);
      return `${apiLog(req.filter(x => x.method !== 'GET')).join(' · ')} · DB status ${r.status} submitted_at ${r.submitted_at} · notifications (user ${U.advisor}, WORK_LOG_SUBMITTED) ${n0} → ${n1} · "${ok.slice(0, 20)}" ประวัติ "${hist}"`;
   });

   await R.run('ST-U8-07', async () => {
      const before = await row(ids.log);
      const put = await api('PUT', `/api/work-logs/${ids.log}`, U.A, logBody(d1, '09:00', '18:00'));
      const del = await api('DELETE', `/api/work-logs/${ids.log}`, U.A);
      await openRow(s, d1);
      const locked = await pg.isDisabled('#wTask'), delBtn = await shown(pg, '#wDelete');
      await shot(pg, 'T5-ST-U8-07-locked.png', 'ST-U8-07 ① submit alt 409 INVALID_STATUS · หน้าเว็บแก้ไม่ได้', []);
      check(put.status === 409 && put.data.code === 'INVALID_STATUS' && del.status === 409 && JSON.stringify(await row(ids.log)) === JSON.stringify(before) && locked && !delBtn, `${put.status} ${del.status}`);
      return `PUT → ${put.status} ${put.data.code} · DELETE → ${del.status} ${del.data.code} · DB แถวไม่เปลี่ยน · หน้าเว็บเปิดแล้วช่องถูกปิด ไม่มีปุ่มลบ`;
   });

   await R.run('ST-U8-08', async () => {
      await pg.click('#wNew');
      await fill(pg, { date: late, start: '10:00', end: '16:00', task: 'แก้ bug หน้า login', skill: 'การ debug' });
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i).filter(x => x.method !== 'GET'), sub = req.find(x => x.url.endsWith('/submit'));
      ids.late = JSON.parse(sub.body).id;
      const lateBox = (await shown(pg, '#wLate')) ? await txt(pg, '#wLate') : '', hist = await histText(pg, late);
      await shot(pg, 'T5-ST-U8-08-late.png', 'ST-U8-08 ① submit + ป้ายส่งล่าช้า', s.since(i));
      check(JSON.parse(sub.body).lateSubmitted === true && lateBox.includes('ส่งล่าช้า') && hist.includes('ส่งล่าช้า'), `${apiLog(req)} ${lateBox} ${hist}`);
      return `${late} → ${apiLog(req).join(' · ')} lateSubmitted true · กล่อง "${lateBox.slice(0, 50)}" · ประวัติ "${hist}"`;
   });

   const adv = await newPage(browser, base);
   await login(adv, 'advisor01');
   await R.run('ST-U8-09', async () => {
      const i = adv.mark();
      await adv.page.goto(base + '/notifications.html'); await adv.page.waitForSelector('#notifList .notif', { timeout: 15000 });
      const first = await txt(adv.page, '#notifList .notif');
      await shot(adv.page, 'T5-ST-U8-09-advisor-notif.png', 'ST-U8-09 ② getPending · อาจารย์เห็นแจ้งเตือน', adv.since(i));
      const p = await api('GET', '/api/work-logs/pending-review', U.advisor);
      const sqlCount = await one("SELECT COUNT(*) FROM work_log w JOIN students s ON w.student_id = s.user_id WHERE s.advisor_id = ? AND w.status = 'SUBMITTED'", [U.advisor]);
      const items = p.data.map(x => `${x.id} ${x.logDate}${x.lateSubmitted ? ' ส่งล่าช้า' : ''}`);
      check(first.includes('มีบันทึกการทำงานรอตรวจ') && p.status === 200 && p.data.length === 2 && sqlCount === 2 && p.data.every(x => x.studentName === 'IN_PROGRESS_31'), `${first} ${p.status} ${items}`);
      return `หน้าแจ้งเตือนอาจารย์บนสุด "${first.slice(0, 80)}" · GET /api/work-logs/pending-review → ${p.status} ${p.data.length} รายการ (${items.join(', ')}) ของ IN_PROGRESS_31 = SQL COUNT ${sqlCount}`;
   });

   await R.run('ST-U8-10', async () => {
      const n0 = await notifCount(U.A, 'WORK_LOG_REVIEWED');
      const a = await api('POST', `/api/work-logs/${ids.log}/review`, U.advisor, { decision: 'REVISE' });
      const st1 = (await row(ids.log)).status;
      const b = await api('POST', `/api/work-logs/${ids.log}/review`, U.advisor, { decision: 'REVISE', comment: 'เพิ่มรายละเอียดงาน' });
      const r = await row(ids.log), n1 = await notifCount(U.A, 'WORK_LOG_REVIEWED');
      const title = await one("SELECT title FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 1", [U.A]);
      check(a.status === 400 && a.data.code === 'COMMENT_REQUIRED' && st1 === 'SUBMITTED' && b.status === 200 && r.status === 'NEED_REVISION' && r.reviewed_by === U.advisor && n1 === n0 + 1 && title.includes('ส่งกลับให้แก้ไข'), `${a.status} ${b.status} ${JSON.stringify(r)} ${n0}->${n1}`);
      return `REVISE ไม่มีความเห็น → ${a.status} ${a.data.code} (DB ยัง ${st1}) · มีความเห็น → ${b.status} ${b.data.status} · DB reviewed_by ${r.reviewed_by} reviewed_at ${r.reviewed_at} review_comment "${r.review_comment}" · notifications IN_PROGRESS_31 ${n0} → ${n1} "${title}"`;
   });

   await R.run('ST-U8-11', async () => {
      await openWorklog(s);
      const hist0 = await histText(pg, d1);
      await openRow(s, d1);
      const review = await txt(pg, '#wReview');
      await shot(pg, 'T5-ST-U8-11-review-comment.png', 'ST-U8-11 นักศึกษาเห็นความเห็นอาจารย์', []);
      const n0 = await notifCount(U.advisor, 'WORK_LOG_SUBMITTED');
      await pg.fill('#wTask', 'ออกแบบหน้าจอรายงาน 3 หน้า (สรุป รายวัน ส่งออก) และนำเสนอทีม');
      const i = s.mark(); await pg.click('#wSend'); await settle(pg);
      const req = s.since(i).filter(x => x.method !== 'GET'), r = await row(ids.log), n1 = await notifCount(U.advisor, 'WORK_LOG_SUBMITTED');
      await shot(pg, 'T5-ST-U8-11-resubmit.png', 'ST-U8-11 ① แก้แล้วส่งใหม่', s.since(i));
      check(hist0.includes('ขอให้แก้ไข') && review.includes('เพิ่มรายละเอียดงาน') && req[0]?.method === 'PUT' && req[0].status === 200 && req[1]?.status === 200 && r.status === 'SUBMITTED' && n1 === n0 + 1, `${hist0} ${review} ${apiLog(req)} ${r.status} ${n0}->${n1}`);
      return `ประวัติ "${hist0}" · กล่อง "${review}" · ${apiLog(req).join(' · ')} · DB status ${r.status} · notifications อาจารย์ ${n0} → ${n1}`;
   });

   await R.run('ST-U8-12', async () => {
      const draft = await api('POST', `/api/internships/${U.iA.id}/work-logs`, U.A, logBody(d2, '09:00', '12:00', 'ร่างงาน'));
      ids.draft = draft.data.id;
      const a = await api('POST', `/api/work-logs/${ids.log}/review`, U.A, { decision: 'APPROVE' });
      const b = await api('POST', `/api/work-logs/${ids.draft}/review`, U.advisor, { decision: 'APPROVE' });
      const st = [(await row(ids.log)).status, (await row(ids.draft)).status];
      check(a.status === 403 && b.status === 409 && b.data.code === 'INVALID_STATUS' && st[0] === 'SUBMITTED' && st[1] === 'DRAFT', `${a.status} ${b.status} ${st}`);
      return `นักศึกษา review → ${a.status} ${a.data.code} · อาจารย์ review บันทึก DRAFT (${d2}) → ${b.status} ${b.data.code} · DB ไม่เปลี่ยน (${st.join(' / ')})`;
   });

   await R.run('ST-U8-13', async () => {
      const pend = (await api('GET', '/api/work-logs/pending-review', U.advisor)).data.map(x => x.id);
      const n0 = await notifCount(U.A, 'WORK_LOG_REVIEWED');
      const b = await api('POST', '/api/work-logs/review-bulk', U.advisor, { workLogIds: [...pend, 9999], comment: 'เรียบร้อย' });
      const st = await sql("SELECT id, status FROM work_log WHERE id IN (?)", [pend]);
      const n1 = await notifCount(U.A, 'WORK_LOG_REVIEWED');
      const i = s.mark();
      await pg.goto(base + '/notifications.html'); await pg.waitForSelector('#notifList .notif', { timeout: 15000 });
      const notif = await txt(pg, '#notifList .notif');
      await shot(pg, 'T5-ST-U8-13-approved-notif.png', 'ST-U8-13 ② review-bulk → นักศึกษาได้แจ้งเตือน', s.since(i));
      await openWorklog(s);
      const hist = [await histText(pg, d1), await histText(pg, late)];
      await shot(pg, 'T5-ST-U8-13-history.png', 'ST-U8-13 ประวัติ "อนุมัติแล้ว"', []);
      check(b.status === 200 && b.data.approved.length === pend.length && b.data.skipped[0].workLogId === 9999 && st.every(x => x.status === 'APPROVED') && n1 === n0 + pend.length && notif.includes('ได้รับการอนุมัติ') && hist.every(h => h.includes('อนุมัติแล้ว')),
         `${JSON.stringify(b.data)} ${JSON.stringify(st)} ${n0}->${n1} ${notif} ${hist}`);
      return `review-bulk [${pend.join(', ')}, 9999] → ${b.status} approved ${JSON.stringify(b.data.approved)} skipped 9999 "${b.data.skipped[0].reason}" · DB ${st.map(x => x.id + ' ' + x.status).join(', ')} · notifications IN_PROGRESS_31 ${n0} → ${n1} · หน้าแจ้งเตือน "${notif.slice(0, 60)}" · ประวัติ ${hist.join(' / ')}`;
   });

   await R.run('ST-U8-14', async () => {
      const ex = await api('GET', `/api/internships/${U.iA.id}/work-logs/export`, U.advisor);
      await writeFile(join(OUT, 'T5-ST-U8-14-export.csv'), ex.buf);
      const lines = ex.buf.toString('utf8').replace(/^﻿/, '').trim().split(/\r?\n/);
      const nd = await one("SELECT COUNT(*) FROM work_log WHERE internship_id = ? AND status <> 'DRAFT'", [U.iA.id]);
      const st = await api('GET', `/api/internships/${U.iA.id}/work-logs/export`, U.A);
      const bom = ex.buf[0] === 0xEF && ex.buf[1] === 0xBB && ex.buf[2] === 0xBF;
      check(ex.status === 200 && ex.type.startsWith('text/csv') && bom && lines.length === nd + 1 && lines[0].startsWith('วันที่') && st.status === 403, `${ex.status} ${ex.type} ${bom} ${lines.length} ${nd} ${st.status}`);
      return `อาจารย์ → ${ex.status} ${ex.type} ขึ้นต้น UTF-8 BOM (Excel อ่านภาษาไทยได้) · หัวตาราง "${lines[0]}" · ${lines.length - 1} แถว = บันทึกที่ไม่ใช่ DRAFT ${nd} (ไฟล์ evidence/T5-ST-U8-14-export.csv) · นักศึกษา → ${st.status} ${st.data.code}`;
   });

   await R.run('ST-U8-15', async () => {
      const i = s.mark(); await openWorklog(s); const req = s.since(i);
      const hours = await txt(pg, '#sumHours'), days = await txt(pg, '#sumDays'), miss = await txt(pg, '#sumMissing'), list = await txt(pg, '#sumMissingList');
      await shot(pg, 'T5-ST-U8-15-summary.png', 'ST-U8-15 ③ summary → missingDates', req);
      const total = await one("SELECT COALESCE(SUM(hours), 0) FROM work_log WHERE internship_id = ? AND status <> 'DRAFT'", [U.iA.id]);
      const logged = (await sql('SELECT log_date FROM work_log WHERE internship_id = ?', [U.iA.id])).map(r => r.log_date);
      const start = U.iA.startDate; let work = 0, missing = 0;
      for (let d = start; d <= today; d = addDays(d, 1)) { const g = new Date(d + 'T00:00:00Z').getUTCDay(); if (g && g !== 6) { work++; if (!logged.includes(d)) missing++; } }
      check(req.some(x => x.url.includes('/summary') && x.status === 200) && hours === Number(total) + ' ชม.' && days === `${logged.length} / ${work}` && miss === missing + ' วัน', `${hours} ${days} ${miss} vs SQL ${total} ${logged.length}/${work} ${missing}`);
      return `${apiLog(req.filter(x => x.url.includes('summary'))).join('')} · หน้าเว็บ ชั่วโมงสะสม ${hours} = SQL SUM(hours) ที่ไม่ใช่ DRAFT ${total} · วันที่บันทึก / วันทำงาน ${days} = DB ${logged.length} วัน / วัน จ.–ศ. ตั้งแต่ ${start} ${work} วัน · ขาด ${miss} = ${missing} · "${list.slice(0, 70)}"`;
   });

   await R.run('ST-U8-16', async () => {
      const c = await api('GET', `/api/internships/${U.iA.id}/work-logs/summary`, U.C);
      const a = await api('GET', `/api/internships/${U.iA.id}/work-logs`, U.advisor);
      const st = [...new Set(a.data.map(x => x.status))];
      check(c.status === 403 && a.status === 200 && !st.includes('DRAFT') && (await one("SELECT COUNT(*) FROM work_log WHERE status = 'DRAFT'")) > 0, `${c.status} ${a.status} ${st}`);
      return `{C} GET summary → ${c.status} ${c.data.code} · อาจารย์ GET work-logs → ${a.status} สถานะ ${st.join(', ')} (ไม่มี DRAFT แม้ DB มี DRAFT ${d2})`;
   });

   const cs = await newPage(browser, base);
   await login(cs, '6609650033');
   await R.run('ST-U8-17', async () => {
      const i = cs.mark(); await openWorklog(cs); const req = cs.since(i);
      const err = await txt(cs.page, '#wErr');
      await shot(cs.page, 'T5-ST-U8-17-no-internship.png', 'ST-U8-17 ① ไม่มีการฝึกงาน', req);
      check(req.some(x => x.url === '/api/work-logs/my-internship' && x.status === 404) && err.includes('ยังไม่มีข้อมูลการฝึกงาน'), `${apiLog(req)} ${err}`);
      return `${apiLog(req).join(' · ')} · "${err}"`;
   });

   await writeFile(join(OUT, 'T5-session-A.json'), JSON.stringify(await pg.evaluate(() => Object.fromEntries(Object.entries(sessionStorage)))), 'utf8');
   R.say('\nwork_log หลังจบ: ' + JSON.stringify(await sql('SELECT id, log_date, hours, status, submitted_at, reviewed_by, review_comment FROM work_log ORDER BY id')));
   R.say('notifications (WORK_LOG_*): ' + JSON.stringify(await sql("SELECT id, user_id, type, title FROM notifications WHERE type LIKE 'WORK_LOG%' ORDER BY id")));
   R.say('work_log_attachment: ' + JSON.stringify(await sql('SELECT id, work_log_id, file_name, content_type, size, file_path FROM work_log_attachment ORDER BY id')));
}

async function serverDown(ph) {
   const session = JSON.parse(await readFile(join(OUT, 'T5-session-A.json'), 'utf8'));
   const p = await newPage(browser, base);
   await p.page.goto(base + '/login.html');
   await p.page.evaluate(x => { for (const [k, v] of Object.entries(x)) sessionStorage.setItem(k, v); }, session);
   const i = p.mark();
   await p.page.goto(base + '/worklog.html');
   await sleep(4000);
   const req = p.since(i), err = await txt(p.page, '#wErr').catch(() => ''), hist = await txt(p.page, '#wHist').catch(() => '');
   if (ph === 'down') {
      await R.run('ST-U8-18', async () => {
         await shot(p.page, 'T5-ST-U8-18-server-down.png', 'ST-U8-18 ระบบล่ม (docker compose stop app)', req);
         check(req.some(x => String(x.status).startsWith('FAILED')) && err.includes('ไม่สามารถเชื่อมต่อระบบได้'), `${apiLog(req)} ${err}`);
         return `docker compose stop app แล้วเปิดหน้าบันทึก: ${apiLog(req).join(' · ')} · "${err}" ไม่ค้าง`;
      });
   } else {
      await R.run('ST-U8-18 (เปิด app คืน)', async () => {
         await shot(p.page, 'T5-ST-U8-18-server-back.png', 'ST-U8-18 เปิด app คืนแล้วใช้งานต่อได้', req);
         check(req.length > 0 && req.every(x => x.status === 200) && hist.includes('อนุมัติแล้ว'), `${apiLog(req)} ${err}`);
         return `docker compose start app แล้วโหลดหน้าใหม่: ${apiLog(req).join(' · ')} · ประวัติกลับมาแสดง ไม่มีข้อความ error`;
      });
   }
}
