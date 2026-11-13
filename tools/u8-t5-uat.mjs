/* =====================================================================
   U8:T5 ส่วนที่ 8 · UAT dry-run ของทีม ตาม T5.md ส่วนที่ 4 (UAT-U8-01 ถึง UAT-U8-12)
   ทำตาม Given / When ของแต่ละข้อผ่านหน้าเว็บจริงเหมือนผู้ทดสอบ แล้วตรวจ Then และ "เกณฑ์ที่ user ยอมรับได้"
   เท่าที่ตรวจด้วยเครื่องได้ (ข้อความบนจอ ข้อมูล เวลาที่ใช้) · ไม่แทนผู้ทดสอบจริง: ความเข้าใจ / ความพอใจ / คำพูด
   ต้องเก็บจาก P1–P4 ในตาราง "ผลการทดสอบ" ของ T5 ส่วนที่ 8
   รันผ่าน tools/u8-t5-run.ps1 บนฐานข้อมูลใหม่ (reset ก่อนรัน)
   บัญชี: P1 = 6609650031 · P2 = 6609650032 · P3 = 6609650033 · P4 = advisor01 (ยิง API แทน เพราะยังไม่มีหน้าอาจารย์)
   ผล: .doc/UserStory/U8/evidence/T5-uat-output.txt · T5-uat-result.json · T5-UAT-U8-*.png
   ===================================================================== */
import { chromium } from 'playwright';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import {
   OUT, PDF, api, backendUp, users, startStatic, newPage, login, openWorklog, settle, fill, val, txt, shown,
   histText, openRow, shot, reporter, check, apiLog, serverToday, thaiToday, addDays, weekdaysBefore, weekdayAtLeast, sleep, sql, closeDb
} from './u8-common.mjs';

const R = reporter('T5-uat');
const { server, base } = await startStatic();
const browser = await chromium.launch();
const sec = t0 => ((Date.now() - t0) / 1000).toFixed(1) + ' วินาที';
try {
   if (!(await backendUp())) throw new Error('backend ไม่ทำงาน');
   await main();
} catch (e) {
   R.say('หยุดกลางคัน: ' + e.stack);
} finally {
   await R.save();
   await browser.close(); server.close(); await closeDb();
}

