/* =====================================================================
   u5-demo.js — API จำลองสำหรับ "โหมดข้อมูลตัวอย่าง" ของหน้า U5
   ใช้ดูหน้าเว็บโดยไม่ต้องเปิด backend: เปิดหน้าใดก็ได้ต่อท้าย ?demo=1 (ปิดด้วย ?demo=0)
   กติกาเหมือน backend จริง (ดู ApprovalService / FormTemplateService) ข้อมูลเก็บใน sessionStorage ของแท็บนี้
   ===================================================================== */
(function () {
   'use strict';
   const KEY = 'u5-demo-db-v2';
   const USERS = { 1: ['STAFF', 'สมศรี ทำงานดี'], 2: ['ADVISOR', 'อ.ดร.วิชัย สอนดี'], 3: ['STUDENT', 'สมชาย ใจดี'], 4: ['STUDENT', 'สมหญิง รักเรียน'] };
   const LOGINS = { 1: 'staff01', 2: 'advisor01', 3: '6609650001', 4: '6609650002' };   // username ตาม DemoDataSeeder
   const pad = n => String(n).padStart(2, '0');
   const now = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`; };
   const daysAgo = (n, h, m) => { const d = new Date(); d.setDate(d.getDate() - n); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(h)}:${pad(m)}:00`; };
   const daysAhead = n => { const d = new Date(); d.setDate(d.getDate() + n); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T23:59:00`; };

   function field(id, order, label, type) { return { id, fieldName: 'field_' + order, label, fieldType: type, required: true, displayOrder: order, options: null }; }
   function app(id, userId, code, name, email, gpa, status, submittedAt, approvals) {
      return {
         id, userId, studentId: code, studentName: name, email, faculty: 'วิทยาศาสตร์และเทคโนโลยี', major: 'วิทยาการคอมพิวเตอร์', gpa,
         formName: 'คำร้องขอฝึกสหกิจศึกษา 2/2569', status, submittedAt,
         answers: [{ fieldId: 2, label: 'ชื่อ-นามสกุล', value: name }, { fieldId: 3, label: 'เบอร์โทรศัพท์', value: '08' + String(id).padStart(8, '1') },
         { fieldId: 4, label: 'อีเมล', value: email }, { fieldId: 5, label: 'ที่อยู่ปัจจุบัน', value: '99 ม.ธรรมศาสตร์ ศูนย์รังสิต' }, { fieldId: 6, label: 'Transcript', value: 'transcript.pdf' }],
         documents: [{ id: id + 100, fileName: 'transcript.pdf', status: 'UPLOADED' }], approvals: approvals || []
      };
   }
   function seed() {
      const staffOk = (appId, at) => [{ id: appId + 900, applicationId: appId, approvalLevel: 'STAFF', decision: 'APPROVE', approverName: 'สมศรี ทำงานดี', comment: null, decidedAt: at, applicationStatus: 'UNDER_REVIEW' }];
      return {
         seq: 1000,
         forms: [
            { id: 10, name: 'คำร้องขอฝึกสหกิจศึกษา 1/2569', description: null, version: 1, status: 'ARCHIVED', deadline: daysAgo(120, 23, 59), createdAt: daysAgo(160, 9, 0), publishedAt: daysAgo(150, 9, 0), fields: [field(1, 1, 'ชื่อ-นามสกุล', 'TEXT')] },
            { id: 11, name: 'คำร้องขอฝึกสหกิจศึกษา 2/2569', description: 'กรอกข้อมูลให้ครบและแนบ Transcript (PDF)', version: 2, status: 'PUBLISHED', deadline: daysAhead(30), createdAt: daysAgo(20, 9, 0), publishedAt: daysAgo(20, 10, 0),
              fields: [field(2, 1, 'ชื่อ-นามสกุล', 'TEXT'), field(3, 2, 'เบอร์โทรศัพท์', 'PHONE'), field(4, 3, 'อีเมล', 'EMAIL'), field(5, 4, 'ที่อยู่ปัจจุบัน', 'TEXTAREA'), field(6, 5, 'Transcript', 'FILE')] }
         ],
         apps: [
            app(50, 3, '6609650001', 'สมชาย ใจดี', 'somchai@dome.tu.ac.th', 3.10, 'SUBMITTED', daysAgo(2, 10, 24)),
            app(51, 4, '6609650002', 'สมหญิง รักเรียน', 'somying@dome.tu.ac.th', 3.45, 'SUBMITTED', daysAgo(2, 14, 2)),
            app(52, 5, '6609650003', 'มานะ ขยันดี', 'mana@dome.tu.ac.th', 2.98, 'SUBMITTED', daysAgo(1, 9, 15), [{ id: 952, applicationId: 52, approvalLevel: 'STAFF', decision: 'RETURN', approverName: 'สมศรี ทำงานดี', comment: 'เบอร์โทรศัพท์ไม่ถูกต้อง\nช่องที่ต้องแก้: เบอร์โทรศัพท์', decidedAt: daysAgo(2, 15, 0), applicationStatus: 'NEED_CORRECTION' }]),
            app(55, 8, '6609650006', 'วีระ ส่งก่อน', 'weera@dome.tu.ac.th', 3.05, 'SUBMITTED', daysAgo(5, 8, 40)),
            app(53, 6, '6609650004', 'นภา ตั้งใจ', 'napa@dome.tu.ac.th', 3.62, 'UNDER_REVIEW', daysAgo(4, 11, 0), staffOk(53, daysAgo(1, 11, 0))),
            app(54, 7, '6609650005', 'ปิติ มีสุข', 'piti@dome.tu.ac.th', 3.20, 'UNDER_REVIEW', daysAgo(4, 13, 30), staffOk(54, daysAgo(1, 13, 30)))
         ],
         notifs: [
            { id: 3, userId: 3, title: 'ใบสมัครได้รับการอนุมัติ', message: 'อาจารย์อนุมัติใบสมัครฝึกสหกิจศึกษาของคุณแล้ว ขั้นตอนต่อไปคือจับคู่บริษัท', applicationId: 50, isRead: false, createdAt: daysAgo(0, 9, 10) },
            { id: 4, userId: 3, title: 'เจ้าหน้าที่ตรวจใบสมัครแล้ว', message: 'ใบสมัครของคุณถูกส่งต่อให้อาจารย์พิจารณา', applicationId: 50, isRead: true, createdAt: daysAgo(1, 11, 0) },
            { id: 2, userId: 3, title: 'ยื่นใบสมัครสำเร็จ', message: 'ระบบได้รับใบสมัครของคุณแล้ว รอเจ้าหน้าที่ตรวจสอบ', applicationId: 50, isRead: true, createdAt: daysAgo(2, 10, 24) }
         ]
      };
   }
   /* คำร้อง 52 ถูกตีกลับเรื่องเบอร์โทรแล้วส่งใหม่: แสดงค่าเดิมให้เทียบ */
   function withResubmission(db) {
      const a = db.apps.find(x => x.id === 52);
      if (a && !a.answers[1].previousValue) a.answers[1].previousValue = '0812';
      return db;
   }
   function load() { try { return JSON.parse(sessionStorage.getItem(KEY)) || withResubmission(seed()); } catch (e) { return withResubmission(seed()); } }
   function save(db) { try { sessionStorage.setItem(KEY, JSON.stringify(db)); } catch (e) { } }
   const ok = (data, status = 200) => ({ ok: true, status, data });
   const err = (status, code, message) => ({ ok: false, status, data: { code, message } });
   const clone = o => JSON.parse(JSON.stringify(o));

   /* ป้ายเตือนแบบเดียวกับ server: รอตรวจ ≥ 3 วัน → OVERDUE */
   function attention(a) {
      if (!['SUBMITTED', 'UNDER_REVIEW'].includes(a.status)) return null;
      return (Date.now() - new Date(a.submittedAt).getTime()) >= 3 * 86400000 ? 'OVERDUE' : null;
   }

   function notify(db, userId, title, message, applicationId) {
      db.notifs.push({ id: ++db.seq, userId, title, message, applicationId, isRead: false, createdAt: now() });
   }

   function handle(method, path, body, uid) {
      const db = load();
      let m;
      /* หา id/role จาก username ที่ login ไว้ (ไม่ต้องมี X-User-Id) */
      if (method === 'GET' && (m = path.match(/^\/api\/users\/([^/?]+)$/))) {
         const name = decodeURIComponent(m[1]).trim();
         const id = Object.keys(LOGINS).find(k => LOGINS[k] === name);
         return Promise.resolve(id ? ok({ id: +id, username: name, name: USERS[id][1], role: USERS[id][0] }) : err(404, 'NOT_FOUND', 'ไม่พบบัญชีผู้ใช้ ' + name + ' ในระบบ'));
      }
      const role = (USERS[uid] || [])[0];
      if (!role) return err(401, 'UNAUTHORIZED', 'กรุณาเข้าสู่ระบบก่อนใช้งาน');
      const staffish = role === 'STAFF' || role === 'ADVISOR';
      /* บันทึกผลการพิจารณา 1 คำร้อง (ใช้ทั้งพิจารณาทีละรายการและอนุมัติหลายรายการ) */
      function decideOne(id, body) {
         if (!staffish) return err(403, 'FORBIDDEN', 'คุณไม่มีสิทธิ์ใช้งานส่วนนี้');
         const a = db.apps.find(x => x.id === +id);
         if (!a) return err(404, 'NOT_FOUND', 'ไม่พบคำร้อง');
         const d = body && body.decision;
         let c = ((body && body.comment) || '').trim() || null;
         if (!['APPROVE', 'RETURN', 'REJECT'].includes(d)) return err(400, 'VALIDATION_ERROR', 'กรุณาเลือกผลการพิจารณา');
         if (d !== 'APPROVE' && !c) return err(400, 'VALIDATION_ERROR', 'กรุณากรอกเหตุผลเมื่อส่งกลับแก้ไขหรือปฏิเสธ');
         const level = role === 'STAFF' ? 'STAFF' : 'ADVISOR';
         if (a.status !== (level === 'STAFF' ? 'SUBMITTED' : 'UNDER_REVIEW')) return err(409, 'INVALID_STATUS', `คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: ${a.status})`);
         const fix = d === 'RETURN' && body.fieldIds ? a.answers.filter(x => body.fieldIds.includes(x.fieldId)).map(x => x.label) : [];
         if (fix.length) c += '\nช่องที่ต้องแก้: ' + fix.join(', ');
         const next = { APPROVE: level === 'STAFF' ? 'UNDER_REVIEW' : 'APPROVED', RETURN: 'NEED_CORRECTION', REJECT: 'REJECTED' }[d];
         const ap = { id: ++db.seq, applicationId: a.id, approvalLevel: level, decision: d, approverName: USERS[uid][1], comment: c, decidedAt: now(), applicationStatus: next };
         a.status = next;
         a.approvals.push(ap);
         const reason = c ? ' เหตุผล: ' + c : '';
         if (d === 'APPROVE' && level === 'STAFF') {
            notify(db, 2, 'มีใบสมัครรอพิจารณา', `ใบสมัครเลขที่ ${a.id} ผ่านการตรวจของเจ้าหน้าที่แล้ว รออาจารย์พิจารณา`, a.id);
            notify(db, a.userId, 'เจ้าหน้าที่ตรวจใบสมัครแล้ว', 'ใบสมัครของคุณถูกส่งต่อให้อาจารย์พิจารณา', a.id);
         } else if (d === 'APPROVE') notify(db, a.userId, 'ใบสมัครได้รับการอนุมัติ', 'อาจารย์อนุมัติใบสมัครฝึกสหกิจศึกษาของคุณแล้ว ขั้นตอนต่อไปคือจับคู่บริษัท', a.id);
         else if (d === 'RETURN') notify(db, a.userId, 'ใบสมัครถูกส่งกลับให้แก้ไข', 'กรุณาแก้ไขใบสมัครแล้วส่งใหม่ก่อนหมดเขต.' + reason, a.id);
         else notify(db, a.userId, 'ใบสมัครไม่ได้รับการอนุมัติ', 'ใบสมัครฝึกสหกิจศึกษาของคุณไม่ได้รับการอนุมัติ.' + reason, a.id);
         return ok(ap);
      }
      const res = (() => {
         if (method === 'GET' && (m = path.match(/^\/api\/applications\?status=(\w+)$/))) {
            if (!staffish) return err(403, 'FORBIDDEN', 'คุณไม่มีสิทธิ์ใช้งานส่วนนี้');
            return ok(db.apps.filter(a => a.status === m[1]).sort((a, b) => a.submittedAt.localeCompare(b.submittedAt)).map(a => {
               const last = a.approvals[a.approvals.length - 1];
               return { id: a.id, studentId: a.studentId, studentName: a.studentName, status: a.status, submittedAt: a.submittedAt, documents: a.documents,
                  reviewedBy: last ? last.approverName : null, reviewedAt: last ? last.decidedAt : null, attention: attention(a) };
            }).sort((x, y) => (x.attention ? 0 : 1) - (y.attention ? 0 : 1)));
         }
         if (method === 'GET' && path === '/api/applications/summary') {
            if (!staffish) return err(403, 'FORBIDDEN', 'คุณไม่มีสิทธิ์ใช้งานส่วนนี้');
            const counts = {};
            ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEED_CORRECTION', 'APPROVED', 'REJECTED'].forEach(st => { counts[st] = db.apps.filter(a => a.status === st).length; });
            return ok(counts);
         }
         if (method === 'POST' && path === '/api/approvals/bulk') {
            const ids = [...new Set((body && body.applicationIds) || [])];
            if (!ids.length) return err(400, 'VALIDATION_ERROR', 'กรุณาเลือกคำร้องอย่างน้อย 1 รายการ');
            const approved = [], skipped = [];
            ids.forEach(id => {
               const r = decideOne(id, { decision: 'APPROVE', comment: null });
               if (r.ok) approved.push(id); else skipped.push({ applicationId: id, reason: r.data.message });
            });
            return ok({ approved, skipped });
         }
         if (method === 'GET' && (m = path.match(/^\/api\/applications\/(\d+)\/detail$/))) {
            const a = db.apps.find(x => x.id === +m[1]);
            return a ? ok(clone(a)) : err(404, 'NOT_FOUND', 'ไม่พบคำร้อง');
         }
         if (method === 'POST' && (m = path.match(/^\/api\/approvals\/(\d+)\/decision$/))) return decideOne(m[1], body);
         if (path.startsWith('/api/forms') && role !== 'STAFF') return err(403, 'FORBIDDEN', 'คุณไม่มีสิทธิ์ใช้งานส่วนนี้');
         if (method === 'GET' && path === '/api/forms') return ok(clone(db.forms).sort((a, b) => b.version - a.version));
         const nextVersion = () => Math.max(0, ...db.forms.map(f => f.version)) + 1;
         const mkFields = list => list.map((f, i) => ({ id: ++db.seq, fieldName: 'field_' + (i + 1), label: f.label, fieldType: f.fieldType, required: !!f.required, displayOrder: i + 1, options: f.options || null }));
         const validate = b => {
            if (!b || !String(b.name || '').trim()) return 'กรุณาระบุชื่อแบบฟอร์ม';
            if (!b.fields || !b.fields.length) return 'แบบฟอร์มต้องมีอย่างน้อย 1 ช่อง';
            const seen = new Set();
            for (const f of b.fields) {
               const k = String(f.label || '').trim().toLowerCase();
               if (seen.has(k)) return `ชื่อช่อง "${f.label.trim()}" ซ้ำกับช่องอื่น`;
               seen.add(k);
            }
            return null;
         };
         if (method === 'POST' && path === '/api/forms') {
            const v = validate(body);
            if (v) return err(400, 'VALIDATION_ERROR', v);
            const f = { id: ++db.seq, name: body.name.trim(), description: body.description, version: nextVersion(), status: 'DRAFT', deadline: body.deadline, createdAt: now(), publishedAt: null, fields: mkFields(body.fields) };
            db.forms.push(f);
            return ok(clone(f), 201);
         }
         if ((m = path.match(/^\/api\/forms\/(\d+)(\/new-version|\/publish|\/close)?$/))) {
            const f = db.forms.find(x => x.id === +m[1]);
            if (!f) return err(404, 'NOT_FOUND', 'ไม่พบแบบฟอร์ม');
            if (method === 'GET' && !m[2]) return ok(clone(f));
            if (method === 'PUT' && !m[2]) {
               if (f.status !== 'DRAFT') return err(409, 'INVALID_STATUS', 'แก้ไขได้เฉพาะแบบฟอร์มที่ยังเป็นฉบับร่าง');
               const v = validate(body);
               if (v) return err(400, 'VALIDATION_ERROR', v);
               Object.assign(f, { name: body.name.trim(), description: body.description, deadline: body.deadline, fields: mkFields(body.fields) });
               return ok(clone(f));
            }
            if (method === 'POST' && m[2] === '/new-version') {
               const c = { ...clone(f), id: ++db.seq, version: nextVersion(), status: 'DRAFT', deadline: null, createdAt: now(), publishedAt: null };
               db.forms.push(c);
               return ok(clone(c), 201);
            }
            if (method === 'POST' && m[2] === '/close') {
               if (f.status !== 'PUBLISHED') return err(409, 'CONFLICT', 'ปิดรับได้เฉพาะแบบฟอร์มที่เปิดใช้งานอยู่');
               if (f.deadline && new Date(f.deadline) <= new Date()) return err(409, 'CONFLICT', 'แบบฟอร์มนี้หมดเขตรับคำร้องไปแล้ว');
               f.deadline = now();
               return ok(clone(f));
            }
            if (method === 'POST' && m[2] === '/publish') {
               if (f.status !== 'DRAFT') return err(409, 'INVALID_STATUS', 'แบบฟอร์มนี้เปิดใช้งานหรือเลิกใช้ไปแล้ว');
               if (!f.deadline || new Date(f.deadline) <= new Date()) return err(400, 'VALIDATION_ERROR', 'กรุณากำหนดวันหมดเขตที่ยังไม่ผ่านไป ก่อนเปิดใช้งาน');
               db.forms.forEach(x => { if (x.status === 'PUBLISHED') x.status = 'ARCHIVED'; });
               f.status = 'PUBLISHED'; f.publishedAt = now();
               return ok(clone(f));
            }
         }
         if (method === 'GET' && path === '/api/notifications')
            return ok(db.notifs.filter(n => n.userId === uid).sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id).map(clone));
         if (method === 'PATCH' && (m = path.match(/^\/api\/notifications\/(\d+)\/read$/))) {
            const n = db.notifs.find(x => x.id === +m[1]);
            if (!n) return err(404, 'NOT_FOUND', 'ไม่พบการแจ้งเตือน');
            if (n.userId !== uid) return err(403, 'FORBIDDEN', 'คุณไม่มีสิทธิ์ใช้งานส่วนนี้');
            n.isRead = true;
            return ok(clone(n));
         }
         return err(404, 'NOT_FOUND', 'ไม่พบ ' + path);
      })();
      save(db);
      return Promise.resolve(res);
   }

   window.U5Demo = { handle, reset: () => { try { sessionStorage.removeItem(KEY); } catch (e) { } } };
})();
