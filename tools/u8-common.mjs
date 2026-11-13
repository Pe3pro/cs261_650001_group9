/* =====================================================================
   ตัวช่วยร่วมของสคริปต์ U8:T5 (u8-t5-ui.mjs, u8-t5-st.mjs, u8-t5-uat.mjs)
   รันใน container mcr.microsoft.com/playwright ผ่าน tools/u8-t5-run.ps1 (เครื่องทีมไม่มี node)
   - static server เสิร์ฟ frontend/ ที่ http://localhost:5599 (ไฟล์เดียวกับ nginx :8081 ไม่แก้โค้ด)
   - หน้าเว็บเรียก http://localhost:8080 ตามโค้ดจริง · ใน container ส่งต่อ request นั้นไปที่ backend (APP_URL = http://app:8080)
     ด้วย page.route โดยไม่แก้ URL ที่หน้าเว็บเห็น · backend หยุด → request ล้มเหลวเหมือนต่อ localhost:8080 ไม่ได้
   - ตรวจ MySQL ตรง (DB_HOST = db) · ไฟล์แนบใน volume uploads (--volumes-from cs261-app)
   - แคปจอพร้อมแถบหลักฐาน (request /api/* + status) แทน DevTools
   ===================================================================== */
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, extname, resolve } from 'node:path';
import mysql from 'mysql2/promise';

export const ROOT = process.env.REPO || resolve('.');
export const FRONT = join(ROOT, 'frontend');
export const OUT = join(ROOT, '.doc', 'UserStory', 'U8', 'evidence');
export const APP = process.env.APP_URL || 'http://app:8080';
export const PAGE_API = 'http://localhost:8080';
export const PASS = process.env.U8_DEMO_PASS || '1234';
export const PORT = Number(process.env.U8_PORT) || 5599;
export const UPLOADS = process.env.U8_UPLOADS || '/app/uploads';

export const sleep = ms => new Promise(r => setTimeout(r, ms));
export const PDF = Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n% U8 timesheet (test)\n%%EOF\n');

/* ---------- วันที่ ---------- */
// backend ใช้ Clock.systemDefaultZone() ของ container (UTC) → "วันนี้" ของ backend = วันที่ UTC
export const serverToday = () => new Date().toISOString().slice(0, 10);
// หน้าเว็บ (เบราว์เซอร์ตั้ง Asia/Bangkok เหมือนผู้ใช้จริง)
export const thaiToday = () => new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10);
export function addDays(iso, n) { const d = new Date(iso + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); }
export const isWeekday = iso => { const g = new Date(iso + 'T00:00:00Z').getUTCDay(); return g !== 0 && g !== 6; };
/** วันทำงาน (จ.–ศ.) ก่อน "วันนี้" ของ backend เรียงใหม่ → เก่า */
export function weekdaysBefore(today = serverToday(), n = 15) {
   const out = []; let d = addDays(today, -1);
   while (out.length < n) { if (isWeekday(d)) out.push(d); d = addDays(d, -1); }
   return out;
}
/** วันทำงานที่ห่างจากวันนี้อย่างน้อย n วัน (ส่งแล้วเป็น "ส่งล่าช้า") */
export function weekdayAtLeast(n, today = serverToday()) { let d = addDays(today, -n); while (!isWeekday(d)) d = addDays(d, -1); return d; }
const TH_MONTH = ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.'];
export const thaiDate = iso => { const [y, m, d] = iso.split('-').map(Number); return `${d} ${TH_MONTH[m - 1]} ${y + 543}`; };

/* ---------- API ตรง (แทน Postman) ---------- */
export async function api(method, path, userId, body, file) {
   const headers = {};
   if (userId != null) headers['X-User-Id'] = String(userId);
   let payload;
   if (file) { payload = new FormData(); payload.append('file', new Blob([file.buffer], { type: file.mimeType }), file.name); }
   else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
   const res = await fetch(APP + path, { method, headers, body: payload });
   const buf = Buffer.from(await res.arrayBuffer());
   let data = buf.toString('utf8');
   try { data = JSON.parse(data); } catch { /* ข้อความ / CSV */ }
   return { status: res.status, data, buf, type: res.headers.get('content-type') || '' };
}
export const logBody = (date, start = '09:00', end = '12:00', activity = 'ประชุมทีมและวางแผนงาน') =>
   ({ logDate: date, startTime: start, endTime: end, activity, learning: 'การแบ่งงานด้วย Scrum', problem: null });

