/* =====================================================================
   ตัวช่วยร่วมของสคริปต์ U3 (u3-t17-run.mjs, u3-t19-run.mjs)
   - static server สำหรับ frontend · login ผ่านหน้าเว็บ · เรียก API ตรง · แคปจอพร้อมแถบหลักฐาน
   - หยุด / เปิด backend ด้วยคำสั่งที่ตั้งได้ (ค่าเริ่มต้นใช้ docker compose)
   ===================================================================== */
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { execSync } from 'node:child_process';
import { join, extname, resolve } from 'node:path';

export const ROOT = resolve('.');
export const FRONT = join(ROOT, 'frontend');
export const OUT = join(ROOT, '.doc', 'UserStory', 'U3', 'evidence');
export const API = process.env.U3_API || 'http://localhost:8080';
export const DEMO_PASS = process.env.U3_DEMO_PASS || '1234';
export const STOP_CMD = process.env.U3_STOP_CMD || 'docker compose stop app';
export const START_CMD = process.env.U3_START_CMD || 'docker compose start app';

/* บัญชีทดลองจาก DemoDataSeeder (ฐานข้อมูลใหม่) */
export const ACC = {
   staff: { username: 'staff01', id: 1 },
   // ใช้ได้แค่ "อ่าน" เท่านั้น: A, B เป็นข้อมูลร่วมของ U4/U5 (U4 คาดว่า A, B ยื่นแล้ว → 409) ห้ามส่งกลับ/ปฏิเสธคำร้องของ A, B
   s1: { username: '6609650001', id: 3 },   // A arizona · seed: มีคำร้อง SUBMITTED
   s2: { username: '6609650002', id: 4 },   // B berlin  · seed: มีคำร้อง SUBMITTED (ใช้ทดสอบยื่นซ้ำ ไม่มีการเปลี่ยนข้อมูล)
   // นักศึกษาตัวอย่างของ U3 โดยเฉพาะ (ไม่ใช้ C 6609650003 / D 6609650004 ซึ่งเป็นข้อมูลของ U4)
   s3: { username: '6609650006', id: 8 },   // F frankfurt · seed: ผ่าน U2 ยังไม่มีคำร้อง → ยื่นสำเร็จ / ถูกส่งกลับแก้
   s4: { username: '6609650007', id: 9 },   // G geneva    · seed: ผ่าน U2 ยังไม่มีคำร้อง → ยื่นหลังหมดเขต
   s5: { username: '6609650005', id: 7 },   // E essen     · seed: ยังไม่เคยตรวจ U2 → ยื่นได้ 403 NOT_ELIGIBLE
   s6: { username: '6609650008', id: 10 }   // H hamburg   · seed: ผ่าน U2 ยังไม่มีคำร้อง → ใช้ใน UAT (ถูกปฏิเสธแล้วยื่นใหม่)
};

export const ANSWERS = { s_name: 'F frankfurt', s_tel: '0812345678', s_mail: 'frankfurt@dome.tu.ac.th', s_addr: '99 ม.ธ. รังสิต' };
export const PDF = Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n% U3 transcript (test)\n%%EOF\n');

export const sleep = ms => new Promise(r => setTimeout(r, ms));
export const sh = cmd => execSync(cmd, { cwd: ROOT, stdio: 'pipe', shell: true }).toString();

/* ---------- static server ---------- */
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
export function startStatic(port) {
   const base = `http://localhost:${port}`;
   const server = createServer(async (req, res) => {
      const path = decodeURIComponent(new URL(req.url, base).pathname);
      const file = resolve(FRONT, '.' + (path === '/' ? '/login.html' : path));
      if (!file.startsWith(FRONT)) { res.writeHead(403).end(); return; }
      try {
         res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream' }).end(await readFile(file));
      } catch { res.writeHead(404).end('not found'); }
   });
   return new Promise(r => server.listen(port, () => r({ server, base })));
}

/* ---------- API ตรง (ใช้เตรียมข้อมูลและตรวจผลในฐานข้อมูลผ่าน API) ---------- */
export async function api(method, path, userId, body) {
   const headers = { 'X-User-Id': String(userId) };
   if (body !== undefined) headers['Content-Type'] = 'application/json';
   const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
   let data = null;
   const text = await res.text();
   try { data = JSON.parse(text); } catch { data = text; }
   return { status: res.status, data };
}

export async function backendUp(timeoutMs = 120000) {
   const end = Date.now() + timeoutMs;
   while (Date.now() < end) {
      try {
         const r = await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
         if (r.status === 400) return true;
      } catch { /* ยังไม่ขึ้น */ }
      await sleep(1500);
   }
   return false;
}

/* เจ้าหน้าที่สร้างแบบฟอร์มใหม่ (5 ช่องเหมือน seed) ตั้งวันหมดเขต แล้วเปิดใช้งาน */
export async function publishForm(name, deadline) {
   const fields = [
      { label: 'ชื่อ-นามสกุล', fieldType: 'TEXT', required: true },
      { label: 'เบอร์โทรศัพท์', fieldType: 'PHONE', required: true },
      { label: 'อีเมล', fieldType: 'EMAIL', required: true },
      { label: 'ที่อยู่ปัจจุบัน', fieldType: 'TEXTAREA', required: true },
      { label: 'Transcript', fieldType: 'FILE', required: true }
   ];
   const c = await api('POST', '/api/forms', ACC.staff.id, { name, description: 'U3 test', deadline, fields });
   if (c.status !== 201) throw new Error('สร้างแบบฟอร์มไม่ได้ ' + c.status + ' ' + JSON.stringify(c.data));
   const p = await api('POST', '/api/forms/' + c.data.id + '/publish', ACC.staff.id);
   if (p.status !== 200) throw new Error('เปิดใช้แบบฟอร์มไม่ได้ ' + p.status + ' ' + JSON.stringify(p.data));
   return c.data.id;
}

