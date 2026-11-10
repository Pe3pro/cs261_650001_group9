/* =====================================================================
   u5.js — U5 หน้าเจ้าหน้าที่ / อาจารย์
   หน้า: staff-forms.html (แบบฟอร์มคำร้อง) · staff-review.html (ตรวจใบสมัคร) · notifications.html
   แต่ละหน้าบอกตัวเองด้วย <body data-page="forms|review|notifications">
   หน้าตาตาม UI prototype ใน .doc/UserStory/U5 (U5 Staff 1–4, Advisor 1, Student)
   ===================================================================== */
(function () {
   'use strict';

   const $ = (s, r = document) => r.querySelector(s);
   const $$ = (s, r = document) => [...r.querySelectorAll(s)];
   const API = window.U5_API_BASE || 'http://localhost:8080';
   const USER_KEY = 'u5-user';
   const DEMO_KEY = 'u5-demo';
   const TEAM_KEY = 'coop-state';   // state ที่ login.html (U1) และ js/script.js ของทีมใช้ร่วมกัน

   /* บัญชีทดลอง (ตรงกับ DemoDataSeeder) ใช้ username นี้ login ที่ login.html รหัสผ่าน 1234 */
   const DEMO_USERS = [
      { id: 1, role: 'STAFF', name: 'สมศรี ทำงานดี', username: 'staff01' },
      { id: 2, role: 'ADVISOR', name: 'อ.ดร.วิชัย สอนดี', username: 'advisor01' },
      { id: 3, role: 'STUDENT', name: 'สมชาย ใจดี', username: '6609650001' }
   ];
   const ROLE_TH = { STAFF: 'เจ้าหน้าที่', ADVISOR: 'อาจารย์', STUDENT: 'นศ.' };

   const STATUS_TH = {
      DRAFT: 'ฉบับร่าง', SUBMITTED: 'รอตรวจ', UNDER_REVIEW: 'รออาจารย์พิจารณา', NEED_CORRECTION: 'ส่งกลับแก้ไข',
      APPROVED: 'อนุมัติแล้ว', REJECTED: 'ปฏิเสธ', PUBLISHED: 'เปิดใช้งาน', ARCHIVED: 'เลิกใช้'
   };
   const DECISION_TH = { APPROVE: 'อนุมัติ', RETURN: 'ส่งกลับแก้ไข', REJECT: 'ปฏิเสธ' };
   const FIELD_TYPES = { TEXT: 'ข้อความสั้น', TEXTAREA: 'ข้อความยาว', NUMBER: 'ตัวเลข', EMAIL: 'อีเมล', PHONE: 'เบอร์โทรศัพท์', DATE: 'วันที่', SELECT: 'ตัวเลือก', FILE: 'แนบไฟล์' };
   const MONTHS = ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.'];
   /* U5: ป้ายเตือนในรายการคำร้อง (จาก server: attention) */
   const ATTN_TH = { DEADLINE_SOON: 'ใกล้หมดเขต', OVERDUE: 'รอนาน' };
   /* U5: เหตุผลที่ใช้บ่อย เลือกแล้วเติมลงช่องเหตุผล (แก้ต่อได้) */
   const PRESET_REASONS = [
      'Transcript ไม่ชัดเจน กรุณาแนบไฟล์ใหม่',
      'Transcript ไม่ใช่ฉบับล่าสุด',
      'ข้อมูลติดต่อไม่ถูกต้อง กรุณาตรวจสอบเบอร์โทรศัพท์และอีเมล',
      'กรอกข้อมูลไม่ครบ',
      'เอกสารแนบไม่ครบตามที่กำหนด',
      'คุณสมบัติไม่ผ่านเกณฑ์ (GPAX / หน่วยกิต)'
   ];
   const MSG = {
      network: 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาเปิด backend (docker compose up -d) แล้วลองใหม่',
      noDecision: 'กรุณาเลือกผลการพิจารณา',
      needReason: 'กรุณากรอกเหตุผลเมื่อส่งกลับแก้ไขหรือปฏิเสธ',
      cannotSave: 'ยังบันทึกผลไม่ได้ กรุณากรอกเหตุผล'
   };

   /* ---------- ตัวช่วย ---------- */
   const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
   const show = (el, msg) => { el.textContent = msg; el.hidden = false; };
   const hide = el => { el.hidden = true; };
   const views = ids => id => ids.forEach(v => { const el = $('#' + v); if (el) el.hidden = v !== id; });
   /* วันที่แบบ "15 ต.ค. 2569 10:24" */
   function fmtDate(iso) {
      if (!iso) return '-';
      const d = new Date(iso);
      if (isNaN(d)) return '-';
      const p = n => String(n).padStart(2, '0');
      return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear() + 543} ${p(d.getHours())}:${p(d.getMinutes())}`;
   }
   const badge = s => `<span class="badge ${esc(s)}">${esc(STATUS_TH[s] || s)}</span>`;

   const store = {
      get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } },
      set(k, v) { try { v == null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch (e) { } }
   };
   let user = null;
   try { user = JSON.parse(store.get(USER_KEY) || 'null'); } catch (e) { user = null; }

   /* username ที่ login ไว้จากหน้า login.html ของทีม (U1) */
   function teamState() { try { return JSON.parse(store.get(TEAM_KEY) || '{}') || {}; } catch (e) { return {}; } }
   const loginName = () => teamState().user || null;
   function setLoginName(name) { const st = teamState(); st.user = name; store.set(TEAM_KEY, JSON.stringify(st)); }
   function logout() {
      setLoginName(null);              // เก็บความคืบหน้าอื่นของทีมไว้เหมือน logout ใน script.js
      store.set(USER_KEY, null);
      store.set('u1-auth', null);      // U1: token จาก POST /api/auth/login
      location.href = 'login.html';
   }

   /* โหมดข้อมูลตัวอย่าง: ใช้ดูหน้าเว็บโดยไม่ต้องเปิด backend (?demo=1 เปิด, ?demo=0 ปิด) */
   const qs = new URLSearchParams(location.search);
   if (qs.get('demo') === '1') store.set(DEMO_KEY, '1');
   if (qs.get('demo') === '0') store.set(DEMO_KEY, null);
   const demo = () => store.get(DEMO_KEY) === '1' && typeof window.U5Demo === 'object';

   /* เรียก API: คืน {ok, status, data} ถ้าเชื่อมต่อไม่ได้คืน {ok:false, status:0} */
   async function api(path, opts = {}) {
      if (demo()) return window.U5Demo.handle(opts.method || 'GET', path, opts.body || null, user?.id);
      const headers = { 'X-User-Id': String(user?.id ?? ''), ...(opts.body ? { 'Content-Type': 'application/json' } : {}) };
      try {
         const res = await fetch(API + path, { method: opts.method || 'GET', headers, body: opts.body ? JSON.stringify(opts.body) : undefined });
         let data = null;
         try { data = await res.json(); } catch (e) { }
         return { ok: res.ok, status: res.status, data };
      } catch (e) {
         return { ok: false, status: 0, data: null };
      }
   }
   const errMsg = r => r.status === 0 ? MSG.network : (r.data?.message || 'เกิดข้อผิดพลาด (' + r.status + ')');
   /* แสดง error ถ้าเป็นเพราะเชื่อมต่อไม่ได้ ให้มีปุ่มเปิดโหมดข้อมูลตัวอย่าง */
   function showErr(el, r) {
      show(el, errMsg(r));
      if (r.status === 0 && typeof window.U5Demo === 'object') {
         const b = document.createElement('button');
         b.type = 'button'; b.className = 'btn sm ghost'; b.style.marginLeft = '12px';
         b.textContent = 'ดูด้วยข้อมูลตัวอย่าง';
         b.onclick = () => { const q = new URLSearchParams(location.search); q.set('demo', '1'); location.search = q.toString(); };
         el.appendChild(b);
      }
   }

   /* ขั้นตอนล่าสุดที่นักศึกษาทำถึง (1 = ตรวจสอบคุณสมบัติ, 2 = ยื่นคำร้อง, 3 = จับคู่บริษัท) */
   function studentUnlocked() {
      try {
         const n = Number(JSON.parse(sessionStorage.getItem('coop-state') || '{}').unlocked);
         return Math.min(Math.max(Number.isFinite(n) ? n : 1, 1), 3);
      } catch (e) { return 1; }
   }

   /* อ่านคำร้องของนักศึกษาจากระบบ: มีคำร้อง → ผ่านขั้น 1 · ยื่นแล้ว → ผ่านครบ (เก็บลง coop-state ให้หน้าอื่นใช้ด้วย) */
   async function syncStudentProgress() {
      const r = await api('/api/requests/me');
      if (!r.ok || !Array.isArray(r.data) || !r.data.length) return false;
      const sent = r.data.some(x => ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'NEED_CORRECTION'].includes(x.status));
      const level = sent ? 3 : 2;
      try {
         const st = JSON.parse(sessionStorage.getItem('coop-state') || '{}');
         if ((Number(st.unlocked) || 1) >= level) return false;
         st.unlocked = level;
         sessionStorage.setItem('coop-state', JSON.stringify(st));
         return true;
      } catch (e) { return false; }
   }

   /* ---------- ส่วนหัว เมนู และการเลือกผู้ใช้ทดลอง ---------- */
   function initShell(page) {
      $('#who').textContent = user ? ROLE_TH[user.role] + ' ' + user.name : (loginName() || 'ยังไม่ได้เข้าสู่ระบบ');
      $('#logout').onclick = logout;
      let links = [];
      if (user?.role === 'STAFF') links = [['staff-review.html', 'ตรวจใบสมัคร', 'review'], ['staff-forms.html', 'แบบฟอร์มคำร้อง', 'forms']];
      if (user?.role === 'ADVISOR') links = [['staff-review.html', 'พิจารณาใบสมัคร', 'review']];
      if (user?.role === 'STUDENT') links = [['check.html', 'ตรวจสอบคุณสมบัติ', 'check'], ['submit.html', 'ยื่นคำร้อง', 'submit'], ['ranking.html', 'จับคู่บริษัท', 'ranking']];
      if (user) links.push(['notifications.html', 'การแจ้งเตือน', 'notifications']);
      /* นักศึกษา: ขั้นที่ยังทำไม่ถึงกดไม่ได้และแสดงจางๆ (ใช้ coop-state.unlocked เดียวกับหน้าตรวจสอบคุณสมบัติ) */
      const STEP = { check: 1, submit: 2, ranking: 3 };
      const renderNav = () => {
         const unlocked = user?.role === 'STUDENT' ? studentUnlocked() : 3;
         $('#u5nav').innerHTML = links.map(([href, label, p]) => {
            if (STEP[p] > unlocked) return `<a href="${href}" class="disabled" aria-disabled="true" tabindex="-1">${label}</a>`;
            return `<a href="${href}"${p === page ? ' class="on" aria-current="page"' : ''}>${label}</a>`;
         }).join('');
      };
      renderNav();
      // ดูจากข้อมูลในระบบด้วยว่านักศึกษาทำถึงขั้นไหนแล้ว
      if (user?.role === 'STUDENT') syncStudentProgress().then(changed => { if (changed) renderNav(); });
      const bar = $('#demoBar');
      if (bar && demo()) {
         bar.innerHTML = 'โหมดข้อมูลตัวอย่าง: หน้าเว็บนี้ยังไม่ได้ต่อ backend ข้อมูลจะหายเมื่อปิดแท็บ <button type="button" id="demoOff">ปิดโหมดตัวอย่าง</button>';
         bar.hidden = false;
         $('#demoOff').onclick = () => { store.set(DEMO_KEY, null); location.reload(); };
      }
   }

   /* โหมดข้อมูลตัวอย่างที่ยังไม่ได้ login: เลือกบัญชีทดลอง (เขียน username ลง state ของทีมให้ด้วย) */
   function renderPicker() {
      const el = $('#vPick');
      el.innerHTML = `<h1>เลือกผู้ใช้ทดลอง</h1>
         <p class="sub">โหมดข้อมูลตัวอย่าง · ถ้าใช้ระบบจริงให้เข้าสู่ระบบที่ <a href="login.html">หน้า Login</a></p>
         <div class="card pick">${DEMO_USERS.map(u => `<button class="btn ghost" type="button" data-uid="${u.id}">${ROLE_TH[u.role]} ${esc(u.name)} (${u.username})</button>`).join('')}</div>`;
      el.hidden = false;
      $$('[data-uid]', el).forEach(b => b.onclick = () => {
         user = DEMO_USERS.find(u => String(u.id) === b.dataset.uid);
         store.set(USER_KEY, JSON.stringify(user));
         setLoginName(user.username);
         location.reload();
      });
   }

   /* login แล้วแต่หาบัญชีไม่เจอ / เชื่อมต่อไม่ได้ */
   function renderLoginProblem(r) {
      const el = $('#vPick');
      el.innerHTML = `<h1>เข้าใช้งานไม่ได้</h1>
         <div class="card"><p class="errmsg" id="loginErr" style="margin:0 0 16px"></p>
         <p class="mute" style="margin:0 0 16px">บัญชีทดลอง: staff01 (เจ้าหน้าที่) · advisor01 (อาจารย์) · 6609650001 (นักศึกษา) รหัสผ่าน 1234</p>
         <button class="btn" type="button" id="btnRelogin">กลับไปหน้า Login</button></div>`;
      el.hidden = false;
      showErr($('#loginErr'), r);
      $('#btnRelogin').onclick = logout;
   }

   /* หา id และ role ของคนที่ login ไว้ (ใช้ซ้ำจาก sessionStorage ถ้าเป็นคนเดิม) */
   async function resolveUser() {
      const name = loginName();
      if (!name) { user = null; return { ok: false, status: 401 }; }
      if (user && user.username === name) return { ok: true };
      user = null;
      const r = await api('/api/users/' + encodeURIComponent(name));
      if (!r.ok) { store.set(USER_KEY, null); return r; }
      user = { id: r.data.id, role: r.data.role, name: r.data.name, username: r.data.username };
      store.set(USER_KEY, JSON.stringify(user));
      return { ok: true };
   }

   /* =====================================================================
      หน้า ตรวจใบสมัคร (staff-review.html) — prototype U5 Staff 1–4, Advisor 1
      ===================================================================== */
   const R = { status: null, current: null, busy: false, rows: [] };
   const reviewView = views(['vPick', 'vDenied', 'vList', 'vDetail', 'vDone']);
   const isAdvisor = () => user.role === 'ADVISOR';

   /* แท็บสถานะ [status, ชื่อแท็บ] */
   function reviewTabs() {
      return isAdvisor()
         ? [['UNDER_REVIEW', 'รอพิจารณา'], ['APPROVED', 'อนุมัติแล้ว'], ['NEED_CORRECTION', 'ส่งกลับแก้ไข']]
         : [['SUBMITTED', 'รอตรวจ'], ['NEED_CORRECTION', 'ส่งกลับแก้ไข'], ['UNDER_REVIEW', 'ส่งต่ออาจารย์แล้ว'], ['REJECTED', 'ปฏิเสธ']];
   }
   const canAct = status => (user.role === 'STAFF' && status === 'SUBMITTED') || (isAdvisor() && status === 'UNDER_REVIEW');

   async function initReview() {
      if (!['STAFF', 'ADVISOR'].includes(user.role)) return reviewView('vDenied');
      $('#listTitle').textContent = isAdvisor() ? 'พิจารณาใบสมัคร' : 'ตรวจใบสมัคร';
      $('#listSub').textContent = isAdvisor() ? 'ใบสมัครที่เจ้าหน้าที่ตรวจผ่านแล้ว รออาจารย์พิจารณาอนุมัติ' : 'ใบสมัครฝึกสหกิจศึกษาที่นักศึกษายื่นเข้ามา รอเจ้าหน้าที่ตรวจสอบ';
      $('#detailTitle').textContent = isAdvisor() ? 'พิจารณาใบสมัคร' : 'ตรวจใบสมัคร';
      $('#approveLabel').textContent = isAdvisor() ? 'อนุมัติ' : 'อนุมัติ ส่งต่ออาจารย์';
      $('#btnBack').onclick = () => loadList(R.status);
      $('#btnExport').onclick = exportList;
      $('#btnBulk').onclick = () => confirmClick($('#btnBulk'), 'ยืนยันอนุมัติ ' + R.selected.size + ' รายการ', bulkApprove);
      $('#presetReason').innerHTML = '<option value="">เลือกเหตุผลที่ใช้บ่อย…</option>'
         + PRESET_REASONS.map(t => `<option>${esc(t)}</option>`).join('');
      $('#presetReason').onchange = usePreset;
      $$('input[name="decision"]').forEach(i => i.addEventListener('change', toggleFixFields));
      $('#btnDoneBack').onclick = () => loadList(R.status);
      $('#btnDoneView').onclick = () => openDetail(R.current.id);
      $('#btnDecide').onclick = submitDecision;
      $('#search').oninput = renderRows;
      $('#decisionCard').addEventListener('input', clearDecisionError);
      await loadList(reviewTabs()[0][0]);
   }

   /* message = ข้อความผลลัพธ์ที่จะแสดงเหนือรายการหลังโหลดเสร็จ (เช่น ผลการอนุมัติหลายรายการ) */
   async function loadList(status, message) {
      R.status = status;
      R.selected = new Set();
      reviewView('vList');
      $('#tabs').innerHTML = reviewTabs().map(([s, label]) =>
         `<button type="button" role="tab" class="chip${s === status ? ' on' : ''}" data-status="${s}" aria-selected="${s === status}">${label}</button>`).join('');
      $$('#tabs .chip').forEach(b => b.onclick = () => loadList(b.dataset.status));
      hide($('#listErr'));
      if (message) show($('#listMsg'), message); else hide($('#listMsg'));
      R.rows = [];
      R.loaded = false;
      renderRows();
      const r = await api('/api/applications?status=' + encodeURIComponent(status));
      if (!r.ok) return showErr($('#listErr'), r);
      R.rows = r.data || [];
      R.loaded = true;
      renderRows();
      await loadCounts(status);
   }

   /* U5: ตัวเลขบนทุกแท็บจาก GET /api/applications/summary · ถ้าเรียกไม่ได้ แสดงเฉพาะแท็บที่เปิดอยู่ */
   async function loadCounts(status) {
      const s = await api('/api/applications/summary');
      if (R.status !== status) return;   // ผู้ใช้เปลี่ยนแท็บไปแล้ว
      const counts = s.ok && s.data ? s.data : { [status]: R.rows.length };
      $$('#tabs .chip').forEach(tab => {
         const n = counts[tab.dataset.status];
         $('b', tab)?.remove();
         if (n) tab.insertAdjacentHTML('beforeend', `<b>${Number(n)}</b>`);
      });
   }

   /* เลือกหลายรายการได้เฉพาะแท็บที่ผู้ใช้มีสิทธิ์อนุมัติ */
   const bulkMode = () => canAct(R.status);

   function renderHead() {
      const cols = isAdvisor()
         ? ['รหัสนักศึกษา', 'ชื่อ-นามสกุล', 'ตรวจโดยเจ้าหน้าที่', 'วันที่ส่งต่อ', 'สถานะ', '']
         : ['รหัสนักศึกษา', 'ชื่อ-นามสกุล', 'วันที่ยื่น', 'เอกสาร', 'สถานะ', ''];
      const all = bulkMode() ? '<th class="ck"><input type="checkbox" id="ckAll" aria-label="เลือกทั้งหมด"></th>' : '';
      $('#appHead').innerHTML = '<tr>' + all + cols.map(h => `<th>${h}</th>`).join('') + '</tr>';
   }

   function visibleRows() {
      const q = ($('#search').value || '').trim().toLowerCase();
      return R.rows.filter(a => !q || String(a.studentName || '').toLowerCase().includes(q) || String(a.studentId || '').includes(q));
   }

   function renderRows() {
      const rows = visibleRows();
      const bulk = bulkMode();
      renderHead();
      const empty = $('#emptyMsg');
      empty.hidden = !R.loaded || rows.length > 0;
      empty.textContent = $('#search').value.trim() && R.rows.length ? 'ไม่พบใบสมัครที่ตรงกับคำค้นหา' : 'ไม่มีใบสมัครในสถานะนี้';
      $('#appRows').innerHTML = rows.map(a => {
         const act = canAct(a.status);
         const label = act ? (isAdvisor() ? 'พิจารณา' : 'ตรวจ') : 'ดู';
         const mid = isAdvisor()
            ? `<td>${esc(a.reviewedBy || '-')}</td><td>${fmtDate(a.reviewedAt)}</td>`
            : `<td>${fmtDate(a.submittedAt)}</td><td>${(a.documents || []).length} ไฟล์</td>`;
         const attn = ATTN_TH[a.attention] ? `<span class="badge ${esc(a.attention)}">${ATTN_TH[a.attention]}</span>` : '';
         const ck = bulk ? `<td class="ck"><input type="checkbox" data-pick="${a.id}" aria-label="เลือก ${esc(a.studentName)}"${R.selected.has(a.id) ? ' checked' : ''}></td>` : '';
         return `<tr${attn ? ' class="attn"' : ''}>${ck}<td>${esc(a.studentId)}</td><td>${esc(a.studentName)}</td>${mid}<td>${badge(a.status)}${attn}</td>
            <td><button type="button" class="btn sm${act ? '' : ' ghost'}" data-open="${a.id}">${label}</button></td></tr>`;
      }).join('');
      $$('[data-open]').forEach(b => b.onclick = () => openDetail(Number(b.dataset.open)));
      $$('[data-pick]').forEach(c => c.onchange = () => {
         const id = Number(c.dataset.pick);
         if (c.checked) R.selected.add(id); else R.selected.delete(id);
         renderBulkBar();
      });
      const all = $('#ckAll');
      if (all) {
         all.checked = rows.length > 0 && rows.every(a => R.selected.has(a.id));
         all.onchange = () => {
            rows.forEach(a => { if (all.checked) R.selected.add(a.id); else R.selected.delete(a.id); });
            renderRows();
         };
      }
      renderBulkBar();
   }

   function renderBulkBar() {
      const bar = $('#bulkBar');
      const n = R.selected ? R.selected.size : 0;
      bar.hidden = !bulkMode() || n === 0;
      $('#bulkCount').textContent = 'เลือกแล้ว ' + n + ' รายการ';
      const b = $('#btnBulk');
      b.dataset.armed = ''; b.textContent = isAdvisor() ? 'อนุมัติที่เลือก' : 'อนุมัติที่เลือก ส่งต่ออาจารย์'; b.classList.remove('warn');
   }

   /* U5: อนุมัติหลายรายการ · server ข้ามรายการที่อนุมัติไม่ได้และบอกเหตุผล */
   async function bulkApprove() {
      if (R.busy || !R.selected.size) return;
      R.busy = true;
      $('#btnBulk').disabled = true;
      const r = await api('/api/approvals/bulk', { method: 'POST', body: { applicationIds: [...R.selected] } });
      R.busy = false;
      $('#btnBulk').disabled = false;
      if (!r.ok) return showErr($('#listErr'), r);
      const done = (r.data.approved || []).length;
      const skipped = r.data.skipped || [];
      const msg = 'อนุมัติแล้ว ' + done + ' รายการ'
         + (skipped.length ? ' · ข้าม ' + skipped.length + ' รายการ: ' + skipped.map(x => 'เลขที่ ' + x.applicationId + ' (' + x.reason + ')').join(', ') : '');
      loadList(R.status, msg);
   }

   /* U5: ดาวน์โหลดรายการในแท็บนี้เป็น CSV (เปิดด้วย Excel ได้) */
   async function exportList() {
      hide($('#listErr'));
      const name = 'applications-' + R.status + '.csv';
      let blob;
      if (demo()) {
         const cell = v => { const t = String(v ?? ''); return /[",\r\n]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t; };
         const lines = [['เลขที่คำร้อง', 'รหัสนักศึกษา', 'ชื่อ-นามสกุล', 'สถานะ', 'วันที่ส่ง', 'ผู้พิจารณาล่าสุด', 'วันที่พิจารณา', 'ไฟล์แนบ', 'หมายเหตุ']]
            .concat(R.rows.map(a => [a.id, a.studentId, a.studentName, a.status, a.submittedAt, a.reviewedBy, a.reviewedAt, (a.documents || []).length, a.attention]));
         blob = new Blob(['\uFEFF' + lines.map(l => l.map(cell).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' });
      } else {
         try {
            const res = await fetch(API + '/api/applications/export?status=' + encodeURIComponent(R.status), { headers: { 'X-User-Id': String(user?.id ?? '') } });
            if (!res.ok) {
               let data = null;
               try { data = await res.json(); } catch (e) { }
               return showErr($('#listErr'), { ok: false, status: res.status, data });
            }
            blob = await res.blob();
         } catch (e) {
            return showErr($('#listErr'), { ok: false, status: 0 });
         }
      }
      if (typeof URL.createObjectURL !== 'function') return;
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 1000);
   }

   function setReviewSteps(status) {
      // 1 นักศึกษายื่น · 2 เจ้าหน้าที่ตรวจ · 3 อาจารย์พิจารณา · 4 อนุมัติ
      const cur = { SUBMITTED: 2, UNDER_REVIEW: 3, APPROVED: 4 }[status] || 2;
      $$('#vDetail .steps .st').forEach((el, i) => {
         const n = i + 1;
         let c = '', d = n;
         if (status === 'APPROVED' || n < cur) { c = 'done'; d = '✓'; }
         else if (n === cur && (status === 'REJECTED' || status === 'NEED_CORRECTION')) { c = 'err'; d = '✕'; }
         else if (n === cur) c = 'cur';
         el.className = 'st ' + c;
         $('.dot', el).textContent = d;
      });
   }

   async function openDetail(id) {
      const r = await api('/api/applications/' + id + '/detail');
      if (!r.ok) { reviewView('vList'); return showErr($('#listErr'), r); }
      const a = r.data;
      R.current = a;
      reviewView('vDetail');
      setReviewSteps(a.status);
      const info = [['ชื่อ-นามสกุล', a.studentName], ['รหัสนักศึกษา', a.studentId], ['คณะ / สาขา', (a.faculty || '-') + ' / ' + (a.major || '-')],
      ['GPAX', a.gpa != null ? Number(a.gpa).toFixed(2) : '-'], ['อีเมล', a.email], ['วันที่ยื่น', fmtDate(a.submittedAt)]];
      $('#dInfo').innerHTML = info.map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v ?? '-')}</dd></div>`).join('');
      // U5: ช่องที่นักศึกษาแก้หลังถูกตีกลับ แสดงค่าเดิมขีดฆ่าไว้ให้เทียบ
      $('#dAnswers').innerHTML = (a.answers || []).length
         ? a.answers.map(x => x.previousValue != null
            ? `<div><dt>${esc(x.label)} <span class="badge NEED_CORRECTION">แก้แล้ว</span></dt><dd class="changed">${esc(x.value || '-')}<span class="was">เดิม: ${esc(x.previousValue || '(ว่าง)')}</span></dd></div>`
            : `<div><dt>${esc(x.label)}</dt><dd>${esc(x.value || '-')}</dd></div>`).join('')
         : '<p class="mute">ไม่มีข้อมูลในแบบฟอร์ม</p>';
      $('#fixList').innerHTML = (a.answers || []).filter(x => x.fieldId != null)
         .map(x => `<label><input type="checkbox" class="fix" value="${Number(x.fieldId)}"> ${esc(x.label)}</label>`).join('');
      $('#dDocs').innerHTML = (a.documents || []).length
         ? a.documents.map(d => `<div class="doc"><span class="ico">PDF</span><div>${esc(d.fileName)}<small>${d.status === 'VERIFIED' ? 'ตรวจแล้ว' : 'อัปโหลดแล้ว'}</small></div>
            <button type="button" class="btn sm ghost" data-doc="${d.id}">ดูไฟล์</button></div>`).join('')
         : '<div class="alert bad">ไม่พบเอกสารแนบ</div>';
      $$('[data-doc]').forEach(b => b.onclick = () => { if (!demo()) window.open(API + '/api/documents/' + b.dataset.doc + '?userId=' + encodeURIComponent(user?.id ?? ''), '_blank'); });
      const hist = [`<div class="hist"><i class="dot ok">✓</i><span>ยื่นใบสมัคร<small>${fmtDate(a.submittedAt)} · ${esc(a.studentName)}</small></span></div>`]
         .concat((a.approvals || []).map(p => `<div class="hist"><i class="dot ok">✓</i><span>${p.approvalLevel === 'STAFF' ? 'เจ้าหน้าที่' : 'อาจารย์'}: ${DECISION_TH[p.decision]}<small>${fmtDate(p.decidedAt)} · ${esc(p.approverName)}${p.comment ? ' · ' + esc(p.comment) : ''}</small></span></div>`));
      if (a.status === 'SUBMITTED') hist.push('<div class="hist"><i class="dot">2</i><span>รอเจ้าหน้าที่ตรวจ<small>สถานะ SUBMITTED</small></span></div>');
      if (a.status === 'UNDER_REVIEW') hist.push('<div class="hist"><i class="dot">3</i><span>รออาจารย์พิจารณา<small>สถานะ UNDER_REVIEW</small></span></div>');
      $('#dHistory').innerHTML = hist.join('');
      $('#decisionCard').hidden = !canAct(a.status);
      $$('input[name="decision"]').forEach(i => { i.checked = i.value === 'APPROVE'; });   // ค่าเริ่มต้นตาม prototype
      $('#comment').value = '';
      $('#presetReason').value = '';
      toggleFixFields();
      clearDecisionError();
   }

   /* U5: เลือกช่องที่ต้องแก้ได้เฉพาะตอนส่งกลับแก้ไข */
   function toggleFixFields() {
      const isReturn = $('input[name="decision"]:checked')?.value === 'RETURN';
      $('#fixFields').hidden = !isReturn || !$$('#fixList .fix').length;
   }

   /* U5: เติมเหตุผลที่ใช้บ่อยต่อท้ายข้อความเดิม */
   function usePreset() {
      const sel = $('#presetReason');
      if (!sel.value) return;
      const c = $('#comment');
      c.value = c.value.trim() ? c.value.trimEnd() + '\n' + sel.value : sel.value;
      sel.value = '';
      clearDecisionError();
   }

   function clearDecisionError() {
      hide($('#decErr'));
      hide($('#commentErr'));
      $('#comment').classList.remove('err');
   }

   /* ตรวจฝั่งหน้าเว็บก่อนส่ง: คืนข้อความ error หรือ null */
   function validateDecision(decision, comment) {
      if (!decision) return MSG.noDecision;
      if (decision !== 'APPROVE' && !comment.trim()) return MSG.needReason;
      return null;
   }

   async function submitDecision() {
      if (R.busy) return;
      const decision = $('input[name="decision"]:checked')?.value || null;
      const comment = $('#comment').value;
      const err = validateDecision(decision, comment);
      if (err === MSG.needReason) {
         show($('#decErr'), MSG.cannotSave);
         show($('#commentErr'), MSG.needReason);
         $('#comment').classList.add('err');
         return;
      }
      if (err) return show($('#decErr'), err);
      clearDecisionError();
      R.busy = true;
      $('#btnDecide').disabled = true;
      const body = { decision, comment: comment.trim() || null };
      const fieldIds = decision === 'RETURN' ? $$('#fixList .fix:checked').map(c => Number(c.value)) : [];
      if (fieldIds.length) body.fieldIds = fieldIds;
      const r = await api('/api/approvals/' + R.current.id + '/decision', { method: 'POST', body });
      R.busy = false;
      $('#btnDecide').disabled = false;
      if (!r.ok) return showErr($('#decErr'), r);
      const next = r.data.applicationStatus;
      const who = esc(R.current.studentName) + ' (' + esc(R.current.studentId) + ')';
      $('#doneMsg').innerHTML = next === 'UNDER_REVIEW'
         ? `ใบสมัครของ ${who} ถูกส่งต่อให้อาจารย์พิจารณาแล้ว`
         : `บันทึกผล "${DECISION_TH[decision]}" ให้ใบสมัครของ ${who} แล้ว`;
      $('#doneInfo').innerHTML = `สถานะใหม่: <b>${esc(STATUS_TH[next])} (${esc(next)})</b><br>`
         + (next === 'UNDER_REVIEW' ? 'ระบบส่งการแจ้งเตือนถึงอาจารย์แล้ว' : 'ระบบส่งการแจ้งเตือนถึงนักศึกษาแล้ว');
      R.current.status = next;
      reviewView('vDone');
   }

   /* =====================================================================
      หน้า แบบฟอร์มคำร้อง (staff-forms.html)
      ===================================================================== */
   const F = { editingId: null, fields: [], busy: false };
   const formsView = views(['vPick', 'vDenied', 'vForms', 'vEdit']);

   async function initForms() {
      if (user.role !== 'STAFF') return formsView('vDenied');
      $('#btnNew').onclick = () => openEditor(null);
      $('#btnCancel').onclick = () => loadForms();
      $('#btnAddField').onclick = () => { readFields(); F.fields.push({ label: '', fieldType: 'TEXT', required: true, options: '' }); renderFields(); };
      $('#btnSaveDraft').onclick = () => saveForm(false);
      $('#btnSavePublish').onclick = () => confirmClick($('#btnSavePublish'), 'ยืนยันบันทึกและเปิดใช้งาน', () => saveForm(true));
      $('#btnPreview').onclick = togglePreview;
      $('#vEdit').addEventListener('input', () => hide($('#editErr')));
      await loadForms();
   }

   async function loadForms(message, ok = true) {
      formsView('vForms');
      const msg = $('#formsMsg');
      if (message) { msg.className = 'alert ' + (ok ? 'ok' : 'bad'); show(msg, message); } else hide(msg);
      const r = await api('/api/forms');
      if (!r.ok) { msg.className = 'alert bad'; return showErr(msg, r); }
      const list = r.data || [];
      $('#formsEmpty').hidden = list.length > 0;
      $('#formRows').innerHTML = list.map(f => `<tr>
            <td>${esc(f.version)}</td><td>${esc(f.name)}</td><td>${(f.fields || []).length} ช่อง</td>
            <td>${fmtDate(f.deadline)}</td><td>${badge(f.status)}</td>
            <td>${f.status === 'DRAFT'
            ? `<button type="button" class="btn ghost" data-edit="${f.id}">แก้ไข</button> <button type="button" class="btn" data-publish="${f.id}">เปิดใช้งาน</button>`
            : `<button type="button" class="btn ghost" data-view="${f.id}">ดู</button> <button type="button" class="btn ghost" data-copy="${f.id}">สร้างฉบับใหม่จากฉบับนี้</button>`
            + (isOpen(f) ? ` <button type="button" class="btn ghost" data-close="${f.id}">ปิดรับก่อนกำหนด</button>` : '')}</td></tr>`).join('');
      $$('[data-edit]').forEach(b => b.onclick = () => openEditor(list.find(f => f.id === Number(b.dataset.edit))));
      $$('[data-view]').forEach(b => b.onclick = () => openEditor(list.find(f => f.id === Number(b.dataset.view)), true));
      $$('[data-publish]').forEach(b => b.onclick = () => confirmClick(b, 'ยืนยันเปิดใช้งาน', () => publishForm(Number(b.dataset.publish))));
      $$('[data-copy]').forEach(b => b.onclick = () => copyForm(Number(b.dataset.copy)));
      $$('[data-close]').forEach(b => b.onclick = () => confirmClick(b, 'ยืนยันปิดรับคำร้องตอนนี้', () => closeForm(Number(b.dataset.close))));
   }

   /* แบบฟอร์มที่เปิดใช้งานและยังไม่หมดเขต */
   const isOpen = f => f.status === 'PUBLISHED' && (!f.deadline || new Date(f.deadline) > new Date());

   /* U5: ปิดรับคำร้องก่อนวันหมดเขต (คำร้องที่ส่งแล้วยังพิจารณาต่อได้) */
   async function closeForm(id) {
      const r = await api('/api/forms/' + id + '/close', { method: 'POST' });
      if (!r.ok) return loadForms(errMsg(r), false);
      loadForms('ปิดรับคำร้องของแบบฟอร์ม "' + r.data.name + '" แล้ว นักศึกษายื่นหรือส่งฉบับแก้ไม่ได้อีก');
   }

   /* ปุ่มที่ต้องกดยืนยัน 2 ครั้ง (ไม่ใช้ window.confirm): กดครั้งแรกเปลี่ยนข้อความ กดครั้งที่สองจึงทำงาน */
   function confirmClick(btn, confirmLabel, action) {
      if (btn.dataset.armed === '1') { btn.dataset.armed = ''; return action(); }
      btn.dataset.armed = '1';
      btn.dataset.label = btn.dataset.label || btn.textContent;
      btn.textContent = confirmLabel + '?';
      btn.classList.add('warn');
   }

   async function copyForm(id) {
      const r = await api('/api/forms/' + id + '/new-version', { method: 'POST' });
      if (!r.ok) return loadForms(errMsg(r), false);
      openEditor(r.data);
   }

   function openEditor(form, readOnly = false) {
      formsView('vEdit');
      hide($('#editErr'));
      F.editingId = form && !readOnly ? form.id : null;
      $('#editTitle').textContent = !form ? 'สร้างแบบฟอร์มใหม่' : (readOnly ? 'ดูแบบฟอร์ม (แก้ไขไม่ได้)' : 'แก้ไขแบบฟอร์มฉบับร่าง');
      $('#fName').value = form?.name || '';
      $('#fDesc').value = form?.description || '';
      $('#fDeadline').value = form?.deadline ? String(form.deadline).slice(0, 16) : '';
      F.fields = form ? form.fields.map(f => ({ label: f.label, fieldType: f.fieldType, required: !!f.required, options: f.options || '' }))
         : [{ label: 'ชื่อ-นามสกุล', fieldType: 'TEXT', required: true, options: '' }];
      renderFields(readOnly);
      ['#fName', '#fDesc', '#fDeadline'].forEach(s => { $(s).disabled = readOnly; });
      $('#btnAddField').hidden = readOnly;
      $('#btnSaveDraft').hidden = readOnly;
      $('#btnSavePublish').hidden = readOnly;
      $('#btnCancel').textContent = readOnly ? 'กลับ' : 'ยกเลิก';
      const sp = $('#btnSavePublish');
      sp.dataset.armed = ''; sp.textContent = 'บันทึกและเปิดใช้งาน'; sp.classList.remove('warn');
      F.readOnly = readOnly;
      $('#previewBox').hidden = true;
      $('#btnPreview').textContent = 'ดูตัวอย่างหน้านักศึกษา';
   }

   /* U5: ตัวอย่างแบบฟอร์มตามที่นักศึกษาจะเห็น (จากค่าที่กรอกอยู่ ยังไม่ต้องบันทึก) */
   function togglePreview() {
      const box = $('#previewBox');
      if (!box.hidden) {
         box.hidden = true;
         $('#btnPreview').textContent = 'ดูตัวอย่างหน้านักศึกษา';
         return;
      }
      if (!F.readOnly) readFields();
      const dl = $('#fDeadline').value;
      const input = (f, i) => {
         const id = 'pv_' + i;
         const req = f.required ? ' <em>*</em>' : '';
         let ctl;
         if (f.fieldType === 'TEXTAREA') ctl = `<textarea id="${id}" rows="3"></textarea>`;
         else if (f.fieldType === 'SELECT') ctl = `<select id="${id}"><option value="">-- เลือก --</option>${String(f.options || '').split('\n').map(o => o.trim()).filter(Boolean).map(o => `<option>${esc(o)}</option>`).join('')}</select>`;
         else if (f.fieldType === 'FILE') ctl = `<input id="${id}" type="file" accept=".pdf,application/pdf">`;
         else {
            const type = { NUMBER: 'number', EMAIL: 'email', PHONE: 'tel', DATE: 'date' }[f.fieldType] || 'text';
            ctl = `<input id="${id}" type="${type}">`;
         }
         return `<div class="fld"><label for="${id}">${esc(f.label || '(ยังไม่มีชื่อช่อง)')}${req}</label>${ctl}</div>`;
      };
      box.innerHTML = `<span class="pv-tag">ตัวอย่างหน้ายื่นคำร้องของนักศึกษา (ยังไม่บันทึก)</span>
         <h2>${esc($('#fName').value.trim() || '(ยังไม่มีชื่อแบบฟอร์ม)')}</h2>
         ${$('#fDesc').value.trim() ? `<p class="sub">${esc($('#fDesc').value.trim())}</p>` : ''}
         ${dl ? `<div class="alert info">หมดเขตยื่นคำร้อง ${fmtDate(dl)}</div>` : ''}
         ${F.fields.map(input).join('')}
         <div class="acts"><span></span><button class="btn" type="button" disabled>ส่งคำร้องเพื่อตรวจสอบ</button></div>`;
      box.hidden = false;
      $('#btnPreview').textContent = 'ซ่อนตัวอย่าง';
   }

   function renderFields(readOnly = false) {
      const dis = readOnly ? ' disabled' : '';
      $('#fieldList').innerHTML = F.fields.map((f, i) => `<div class="frow" data-i="${i}">
            <span class="no">${i + 1}</span>
            <input class="f-label" aria-label="ชื่อช่องที่ ${i + 1}" placeholder="ชื่อช่อง เช่น เบอร์โทรศัพท์" value="${esc(f.label)}"${dis}>
            <select class="f-type" aria-label="ชนิดของช่องที่ ${i + 1}"${dis}>${Object.entries(FIELD_TYPES).map(([k, v]) => `<option value="${k}"${k === f.fieldType ? ' selected' : ''}>${v}</option>`).join('')}</select>
            <label class="req"><input type="checkbox" class="f-req"${f.required ? ' checked' : ''}${dis}> ต้องกรอก</label>
            <span class="tools">${readOnly ? '' : `<button type="button" data-up="${i}" aria-label="เลื่อนขึ้น">↑</button><button type="button" data-down="${i}" aria-label="เลื่อนลง">↓</button><button type="button" data-del="${i}" aria-label="ลบช่อง">ลบ</button>`}</span>
            <textarea class="f-opts opts-in" rows="2" placeholder="ตัวเลือก บรรทัดละ 1 ตัวเลือก"${f.fieldType === 'SELECT' ? '' : ' hidden'}${dis}>${esc(f.options)}</textarea>
         </div>`).join('');
      $$('.f-type').forEach(s => s.onchange = () => { readFields(); renderFields(); });
      $$('[data-del]').forEach(b => b.onclick = () => { readFields(); F.fields.splice(Number(b.dataset.del), 1); renderFields(); });
      $$('[data-up]').forEach(b => b.onclick = () => move(Number(b.dataset.up), -1));
      $$('[data-down]').forEach(b => b.onclick = () => move(Number(b.dataset.down), 1));
   }

   function move(i, dir) {
      readFields();
      const j = i + dir;
      if (j < 0 || j >= F.fields.length) return;
      [F.fields[i], F.fields[j]] = [F.fields[j], F.fields[i]];
      renderFields();
   }

   function readFields() {
      F.fields = $$('#fieldList .frow').map(row => ({
         label: $('.f-label', row).value, fieldType: $('.f-type', row).value,
         required: $('.f-req', row).checked, options: $('.f-opts', row).value
      }));
   }

   /* ตรวจฝั่งหน้าเว็บก่อนส่ง (ข้อความเดียวกับฝั่ง server): คืนข้อความ error หรือ null */
   function validateForm(body, publishNow) {
      if (!body.name.trim()) return 'กรุณาระบุชื่อแบบฟอร์ม';
      if (!body.fields.length) return 'แบบฟอร์มต้องมีอย่างน้อย 1 ช่อง';
      for (let i = 0; i < body.fields.length; i++) {
         const f = body.fields[i];
         if (!f.label.trim()) return 'ช่องที่ ' + (i + 1) + ' ยังไม่มีชื่อช่อง';
         if (f.fieldType === 'SELECT' && !String(f.options || '').trim()) return 'ช่องที่ ' + (i + 1) + ' เป็นตัวเลือก ต้องมีตัวเลือกอย่างน้อย 1 รายการ';
      }
      if (publishNow && (!body.deadline || new Date(body.deadline) <= new Date())) return 'กรุณากำหนดวันหมดเขตที่ยังไม่ผ่านไป ก่อนเปิดใช้งาน';
      return null;
   }

   async function saveForm(publishNow) {
      if (F.busy) return;
      readFields();
      const dl = $('#fDeadline').value;
      const body = {
         name: $('#fName').value, description: $('#fDesc').value.trim() || null,
         deadline: dl ? (dl.length === 16 ? dl + ':00' : dl) : null,
         fields: F.fields.map(f => ({ label: f.label.trim(), fieldType: f.fieldType, required: f.required, options: f.fieldType === 'SELECT' ? f.options.trim() : null }))
      };
      const err = validateForm(body, publishNow);
      if (err) {
         const sp = $('#btnSavePublish');
         sp.dataset.armed = ''; sp.textContent = 'บันทึกและเปิดใช้งาน'; sp.classList.remove('warn');
         return show($('#editErr'), err);
      }
      hide($('#editErr'));
      F.busy = true;
      const r = F.editingId
         ? await api('/api/forms/' + F.editingId, { method: 'PUT', body })
         : await api('/api/forms', { method: 'POST', body });
      if (!r.ok) { F.busy = false; return showErr($('#editErr'), r); }
      if (publishNow) {
         const p = await api('/api/forms/' + r.data.id + '/publish', { method: 'POST' });
         F.busy = false;
         if (!p.ok) return loadForms('บันทึกฉบับร่างแล้ว แต่เปิดใช้งานไม่สำเร็จ: ' + errMsg(p), false);
         return loadForms('เปิดใช้งานแบบฟอร์ม "' + r.data.name + '" แล้ว นักศึกษาใช้ฉบับนี้ยื่นคำร้องได้ทันที');
      }
      F.busy = false;
      loadForms('บันทึกฉบับร่าง "' + r.data.name + '" แล้ว');
   }

   async function publishForm(id) {
      const r = await api('/api/forms/' + id + '/publish', { method: 'POST' });
      if (!r.ok) return loadForms(errMsg(r), false);
      loadForms('เปิดใช้งานแบบฟอร์ม "' + r.data.name + '" แล้ว ฉบับเดิมถูกเลิกใช้');
   }

   /* =====================================================================
      หน้า การแจ้งเตือน (notifications.html) — prototype U5 Student
      ===================================================================== */
   async function initNotifications() {
      $('#vNotif').hidden = false;
      const r = await api('/api/notifications');
      if (!r.ok) return showErr($('#notifErr'), r);
      const list = r.data || [];
      $('#notifList').innerHTML = list.length ? list.map(n => `<div class="notif${n.isRead ? ' read' : ''}" data-nid="${n.id}" tabindex="0">
            <span class="nd" aria-label="${n.isRead ? 'อ่านแล้ว' : 'ยังไม่ได้อ่าน'}"></span><div><b>${esc(n.title)}</b><p>${esc(n.message)}</p></div><time>${fmtDate(n.createdAt)}</time></div>`).join('')
         : '<p class="empty">ยังไม่มีการแจ้งเตือน</p>';
      $$('.notif:not(.read)').forEach(el => el.onclick = async () => {
         const res = await api('/api/notifications/' + el.dataset.nid + '/read', { method: 'PATCH' });
         if (res.ok) { el.classList.add('read'); el.onclick = null; }
      });
   }

   /* ---------- เริ่มทำงาน ---------- */
   let started = false;
   async function start() {
      if (started) return;
      started = true;
      const page = document.body.dataset.page;
      if (!loginName() && !demo()) return location.replace('login.html');   // ยังไม่ login → ไปหน้า login ของทีม
      const who = await resolveUser();
      initShell(page);
      if (!who.ok) return who.status === 401 ? renderPicker() : renderLoginProblem(who);
      if (page === 'review') return initReview();
      if (page === 'forms') return initForms();
      if (page === 'notifications') return initNotifications();
   }
   if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();

   /* เปิดให้ test เรียกใช้ */
   window.U5 = { validateDecision, validateForm, fmtDate, MSG, STATUS_TH, PRESET_REASONS };
})();