export async function backendUp(timeoutMs = 180000) {
   const end = Date.now() + timeoutMs;
   while (Date.now() < end) {
      try { const r = await api('GET', '/api/users/6609650031'); if (r.status === 200) return true; } catch { /* ยังไม่ขึ้น */ }
      await sleep(2000);
   }
   return false;
}

export async function users() {
   const id = async u => (await api('GET', '/api/users/' + u)).data.id;
   const u = { staff: await id('staff01'), advisor: await id('advisor01'), A: await id('6609650031'), B: await id('6609650032'), C: await id('6609650033') };
   u.iA = (await api('GET', '/api/work-logs/my-internship', u.A)).data;
   u.iB = (await api('GET', '/api/work-logs/my-internship', u.B)).data;
   return u;
}

/* ---------- MySQL ---------- */
let pool;
export async function sql(q, args = []) {
   pool ||= mysql.createPool({ host: process.env.DB_HOST || 'db', user: 'root', password: 'root1234', database: 'cs261db', dateStrings: true, connectionLimit: 2 });
   const [rows] = await pool.query(q, args);
   return rows;
}
export const one = async (q, args) => { const r = await sql(q, args); return r.length ? Object.values(r[0])[0] : null; };
export const closeDb = () => pool && pool.end();
export const fileInVolume = path => existsSync(join(UPLOADS, path.slice(path.indexOf('work-logs/'))));

/* ---------- static server ---------- */
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png' };
export function startStatic(port = PORT) {
   const base = `http://localhost:${port}`;
   const server = createServer(async (req, res) => {
      const path = decodeURIComponent(new URL(req.url, base).pathname);
      const file = resolve(FRONT, '.' + (path === '/' ? '/login.html' : path));
      if (!file.startsWith(FRONT)) { res.writeHead(403).end(); return; }
      try { res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream' }).end(await readFile(file)); }
      catch { res.writeHead(404).end('not found'); }
   });
   return new Promise(r => server.listen(port, () => r({ server, base })));
}

/* ---------- browser ---------- */
export async function newPage(browser, base) {
   const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, timezoneId: 'Asia/Bangkok', locale: 'th-TH', acceptDownloads: true });
   // หน้าเว็บเรียก http://localhost:8080/api/... ตามโค้ดจริง → ส่งต่อไปที่ backend ใน docker network
   await ctx.route(PAGE_API + '/**', async route => {
      const rq = route.request();
      try {
         const res = await fetch(rq.url().replace(PAGE_API, APP), {
            method: rq.method(), headers: Object.fromEntries(Object.entries(rq.headers()).filter(([k]) => !['host', 'content-length'].includes(k))),
            body: ['GET', 'HEAD'].includes(rq.method()) ? undefined : rq.postDataBuffer()
         });
         const headers = Object.fromEntries(res.headers.entries());
         headers['access-control-allow-origin'] = headers['access-control-allow-origin'] || '*';
         await route.fulfill({ status: res.status, headers, body: Buffer.from(await res.arrayBuffer()) });
      } catch {
         await route.abort('connectionrefused');   // backend ไม่ทำงาน
      }
   });
   const page = await ctx.newPage();
   const log = [];
   const tag = rq => rq.url().startsWith(PAGE_API) && rq.method() !== 'OPTIONS';
   page.on('requestfinished', async rq => {
      if (!tag(rq)) return;
      const rs = await rq.response();
      let body = '';
      try { body = (await rs.text()).slice(0, 300); } catch { /* ไฟล์ */ }
      log.push({ method: rq.method(), url: rq.url().replace(PAGE_API, ''), status: rs ? rs.status() : '-', body });
   });
   page.on('requestfailed', rq => { if (tag(rq)) log.push({ method: rq.method(), url: rq.url().replace(PAGE_API, ''), status: 'FAILED (' + (rq.failure()?.errorText || 'network') + ')' }); });
   page.dialogs = [];
   page.dialogAnswer = true;
   page.on('dialog', d => { page.dialogs.push(d.message()); page.dialogAnswer ? d.accept() : d.dismiss(); });
   return { ctx, page, log, base, mark() { return log.length; }, since(i) { return log.slice(i); } };
}
export const apiLog = log => log.map(x => `${x.method} ${x.url} → ${x.status}`);

