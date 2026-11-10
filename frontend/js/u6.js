/* =====================================================================
   u6.js — U6 ระบบจับคู่และเลือกบริษัท (Matching)
   หน้า: matching.html (นักศึกษา: เลือกตำแหน่ง 1–5 อันดับ · ผลการจับคู่ · ยืนยัน / สละสิทธิ์)
         staff-matching.html (เจ้าหน้าที่: บริษัท / ตำแหน่ง / โควตา · เปิดรอบ · จับคู่ · ประกาศผล)
   แต่ละหน้าบอกตัวเองด้วย <body data-page="matching|staff-matching">
   API ดู .doc/UserStory/U6/T2.md · ผู้ใช้ที่ login มาจาก login.html (U1) เหมือนหน้า U5
   ===================================================================== */
(function () {
   'use strict';

   const $ = (s, r = document) => r.querySelector(s);
   const $$ = (s, r = document) => [...r.querySelectorAll(s)];
   const API = window.U6_API_BASE || window.U5_API_BASE || 'http://localhost:8080';
   const USER_KEY = 'u5-user';      // ใช้ cache เดียวกับหน้า U5
   const TEAM_KEY = 'coop-state';   // state ที่ login.html (U1) ใช้ร่วมกัน
   const MAX_RANKS = 5;

   const ROLE_TH = { STAFF: 'เจ้าหน้าที่', ADVISOR: 'อาจารย์', STUDENT: 'นศ.' };
   const STATUS_TH = {
      PROPOSED: 'รอยืนยัน', CONFIRMED: 'ยืนยันแล้ว', DECLINED: 'สละสิทธิ์', CANCELLED: 'ยกเลิก',
      OPEN: 'เปิดจับคู่', ANNOUNCED: 'ประกาศผลแล้ว', CLOSED: 'ปิดรอบ'
   };
   const RESULT_TH = { PENDING: 'รอประกาศผล', MATCHED: 'ได้อันดับนี้', NOT_SELECTED: 'ไม่ได้อันดับนี้' };
   const MONTHS = ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.'];
   const MSG = {
      network: 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาเปิด backend (docker compose up -d) แล้วลองใหม่',
      needOne: 'กรุณาเลือกตำแหน่งอย่างน้อย 1 อันดับ',
      gap: 'กรุณาเลือกอันดับให้ต่อเนื่อง เริ่มจากอันดับ 1',
      dup: 'เลือกตำแหน่งเดียวกันซ้ำหลายอันดับไม่ได้',
      needChoice: 'กรุณาเลือกยืนยันสิทธิ์หรือสละสิทธิ์',
      needReason: 'กรุณากรอกเหตุผลที่สละสิทธิ์',
      needDeadline: 'กรุณากำหนดวันหมดเขตยืนยันสิทธิ์'
   };

   /* ---------- ตัวช่วย ---------- */
   const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
   const show = (el, msg) => { el.textContent = msg; el.hidden = false; };
   const hide = el => { if (el) el.hidden = true; };
   const badge = s => `<span class="badge ${esc(s)}">${esc(STATUS_TH[s] || RESULT_TH[s] || s)}</span>`;
   function fmtDate(iso) {
      if (!iso) return '-';
      const d = new Date(iso);
      if (isNaN(d)) return '-';
      const p = n => String(n).padStart(2, '0');
      return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear() + 543} ${p(d.getHours())}:${p(d.getMinutes())}`;
   }

   const store = {
      get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } },
      set(k, v) { try { v == null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch (e) { } }
   };
   let user = null;
   try { user = JSON.parse(store.get(USER_KEY) || 'null'); } catch (e) { user = null; }
   function teamState() { try { return JSON.parse(store.get(TEAM_KEY) || '{}') || {}; } catch (e) { return {}; } }
   const loginName = () => teamState().user || null;
   function logout() {
      const st = teamState(); st.user = null; store.set(TEAM_KEY, JSON.stringify(st));
      store.set(USER_KEY, null);
      store.set('u1-auth', null);
      location.href = 'login.html';
   }

   /* เรียก API: คืน {ok, status, data} ถ้าเชื่อมต่อไม่ได้คืน {ok:false, status:0} */
   async function api(path, opts = {}) {
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

   /* หา id / role ของผู้ใช้ที่ login (cache ไว้ใน sessionStorage) */
   async function resolveUser() {
      const name = loginName();
      if (!name) return { ok: false, status: 401 };
      if (user && user.username === name) return { ok: true };
      user = null;
      const r = await api('/api/users/' + encodeURIComponent(name));
      if (!r.ok) return r;
      user = { id: r.data.id, role: r.data.role, name: r.data.name, username: r.data.username };
      store.set(USER_KEY, JSON.stringify(user));
      return { ok: true };
   }

   function initShell(page) {
      $('#who').textContent = user ? ROLE_TH[user.role] + ' ' + user.name : 'ยังไม่ได้เข้าสู่ระบบ';
      $('#logout').onclick = logout;
      let links = [];
      if (user?.role === 'STUDENT') links = [['check.html', 'ตรวจสอบคุณสมบัติ'], ['submit.html', 'ยื่นคำร้อง'], ['ranking.html', 'จับคู่บริษัท'], ['matching.html', 'เลือกตำแหน่ง / ผลจับคู่', 'matching']];
      if (user?.role === 'STAFF') links = [['staff-review.html', 'ตรวจใบสมัคร'], ['staff-forms.html', 'แบบฟอร์มคำร้อง'], ['staff-matching.html', 'จับคู่บริษัท', 'staff-matching']];
      if (user?.role === 'ADVISOR') links = [['staff-review.html', 'พิจารณาใบสมัคร']];
      if (user) links.push(['notifications.html', 'การแจ้งเตือน']);
      $('#u6nav').innerHTML = links.map(([href, label, p]) =>
         `<a href="${href}"${p === page ? ' class="on" aria-current="page"' : ''}>${label}</a>`).join('');
   }

   /* ตรวจอันดับก่อนส่ง: values = id ของแต่ละช่อง (ว่าง = '') · คืน {ok, ids} หรือ {ok:false, msg} */
   function validatePreferences(values) {
      const v = values.map(x => (x == null ? '' : String(x)));
      let last = -1;
      v.forEach((x, i) => { if (x !== '') last = i; });
      if (last < 0) return { ok: false, msg: MSG.needOne };
      const used = v.slice(0, last + 1);
      if (used.some(x => x === '')) return { ok: false, msg: MSG.gap };
      if (new Set(used).size !== used.length) return { ok: false, msg: MSG.dup };
      return { ok: true, ids: used.map(Number) };
   }

   /* =====================================================================
      หน้า นักศึกษา (matching.html)
      ===================================================================== */
   const S = { app: null, positions: [], summary: null, editable: true };

   async function initStudent() {
      if (user.role !== 'STUDENT') return location.replace('staff-matching.html');
      const req = await api('/api/requests/me');
      hide($('#vLoading'));
      if (!req.ok && req.status !== 404) return show($('#err'), errMsg(req));
      const approved = (Array.isArray(req.data) ? req.data : []).filter(a => a.status === 'APPROVED')
         .sort((a, b) => b.id - a.id);
      if (!approved.length) { $('#vNoApp').hidden = false; return; }
      S.app = approved[0];
      await loadPositions();
      await loadSummary();
      await loadPreferences();
      $('#btnSave').onclick = savePreferences;
      $('#btnClear').onclick = () => { $$('#rankList select').forEach(s => { s.value = ''; }); hide($('#prefOk')); };
      $$('input[name=accept]').forEach(r => r.onchange = () => { $('#reasonBox').hidden = r.value !== 'no' || !r.checked; hide($('#respondErr')); });
      $('#btnRespond').onclick = respond;
   }

   /* ตำแหน่งที่เปิดรับของทุกบริษัทที่เปิดรับ */
   async function loadPositions() {
      const r = await api('/api/companies');
      if (!r.ok) return show($('#err'), errMsg(r));
      const details = await Promise.all((r.data || []).map(c => api('/api/companies/' + c.id)));
      S.positions = details.filter(d => d.ok).flatMap(d => (d.data.positions || [])
         .filter(p => p.status === 'OPEN')
         .map(p => ({ ...p, companyName: d.data.name })));
   }

   function optionsHtml(selected) {
      const groups = {};
      S.positions.forEach(p => { (groups[p.companyName] = groups[p.companyName] || []).push(p); });
      return '<option value="">— ไม่เลือก —</option>' + Object.entries(groups).map(([company, list]) =>
         `<optgroup label="${esc(company)}">${list.map(p => `<option value="${p.id}"${String(p.id) === String(selected) ? ' selected' : ''}>${esc(p.positionName)} (ว่าง ${p.availableSlots}/${p.quota})</option>`).join('')}</optgroup>`).join('');
   }

   async function loadPreferences() {
      const r = await api(`/api/applications/${S.app.id}/preferences`);
      const prefs = r.ok ? (r.data || []) : [];
      const m = S.summary?.match;
      S.editable = !(m && (m.status === 'PROPOSED' || m.status === 'CONFIRMED'));
      const rows = [];
      for (let i = 1; i <= MAX_RANKS; i++) {
         const p = prefs.find(x => x.rank === i);
         const res = p && p.resultStatus && p.resultStatus !== 'PENDING' ? `<span class="res">${badge(p.resultStatus)}</span>` : '';
         rows.push(`<li><b>อันดับ ${i}</b><div><select data-rank="${i}" aria-label="อันดับ ${i}"${S.editable ? '' : ' disabled'}>${optionsHtml(p?.positionId)}</select>${res}</div></li>`);
      }
      $('#rankList').innerHTML = rows.join('');
      $('#prefActs').hidden = !S.editable;
      $('#prefSub').textContent = S.editable
         ? 'เลือกได้ 1–5 อันดับ อันดับ 1 คือที่อยากไปที่สุด · ระบบจับคู่ตามอันดับและโควตาของแต่ละตำแหน่ง'
         : 'คุณได้รับการจับคู่แล้ว จึงแก้อันดับไม่ได้';
      $('#vPrefs').hidden = false;
      if (!r.ok) show($('#prefErr'), errMsg(r));
   }

   async function savePreferences() {
      hide($('#prefErr')); hide($('#prefOk'));
      const check = validatePreferences($$('#rankList select').map(s => s.value));
      if (!check.ok) return show($('#prefErr'), check.msg);
      $('#btnSave').disabled = true;
      const r = await api(`/api/applications/${S.app.id}/preferences`, { method: 'PUT', body: { positionIds: check.ids } });
      $('#btnSave').disabled = false;
      if (!r.ok) return show($('#prefErr'), errMsg(r));
      show($('#prefOk'), `บันทึก ${r.data.length} อันดับแล้ว`);
   }

   async function loadSummary() {
      const r = await api('/api/matches/me/summary');
      S.summary = r.ok ? r.data : null;
      if (!r.ok) { $('#vResult').hidden = true; if (r.status !== 404) show($('#err'), errMsg(r)); return; }
      const s = r.data;
      $('#roundInfo').innerHTML = `รอบที่ ${esc(s.round.roundNo)} ${badge(s.round.status)} · ยืนยันสิทธิ์ภายใน ${fmtDate(s.round.confirmDeadline)}`;
      $('#summaryText').textContent = s.summary;
      const m = s.match;
      if (m) {
         $('#matchInfo').innerHTML = `<div><dt>บริษัท</dt><dd>${esc(m.companyName)}</dd></div>
            <div><dt>ตำแหน่ง</dt><dd>${esc(m.positionName)}</dd></div>
            <div><dt>อันดับที่ได้</dt><dd>${m.rank ? esc(m.rank) : 'เจ้าหน้าที่จับคู่ให้'}</dd></div>
            <div><dt>สถานะ</dt><dd>${badge(m.status)}</dd></div>
            <div><dt>โควตาตำแหน่ง</dt><dd>${esc(s.quota ?? '-')} คน</dd></div>`;
         $('#matchInfo').hidden = false;
      } else {
         $('#matchInfo').hidden = true;
      }
      $('#respondBox').hidden = !(m && m.status === 'PROPOSED' && s.round.status === 'ANNOUNCED');
      $('#vResult').hidden = false;
   }

   async function respond() {
      hide($('#respondErr'));
      const pick = $('input[name=accept]:checked')?.value;
      if (!pick) return show($('#respondErr'), MSG.needChoice);
      const reason = $('#reason').value.trim();
      if (pick === 'no' && !reason) return show($('#respondErr'), MSG.needReason);
      $('#btnRespond').disabled = true;
      const r = await api(`/api/matches/${S.summary.match.id}/respond`, { method: 'POST', body: { accept: pick === 'yes', reason: pick === 'no' ? reason : null } });
      $('#btnRespond').disabled = false;
      if (!r.ok) return show($('#respondErr'), errMsg(r));
      await loadSummary();
      await loadPreferences();
      show($('#respondOk'), pick === 'yes' ? 'ยืนยันสิทธิ์เรียบร้อยแล้ว' : 'สละสิทธิ์เรียบร้อยแล้ว คุณแก้อันดับเพื่อรอรอบถัดไปได้');
   }

   /* =====================================================================
      หน้า เจ้าหน้าที่ (staff-matching.html)
      ===================================================================== */
   const T = { round: null };

   async function initStaff() {
      hide($('#vLoading'));
      if (user.role !== 'STAFF') { $('#vDenied').hidden = false; return; }
      $('#vStaff').hidden = false;
      $('#btnAuto').onclick = autoMatch;
      $('#btnAnnounce').onclick = announce;
      $('#openForm').onsubmit = e => { e.preventDefault(); openRound(); };
      $('#companyForm').onsubmit = e => { e.preventDefault(); addCompany(); };
      await loadRound();
      await loadCompanies();
   }

   const note = (text, bad = false) => {
      const el = $('#msg');
      el.className = 'alert ' + (bad ? 'bad' : 'ok');
      show(el, text);
   };

   async function loadRound() {
      const r = await api('/api/matching/rounds/current');
      T.round = r.ok ? r.data : null;
      if (!r.ok && r.status !== 404) note(errMsg(r), true);
      const R = T.round;
      $('#roundText').innerHTML = R
         ? `รอบที่ ${esc(R.roundNo)} ${badge(R.status)} · จับคู่แล้ว ${esc(R.matchedCount)} คน · หมดเขตยืนยันสิทธิ์ ${fmtDate(R.confirmDeadline)}`
         : 'ยังไม่มีรอบการจับคู่ เปิดรอบแรกได้ด้านล่าง';
      $('#btnAuto').hidden = !(R && R.status === 'OPEN');
      $('#btnAnnounce').hidden = !(R && R.status === 'OPEN');
      await loadMatches();
   }

   async function loadMatches() {
      const tb = $('#matchRows');
      if (!T.round) { tb.innerHTML = '<tr><td colspan="6" class="empty">ยังไม่มีผลการจับคู่</td></tr>'; return; }
      const r = await api('/api/matches?roundId=' + T.round.id);
      if (!r.ok) { tb.innerHTML = ''; return note(errMsg(r), true); }
      const list = r.data || [];
      tb.innerHTML = list.length ? list.map(m => `<tr>
            <td>${esc(m.studentName)}<br><small class="mute">${esc(m.studentId)}</small></td>
            <td>${esc(m.positionName)}<br><small class="mute">${esc(m.companyName)}</small></td>
            <td>${m.rank ? esc(m.rank) : '-'}</td><td>${esc(m.roundNo)}</td><td>${badge(m.status)}</td>
            <td>${m.status === 'PROPOSED' || m.status === 'CONFIRMED' ? `<button class="btn ghost sm" type="button" data-cancel="${m.id}">ยกเลิก</button>` : ''}</td></tr>`).join('')
         : '<tr><td colspan="6" class="empty">ยังไม่มีผลการจับคู่ในรอบนี้</td></tr>';
      $$('[data-cancel]', tb).forEach(b => b.onclick = () => cancelMatch(b.dataset.cancel));
   }

   async function openRound() {
      const v = $('#deadline').value;
      if (!v) return note(MSG.needDeadline, true);
      const r = await api('/api/matching/rounds', { method: 'POST', body: { confirmDeadline: v.length === 16 ? v + ':00' : v } });
      if (!r.ok) return note(errMsg(r), true);
      note(`เปิดรอบที่ ${r.data.roundNo} แล้ว`);
      await loadRound();
   }

   async function autoMatch() {
      $('#btnAuto').disabled = true;
      const r = await api('/api/matching/auto', { method: 'POST' });
      $('#btnAuto').disabled = false;
      if (!r.ok) return note(errMsg(r), true);
      const box = $('#autoResult');
      box.innerHTML = `<p>จับคู่ได้ ${r.data.matched.length} คน · จับไม่ได้ ${r.data.unmatched.length} คน</p>` +
         (r.data.unmatched.length ? `<ul class="unmatched">${r.data.unmatched.map(u => `<li>${esc(u.studentName)}: ${esc(u.reason)}</li>`).join('')}</ul>` : '');
      box.hidden = false;
      note('จับคู่อัตโนมัติเสร็จแล้ว');
      await loadRound();
      await loadCompanies();
   }

   async function announce() {
      if (!T.round) return;
      const r = await api(`/api/matching/rounds/${T.round.id}/announce`, { method: 'POST' });
      if (!r.ok) return note(errMsg(r), true);
      note(`ประกาศผลรอบที่ ${r.data.roundNo} แล้ว ระบบแจ้งเตือนนักศึกษาในรอบนี้แล้ว`);
      await loadRound();
   }

   async function cancelMatch(id) {
      const r = await api('/api/matches/' + id, { method: 'DELETE' });
      if (!r.ok) return note(errMsg(r), true);
      note('ยกเลิกผลการจับคู่แล้ว คืนโควตาให้ตำแหน่งแล้ว');
      await loadRound();
      await loadCompanies();
   }

   async function loadCompanies() {
      const r = await api('/api/companies');
      if (!r.ok) return note(errMsg(r), true);
      const details = await Promise.all((r.data || []).map(c => api('/api/companies/' + c.id)));
      $('#companyList').innerHTML = details.filter(d => d.ok).map(({ data: c }) => `<div class="company" data-company="${c.id}">
            <h3>${esc(c.name)} <small class="mute">${esc(c.address || '')}</small></h3>
            ${(c.positions || []).map(p => `<div class="pos" data-pos="${p.id}">
               <span>${esc(p.positionName)} ${badge(p.status)}</span>
               <small class="mute">ว่าง ${esc(p.availableSlots)}/</small>
               <input type="number" min="1" value="${esc(p.quota)}" aria-label="โควตา ${esc(p.positionName)}" data-quota="${p.id}">
               <button class="btn ghost sm" type="button" data-save="${p.id}">บันทึกโควตา</button>
               <button class="btn ghost sm" type="button" data-cand="${p.id}">ดูผู้เลือก</button></div>`).join('') || '<p class="mute">ยังไม่มีตำแหน่ง</p>'}
            <form class="inline-form" data-addpos="${c.id}">
               <input placeholder="ชื่อตำแหน่งใหม่" data-pname><input type="number" min="1" placeholder="โควตา" data-pquota>
               <button class="btn ghost sm" type="submit">เพิ่มตำแหน่ง</button></form></div>`).join('');
      const positions = details.filter(d => d.ok).flatMap(d => d.data.positions || []);
      $$('[data-save]').forEach(b => b.onclick = () => saveQuota(positions.find(p => String(p.id) === b.dataset.save)));
      $$('[data-cand]').forEach(b => b.onclick = () => loadCandidates(positions.find(p => String(p.id) === b.dataset.cand)));
      $$('[data-addpos]').forEach(f => f.onsubmit = e => { e.preventDefault(); addPosition(f); });
   }

   async function saveQuota(p) {
      const quota = Number($(`[data-quota="${p.id}"]`).value);
      const r = await api('/api/positions/' + p.id, { method: 'PUT', body: { positionName: p.positionName, description: p.description, quota, status: p.status } });
      if (!r.ok) return note(errMsg(r), true);
      note(`บันทึกโควตา ${p.positionName} เป็น ${r.data.quota} แล้ว`);
      await loadCompanies();
   }

   async function addPosition(form) {
      const name = $('[data-pname]', form).value.trim();
      const quota = Number($('[data-pquota]', form).value);
      const r = await api(`/api/companies/${form.dataset.addpos}/positions`, { method: 'POST', body: { positionName: name, quota } });
      if (!r.ok) return note(errMsg(r), true);
      note(`เพิ่มตำแหน่ง ${r.data.positionName} แล้ว`);
      await loadCompanies();
   }

   async function addCompany() {
      const name = $('#newCompany').value.trim();
      const r = await api('/api/companies', { method: 'POST', body: { name, address: $('#newAddress').value.trim() } });
      if (!r.ok) return note(errMsg(r), true);
      $('#newCompany').value = ''; $('#newAddress').value = '';
      note(`เพิ่มบริษัท ${r.data.name} แล้ว`);
      await loadCompanies();
   }

   async function loadCandidates(p) {
      const r = await api('/api/matching/candidates?positionId=' + p.id);
      if (!r.ok) return note(errMsg(r), true);
      $('#candTitle').textContent = `นักศึกษาที่เลือก ${p.positionName}`;
      const list = r.data || [];
      $('#candRows').innerHTML = list.length ? list.map(c => `<tr><td>${esc(c.studentName)}<br><small class="mute">${esc(c.studentId)}</small></td>
            <td>${esc(c.rank)}</td><td>${c.alreadyMatched ? badge('MATCHED') : '<span class="mute">ยังไม่มีผล</span>'}</td>
            <td>${c.alreadyMatched ? '' : `<button class="btn sm" type="button" data-match="${c.applicationId}">จับคู่กับตำแหน่งนี้</button>`}</td></tr>`).join('')
         : '<tr><td colspan="4" class="empty">ยังไม่มีนักศึกษาเลือกตำแหน่งนี้</td></tr>';
      $$('[data-match]').forEach(b => b.onclick = () => manualMatch(Number(b.dataset.match), p));
      $('#candidateCard').hidden = false;
   }

   async function manualMatch(applicationId, p) {
      const r = await api('/api/matches', { method: 'POST', body: { applicationId, positionId: p.id } });
      if (!r.ok) return note(errMsg(r), true);
      note(`จับคู่ ${r.data.studentName} กับ ${r.data.positionName} แล้ว`);
      await loadRound();
      await loadCompanies();
      await loadCandidates(p);
   }

   /* ---------- เริ่มทำงาน ---------- */
   let started = false;
   async function start() {
      if (started) return;
      started = true;
      const page = document.body.dataset.page;
      if (!loginName()) return location.replace('login.html');
      const who = await resolveUser();
      if (!who.ok) {
         hide($('#vLoading'));
         initShell(page);
         return show($('#err'), who.status === 0 ? MSG.network : 'ไม่พบบัญชีผู้ใช้นี้ กรุณาเข้าสู่ระบบใหม่');
      }
      initShell(page);
      if (page === 'matching') return initStudent();
      if (page === 'staff-matching') return initStaff();
   }
   if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();

   /* เปิดให้ test เรียกใช้ */
   window.U6 = { validatePreferences, fmtDate, MSG, STATUS_TH, RESULT_TH };
})();