/* เวลา server ปัจจุบัน (LocalDateTime) จาก GET /api/forms/published */
/* ยื่นคำร้องผ่าน API ตรง (สร้าง → กรอก → แนบ PDF → ส่ง) คืนเลขที่คำร้อง ใช้เตรียมข้อมูลก่อนทดสอบ */
export async function submitViaApi(acc, values = ANSWERS) {
   const created = await api('POST', '/api/requests', acc.id, {});
   const id = created.data.id;
   await api('PUT', `/api/requests/${id}/form`, acc.id,
      { answers: { field_1: values.s_name, field_2: values.s_tel, field_3: values.s_mail, field_4: values.s_addr } });
   const fd = new FormData();
   fd.append('file', new Blob([PDF], { type: 'application/pdf' }), 'transcript.pdf');
   await fetch(`${API}/api/requests/${id}/documents`, { method: 'POST', headers: { 'X-User-Id': String(acc.id) }, body: fd });
   const sent = await api('POST', `/api/requests/${id}/submit`, acc.id);
   if (sent.status !== 200) throw new Error(`เตรียมคำร้องของ ${acc.username} ไม่สำเร็จ: ${sent.status} ${JSON.stringify(sent.data)}`);
   return id;
}

export async function serverTime(userId) {
   const r = await api('GET', '/api/forms/published', userId);
   return r.data.serverTime;
}

export function addSeconds(localIso, s) {
   const d = new Date(localIso.slice(0, 19) + 'Z');
   d.setUTCSeconds(d.getUTCSeconds() + s);
   return d.toISOString().slice(0, 19);
}

/* ---------- browser ---------- */
export async function newPage(browser, base) {
   const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
   const page = await ctx.newPage();
   const log = [];
   page.on('requestfinished', async rq => {
      if (!rq.url().startsWith(API) || rq.method() === 'OPTIONS') return;
      const rs = await rq.response();
      log.push({ method: rq.method(), url: rq.url().replace(API, ''), status: rs ? rs.status() : '-' });
   });
   page.on('requestfailed', rq => {
      if (rq.url().startsWith(API) && rq.method() !== 'OPTIONS') {
         log.push({ method: rq.method(), url: rq.url().replace(API, ''), status: 'FAILED (' + (rq.failure()?.errorText || 'network') + ')' });
      }
   });
   page.on('dialog', d => d.accept());
   return { ctx, page, log, base };
}

/* login ผ่าน login.html แล้วเปิดหน้ายื่นคำร้อง (ตั้ง unlocked = 2 แทนการผ่านหน้าตรวจคุณสมบัติ U2) */
export async function loginStudent(p, username, gotoSubmit = true) {
   await p.page.goto(p.base + '/login.html');
   await p.page.fill('#u', username);
   await p.page.fill('#p', DEMO_PASS);
   await p.page.click('#loginBtn');
   await p.page.waitForURL(/check|submit|ranking/, { timeout: 15000 });
   if (!gotoSubmit) return;
   await p.page.evaluate(() => {
      const s = JSON.parse(sessionStorage.getItem('coop-state') || '{}');
      s.unlocked = Math.max(Number(s.unlocked) || 1, 2);
      sessionStorage.setItem('coop-state', JSON.stringify(s));
   });
   await p.page.goto(p.base + '/submit.html');
   await p.page.evaluate(() => window.U3Submit && window.U3Submit.ready);
}

export async function loginStaff(p) {
   await p.page.goto(p.base + '/login.html');
   await p.page.fill('#u', ACC.staff.username);
   await p.page.fill('#p', DEMO_PASS);
   await p.page.click('#loginBtn');
   await p.page.waitForURL('**/staff-review.html', { timeout: 15000 });
}

export async function fillForm(page, values = ANSWERS) {
   for (const [id, v] of Object.entries(values)) await page.fill('#' + id, v);
}

export async function clickSend(page) {
   await page.click('#btnSend');
   await page.waitForFunction(() => document.querySelector('#vLoad').hidden, null, { timeout: 20000 });
}

export const text = async (page, sel) => ((await page.locator(sel).textContent()) || '').trim();
export const apiLog = log => log.map(x => `${x.method} ${x.url} → ${x.status}`);

/* แคปจอพร้อมแถบหลักฐาน (request /api/* ที่หน้าเว็บส่ง + status) แทน DevTools */
export async function shot(page, file, title, log) {
   const lines = [`<b>${title}</b> · ${new Date().toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' })}`,
      'Network (/api/*): ' + (apiLog(log).join(' | ') || '(ไม่มี request)')];
   await page.evaluate(html => {
      const d = document.createElement('div');
      d.id = '__u3';
      d.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;background:#111;color:#0f0;font:12px/1.5 Consolas,monospace;padding:8px 12px;white-space:pre-wrap;word-break:break-all';
      d.innerHTML = html;
      document.body.appendChild(d);
   }, lines.join('<br>')).catch(() => { });
   await page.screenshot({ path: join(OUT, file), fullPage: true });
   await page.evaluate(() => document.getElementById('__u3')?.remove()).catch(() => { });
}