export async function login(p, username) {
   await p.page.goto(p.base + '/login.html');
   await p.page.fill('#u', username);
   await p.page.fill('#p', PASS);
   await p.page.click('#loginBtn');
   await p.page.waitForURL(u => !String(u).includes('login.html'), { timeout: 20000 });
}
export async function openWorklog(p) {
   await p.page.goto(p.base + '/worklog.html');
   await p.page.waitForFunction(() => {
      const h = document.querySelector('#wHist'); const e = document.querySelector('#wErr');
      return (h && h.children.length > 0) || (e && !e.hidden && e.textContent.trim());
   }, null, { timeout: 20000 });
   await sleep(300);
}
/** รอให้ request ที่หน้าเว็บส่งหลังกดปุ่มเสร็จ (ปุ่มกลับมาใช้ได้) */
export async function settle(page) {
   await page.waitForFunction(() => !document.querySelector('#wSend')?.textContent.includes('กำลัง'), null, { timeout: 20000 });
   await sleep(500);
}
export async function fill(page, { date, start, end, task, skill = 'การแบ่งงานด้วย Scrum', prob = '' }) {
   if (date !== undefined) { await page.fill('#wDate', date); await page.dispatchEvent('#wDate', 'change'); await sleep(200); }
   if (start !== undefined) await page.fill('#wStart', start);
   if (end !== undefined) await page.fill('#wEnd', end);
   if (task !== undefined) await page.fill('#wTask', task);
   if (skill !== undefined) await page.fill('#wSkill', skill);
   if (prob !== undefined) await page.fill('#wProb', prob);
}
export const val = (page, sel) => page.inputValue(sel);
export const txt = async (page, sel) => ((await page.locator(sel).first().textContent()) || '').replace(/\s+/g, ' ').trim();
export const shown = (page, sel) => page.locator(sel).first().isVisible();
export const histRow = (page, iso) => page.locator('#wHist button', { hasText: new RegExp('^\\s*' + thaiDate(iso).replace(/\./g, '\\.') + '(?!\\d)') }).first();
export const histText = async (page, iso) => ((await histRow(page, iso).textContent().catch(() => '')) || '').replace(/\s+/g, ' ').trim();
export async function openRow(p, iso) { await histRow(p.page, iso).click(); await sleep(800); }

/* แคปจอพร้อมแถบหลักฐาน (request /api/* ที่หน้าเว็บส่ง + status) */
export async function shot(page, file, title, log) {
   await mkdir(OUT, { recursive: true });
   const lines = [`<b>${title}</b> · ${new Date().toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' })}`,
      'Network (/api/*): ' + (apiLog(log).join(' | ') || '(ไม่มี request)')];
   await page.evaluate(html => {
      const d = document.createElement('div');
      d.id = '__u8';
      d.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;background:#111;color:#0f0;font:12px/1.5 Consolas,monospace;padding:8px 12px;white-space:pre-wrap;word-break:break-all';
      d.innerHTML = html;
      document.body.appendChild(d);
   }, lines.join('<br>')).catch(() => { });
   await page.screenshot({ path: join(OUT, file), fullPage: true });
   await page.evaluate(() => document.getElementById('__u8')?.remove()).catch(() => { });
}

/* ---------- ผล ---------- */
export function reporter(name) {
   const results = [];
   const lines = [];
   const say = s => { console.log(s); lines.push(s); };
   return {
      results, say,
      async run(id, fn) {
         try { const actual = await fn(); results.push({ id, ok: true, actual }); say(`✅ ${id}\n     ${actual}`); }
         catch (e) { results.push({ id, ok: false, actual: e.message }); say(`❌ ${id}\n     ${e.message}`); }
      },
      async save(extra = {}) {
         await mkdir(OUT, { recursive: true });
         const pass = results.filter(r => r.ok).length;
         say(`\nสรุป: ผ่าน ${pass} / ${results.length}`);
         await writeFile(join(OUT, `${name}-output.txt`), lines.join('\n') + '\n', 'utf8');
         await writeFile(join(OUT, `${name}-result.json`), JSON.stringify({ ranAt: new Date().toISOString(), results, ...extra }, null, 1), 'utf8');
      }
   };
}
/** assert แบบสั้น: เงื่อนไขไม่จริง → test ไม่ผ่าน พร้อมรายละเอียด */
export function check(cond, msg) { if (!cond) throw new Error(msg); }