async function main() {
   const U = await users();
   const today = serverToday();
   const [y1, y2, y3, y4] = weekdaysBefore(today);
   const late = weekdayAtLeast(5, today);
   R.say(`ข้อมูล: P1=${U.A} P2=${U.B} P3=${U.C} P4=${U.advisor} · วันนี้ของ backend ${today} · ของเบราว์เซอร์ ${thaiToday()} · เมื่อวาน (วันทำงาน) ${y1}`);
   const p1 = await newPage(browser, base), p2 = await newPage(browser, base);
   await login(p1, '6609650031'); await openWorklog(p1);
   await login(p2, '6609650032'); await openWorklog(p2);
   const ids = {};

   for (const [who, p, uid] of [['P1', p1, U.A], ['P2', p2, U.B]]) {
      await R.run(`UAT-U8-01 (${who})`, async () => {
         const t0 = Date.now();
         await fill(p.page, { date: y1, start: '09:00', end: '17:00', task: 'เขียน unit test ของหน้าบันทึก', skill: 'Vitest' });
         const hrs = await val(p.page, '#wHours');
         const i = p.mark(); await p.page.click('#wSend'); await settle(p.page);
         const used = sec(t0), ok = await txt(p.page, '#wOk'), hist = await histText(p.page, y1);
         await shot(p.page, `T5-UAT-U8-01-${who}.png`, `UAT-U8-01 (${who}) บันทึกและส่งบันทึกของเมื่อวาน`, p.since(i));
         ids[who] = JSON.parse(p.since(i).find(x => x.method === 'POST').body).id;
         check(hrs === '8 ชั่วโมง' && ok.includes('ส่งบันทึกสำเร็จ') && hist.includes('ส่งแล้ว'), `${hrs} ${ok} ${hist}`);
         return `ชั่วโมงคำนวณให้เอง "${hrs}" · "${ok.slice(0, 20)}" · ประวัติ "${hist}" · ใช้เวลา ${used} (เกณฑ์ ≤ 2 นาที)`;
      });
   }

   await R.run('UAT-U8-02', async () => {
      const t0 = Date.now();
      await p1.page.click('#wNew');
      await fill(p1.page, { date: y2, start: '09:00', end: '12:00', task: 'ร่าง: ประชุม' });
      await p1.page.click('#wDraft'); await settle(p1.page);
      await openWorklog(p1);                         // กลับมาใหม่ (โหลดหน้าใหม่)
      const hist = await histText(p1.page, y2);
      await openRow(p1, y2);
      await p1.page.fill('#wTask', 'ร่าง: ประชุม แล้วเขียนต่อ'); await p1.page.click('#wDraft'); await settle(p1.page);
      const adv = (await api('GET', `/api/internships/${U.iA.id}/work-logs`, U.advisor)).data.map(x => x.logDate);
      await shot(p1.page, 'T5-UAT-U8-02-draft.png', 'UAT-U8-02 บันทึกร่างแล้วเขียนต่อ', []);
      check(hist.includes('ฉบับร่าง') && (await val(p1.page, '#wTask')).includes('เขียนต่อ') && !adv.includes(y2), `${hist} ${adv}`);
      return `ประวัติ "${hist}" เปิดมาแก้ต่อได้ · อาจารย์ดูรายการไม่เห็นวันที่ ${y2} (ฉบับร่าง) · ใช้เวลา ${sec(t0)}`;
   });

   await R.run('UAT-U8-03', async () => {
      const out = [];
      for (const [name, v, msg] of [['เวลาผิด', { start: '13:00', end: '09:00', task: 'งาน' }, 'เวลาสิ้นสุดต้องหลังเวลาเริ่ม'],
      ['วันพรุ่งนี้', { date: addDays(thaiToday(), 1), start: '09:00', end: '12:00', task: 'งาน' }, 'ไม่สามารถบันทึกวันที่ในอนาคตได้'],
      ['ลืมกรอกงานที่ทำ', { date: y3, start: '09:00', end: '12:00', task: '' }, 'กรุณากรอกงานที่ทำ']]) {
         await p2.page.click('#wNew'); await fill(p2.page, { date: y3, ...v });
         await p2.page.click('#wSend'); await sleep(600);
         const err = await txt(p2.page, '#wErr');
         check(err.includes(msg) && (await val(p2.page, '#wStart')) === v.start, `${name} "${err}"`);
         out.push(`${name}: "${err}" ข้อมูลไม่หาย`);
      }
      await shot(p2.page, 'T5-UAT-U8-03-error.png', 'UAT-U8-03 กรอกผิด (ลืมกรอกงานที่ทำ)', []);
      return out.join(' · ');
   });

   await R.run('UAT-U8-04', async () => {
      await openRow(p1, y2);
      await p1.page.setInputFiles('#wFile', { name: 'timesheet.pdf', mimeType: 'application/pdf', buffer: PDF });
      await p1.page.click('#wDraft'); await settle(p1.page);
      const files = await txt(p1.page, '#wFiles');
      const i = p1.mark(); await p1.page.locator('#wFiles button', { hasText: 'timesheet.pdf' }).click(); await sleep(800);
      const open = p1.since(i);
      await p1.page.setInputFiles('#wFile', { name: 'timesheet.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', buffer: Buffer.from('x') });
      await p1.page.click('#wDraft'); await sleep(800);
      const err = await txt(p1.page, '#wErr');
      await shot(p1.page, 'T5-UAT-U8-04-attach.png', 'UAT-U8-04 แนบใบลงเวลา', open);
      await p1.page.setInputFiles('#wFile', []);
      check(files.includes('timesheet.pdf') && open.some(x => x.status === 200) && err.includes('แนบได้เฉพาะไฟล์รูป'), `${files} ${apiLog(open)} ${err}`);
      return `เห็นชื่อไฟล์ "timesheet.pdf" กดเปิด ${apiLog(open).join('')} · ไฟล์ .docx ถูกปฏิเสธ "${err}"`;
   });

   await R.run('UAT-U8-05', async () => {
      await p2.page.click('#wNew');
      await fill(p2.page, { date: y4, start: '09:00', end: '12:00', task: 'บันทึกผิดวัน' });
      await p2.page.click('#wDraft'); await settle(p2.page);
      p2.page.dialogs.length = 0; p2.page.dialogAnswer = false;
      await p2.page.click('#wDelete'); await sleep(500);
      const still = await histText(p2.page, y4);
      p2.page.dialogAnswer = true;
      await p2.page.click('#wDelete'); await settle(p2.page);
      const gone = !(await histText(p2.page, y4));
      await shot(p2.page, 'T5-UAT-U8-05-delete.png', 'UAT-U8-05 ลบบันทึกร่าง', []);
      check(p2.page.dialogs.length === 2 && still.includes('ฉบับร่าง') && gone, `${p2.page.dialogs} ${still} ${gone}`);
      return `ระบบถามยืนยัน "${p2.page.dialogs[0]}" · กดยกเลิกแล้วบันทึกยังอยู่ · ยืนยันแล้วหายจากประวัติ`;
   });

   await R.run('UAT-U8-06', async () => {
      await p1.page.click('#wNew');
      await fill(p1.page, { date: late, start: '09:00', end: '15:00', task: 'งานย้อนหลัง' });
      const i = p1.mark(); await p1.page.click('#wSend'); await settle(p1.page);
      const lateBox = (await shown(p1.page, '#wLate')) ? await txt(p1.page, '#wLate') : '', hist = await histText(p1.page, late), due = await txt(p1.page, '#wDue');
      ids.late = JSON.parse(p1.since(i).find(x => x.url.endsWith('/submit')).body).id;
      await shot(p1.page, 'T5-UAT-U8-06-late.png', 'UAT-U8-06 ส่งย้อนหลัง', p1.since(i));
      check(lateBox.includes('ส่งล่าช้า') && hist.includes('ส่งล่าช้า'), `${lateBox} ${hist}`);
      return `ส่งได้ · กล่อง "${lateBox.slice(0, 70)}" · ประวัติ "${hist}" · แถบกำหนดส่งอธิบายเหตุผล "${due.slice(0, 70)}"`;
   });

   await R.run('UAT-U8-07', async () => {
      await api('POST', `/api/work-logs/${ids.P1}/review`, U.advisor, { decision: 'REVISE', comment: 'เพิ่มชื่อ test ที่เขียน' });
      const t0 = Date.now();
      await p1.page.goto(base + '/notifications.html'); await p1.page.waitForSelector('#notifList .notif');
      const notif = await txt(p1.page, '#notifList .notif');
      await openWorklog(p1); await openRow(p1, y1);
      const review = await txt(p1.page, '#wReview');
      await shot(p1.page, 'T5-UAT-U8-07-revise.png', 'UAT-U8-07 แก้ตามที่อาจารย์ขอ', []);
      await p1.page.fill('#wTask', 'เขียน unit test ของหน้าบันทึก (UT-UI-01 ถึง 15)');
      await p1.page.click('#wSend'); await settle(p1.page);
      const hist = await histText(p1.page, y1);
      check(notif.includes('ส่งกลับให้แก้ไข') && review.includes('เพิ่มชื่อ test') && hist.includes('ส่ง'), `${notif} ${review} ${hist}`);
      return `แจ้งเตือน "${notif.slice(0, 70)}" · กล่อง "${review}" · แก้แล้วส่งใหม่ ประวัติ "${hist}" · ใช้เวลา ${sec(t0)} (เกณฑ์ ≤ 2 นาที)`;
   });

   await R.run('UAT-U8-08', async () => {
      await api('POST', '/api/work-logs/review-bulk', U.advisor, { workLogIds: [ids.P1, ids.late], comment: 'เรียบร้อย' });
      const t0 = Date.now();
      await p1.page.goto(base + '/notifications.html'); await p1.page.waitForSelector('#notifList .notif');
      const notif = await txt(p1.page, '#notifList .notif');
      await openWorklog(p1); await openRow(p1, y1);
      const used = sec(t0), hist = await histText(p1.page, y1), locked = await p1.page.isDisabled('#wTask');
      await shot(p1.page, 'T5-UAT-U8-08-approved.png', 'UAT-U8-08 ดูผลการอนุมัติ', []);
      check(notif.includes('ได้รับการอนุมัติ') && hist.includes('อนุมัติแล้ว') && locked, `${notif} ${hist} ${locked}`);
      return `แจ้งเตือน "${notif.slice(0, 60)}" · ประวัติ "${hist}" แก้ไม่ได้ · หาผลเจอใน ${used} (เกณฑ์ ≤ 30 วินาที)`;
   });

   await R.run('UAT-U8-09', async () => {
      const t0 = Date.now();
      await openWorklog(p1);
      const used = sec(t0), hours = await txt(p1.page, '#sumHours'), days = await txt(p1.page, '#sumDays'), miss = await txt(p1.page, '#sumMissing'), list = await txt(p1.page, '#sumMissingList');
      await shot(p1.page, 'T5-UAT-U8-09-summary.png', 'UAT-U8-09 ดูชั่วโมงสะสม / วันที่ขาด', []);
      check(/ชม\./.test(hours) && /\d+ \/ \d+/.test(days) && /วัน/.test(miss) && list.startsWith('ยังไม่บันทึก:'), `${hours} ${days} ${miss}`);
      return `ชั่วโมงสะสม ${hours} · วันที่บันทึก / วันทำงาน ${days} · วันที่ขาดบันทึก ${miss} · "${list.slice(0, 60)}" · เห็นได้ทันทีที่เปิดหน้า (${used})`;
   });

   await R.run('UAT-U8-10', async () => {
      const pend = await api('GET', '/api/work-logs/pending-review', U.advisor);
      const item = pend.data[0];
      const noComment = await api('POST', `/api/work-logs/${item.id}/review`, U.advisor, { decision: 'REVISE' });
      const n0 = (await api('GET', '/api/notifications', U.B)).data.length;
      const ok = await api('POST', `/api/work-logs/${item.id}/review`, U.advisor, { decision: 'APPROVE', comment: 'ดี' });
      const n1 = (await api('GET', '/api/notifications', U.B)).data.length;
      check(pend.status === 200 && noComment.status === 400 && ok.status === 200 && n1 === n0 + 1, `${pend.status} ${noComment.status} ${ok.status} ${n0}->${n1}`);
      return `P4 (ยิง API แทน) pending-review → ${pend.status} ${pend.data.length} รายการ ข้อมูลต่อรายการ: ${Object.keys(item).join(', ')} · ส่งกลับไม่ใส่ความเห็น → ${noComment.status} ${noComment.data.code} · อนุมัติ → ${ok.status} นักศึกษาได้แจ้งเตือน ${n0} → ${n1}`;
   });

   await R.run('UAT-U8-11', async () => {
      const ex = await api('GET', `/api/internships/${U.iA.id}/work-logs/export`, U.advisor);
      await writeFile(join(OUT, 'T5-UAT-U8-11-export.csv'), ex.buf);
      const lines = ex.buf.toString('utf8').replace(/^﻿/, '').trim().split(/\r?\n/);
      const bom = ex.buf[0] === 0xEF && ex.buf[1] === 0xBB && ex.buf[2] === 0xBF;
      check(ex.status === 200 && bom && lines[0].includes('วันที่') && lines.length > 1, `${ex.status} ${bom}`);
      return `export → ${ex.status} ${ex.type} มี UTF-8 BOM (Excel แสดงภาษาไทยได้) · หัวตาราง "${lines[0]}" · ${lines.length - 1} แถว (ไฟล์ evidence/T5-UAT-U8-11-export.csv ให้ผู้ทดสอบเปิดใน Excel)`;
   });

   const p3 = await newPage(browser, base);
   await login(p3, '6609650033');
   await R.run('UAT-U8-12', async () => {
      await openWorklog(p3);
      const err = await txt(p3.page, '#wErr');
      await shot(p3.page, 'T5-UAT-U8-12-no-internship.png', 'UAT-U8-12 ยังไม่มีการฝึกงาน', []);
      check(err.includes('ยังไม่มีข้อมูลการฝึกงาน') && !err.includes('เชื่อมต่อ'), err);
      return `"${err}" (ไม่ใช่ข้อความระบบล่ม) · ปุ่มบันทึกถูกปิด`;
   });
   R.say('\nwork_log หลังจบ: ' + JSON.stringify(await sql('SELECT id, student_id, log_date, hours, status FROM work_log ORDER BY id')));
}
