// @vitest-environment node
// U5:T7 Unit test หน้า UI (staff-review.html, staff-forms.html, notifications.html)
// รัน: npm install (ครั้งแรก) แล้ว npx vitest run frontend/tests/u5.test.js
import { describe, test, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const read = f => fs.readFileSync(path.join(testDir, '..', f), 'utf8');
const script = read('js/u5.js');

const USERS = {
    staff: { id: 1, role: 'STAFF', name: 'สมศรี ทำงานดี', username: 'staff01' },
    advisor: { id: 2, role: 'ADVISOR', name: 'อ.ดร.วิชัย สอนดี', username: 'advisor01' },
    student: { id: 3, role: 'STUDENT', name: 'สมชาย ใจดี', username: '6609650001' }
};

const APP = {
    id: 50, studentId: '6609650001', studentName: 'สมชาย ใจดี', email: 'somchai@dome.tu.ac.th',
    faculty: 'วิทยาศาสตร์และเทคโนโลยี', major: 'วิทยาการคอมพิวเตอร์', gpa: 3.1,
    formName: 'คำร้องขอฝึกสหกิจศึกษา 2/2569', status: 'SUBMITTED', submittedAt: '2026-10-15T10:24:00',
    answers: [{ label: 'ชื่อ-นามสกุล', value: 'สมชาย ใจดี' }, { label: 'เบอร์โทรศัพท์', value: '0812345678' }],
    documents: [{ id: 7, fileName: 'transcript.pdf', status: 'UPLOADED' }], approvals: []
};

/* API จำลอง: routes = { 'GET /api/...': [status, body] } เก็บทุก request ไว้ใน calls */
function fakeFetch(routes, calls) {
    return async (url, opts = {}) => {
        const method = opts.method || 'GET';
        const p = url.replace(/^https?:\/\/[^/]+/, '');
        calls.push({ method, path: p, body: opts.body ? JSON.parse(opts.body) : null, headers: opts.headers });
        const hit = routes[method + ' ' + p];
        if (hit === 'DOWN') throw new TypeError('Failed to fetch');
        const [status, body] = hit || [404, { code: 'NOT_FOUND', message: 'ไม่พบ' }];
        return { ok: status < 400, status, json: async () => body };
    };
}

const flush = () => new Promise(r => setTimeout(r, 20));

/* user = บัญชีที่ login แล้ว (cache ไว้แล้ว) · login = username ที่ login จาก login.html แต่ยังไม่ได้หา id */
async function open(file, user, routes = {}, login = null) {
    const dom = new JSDOM(read(file), { url: 'http://localhost/' + file, runScripts: 'outside-only' });
    const { window } = dom;
    const calls = [];
    const name = login || user?.username;
    if (name) window.sessionStorage.setItem('coop-state', JSON.stringify({ user: name, unlocked: 3 }));
    if (user) window.sessionStorage.setItem('u5-user', JSON.stringify(user));
    window.fetch = fakeFetch(routes, calls);
    window.eval(script);
    window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
    await flush();
    return { window, doc: window.document, calls };
}

const listRoutes = {
    'GET /api/applications?status=SUBMITTED': [200, [APP]],
    'GET /api/applications/50/detail': [200, APP],
    'POST /api/approvals/50/decision': [200, { id: 900, applicationId: 50, approvalLevel: 'STAFF', decision: 'APPROVE', approverName: 'สมศรี ทำงานดี', comment: null, decidedAt: '2026-10-17T10:00:00', applicationStatus: 'UNDER_REVIEW' }]
};

async function openDetailAsStaff(routes = listRoutes) {
    const page = await open('staff-review.html', USERS.staff, routes);
    page.doc.querySelector('[data-open="50"]').click();
    await flush();
    return page;
}

describe('U5:T7 หน้าตรวจคำร้อง (staff-review.html)', () => {

    test('UI-01 เจ้าหน้าที่เปิดหน้า เรียก status=SUBMITTED และส่ง X-User-Id', async () => {
        const { doc, calls } = await open('staff-review.html', USERS.staff, listRoutes);
        expect(calls[0].path).toBe('/api/applications?status=SUBMITTED');
        expect(calls[0].headers['X-User-Id']).toBe('1');
        expect(doc.querySelectorAll('#appRows tr').length).toBe(1);
        expect(doc.querySelector('#appRows').textContent).toContain('สมชาย ใจดี');
    });

    test('UI-03 ไม่มีคำร้องในสถานะนั้น แสดงข้อความว่าง', async () => {
        const { doc } = await open('staff-review.html', USERS.staff, { 'GET /api/applications?status=SUBMITTED': [200, []] });
        expect(doc.querySelector('#emptyMsg').hidden).toBe(false);
    });

    test('UI-04 กดตรวจ แสดงคำตอบในแบบฟอร์มและเอกสารแนบ', async () => {
        const { doc } = await openDetailAsStaff();
        expect(doc.querySelector('#vDetail').hidden).toBe(false);
        expect(doc.querySelectorAll('#dAnswers > div').length).toBe(2);
        expect(doc.querySelector('#dDocs').textContent).toContain('transcript.pdf');
    });

    test('UI-06 ค่าเริ่มต้นเป็นอนุมัติ (ตาม prototype) และถ้าไม่มีผลที่เลือก แจ้งเตือนและไม่เรียก API', async () => {
        const { doc, calls } = await openDetailAsStaff();
        const before = calls.length;
        expect(doc.querySelector('input[value="APPROVE"]').checked).toBe(true);
        doc.querySelectorAll('input[name="decision"]').forEach(i => { i.checked = false; });
        doc.querySelector('#btnDecide').click();
        await flush();
        expect(doc.querySelector('#decErr').textContent).toBe('กรุณาเลือกผลการพิจารณา');
        expect(calls.length).toBe(before);
    });

    test('UI-07 ส่งกลับแก้ไขแต่เหตุผลมีแต่ช่องว่าง แจ้งให้กรอกเหตุผล', async () => {
        const { doc, calls } = await openDetailAsStaff();
        const before = calls.length;
        doc.querySelector('input[value="RETURN"]').checked = true;
        doc.querySelector('#comment').value = '   ';
        doc.querySelector('#btnDecide').click();
        await flush();
        expect(doc.querySelector('#decErr').textContent).toBe('ยังบันทึกผลไม่ได้ กรุณากรอกเหตุผล');
        expect(doc.querySelector('#commentErr').textContent).toBe('กรุณากรอกเหตุผลเมื่อส่งกลับแก้ไขหรือปฏิเสธ');
        expect(doc.querySelector('#comment').classList.contains('err')).toBe(true);
        expect(calls.length).toBe(before);
    });

    test('UI-08 อนุมัติแล้วกดซ้ำรัว ๆ ส่ง API ครั้งเดียว และ body ถูกต้อง', async () => {
        const { doc, calls } = await openDetailAsStaff();
        doc.querySelector('input[value="APPROVE"]').checked = true;
        doc.querySelector('#btnDecide').click();
        doc.querySelector('#btnDecide').click();
        await flush();
        const posts = calls.filter(c => c.method === 'POST');
        expect(posts.length).toBe(1);
        expect(posts[0].path).toBe('/api/approvals/50/decision');
        expect(posts[0].body).toEqual({ decision: 'APPROVE', comment: null });
    });

    test('UI-09 บันทึกสำเร็จ แสดงว่าส่งต่อให้อาจารย์แล้ว', async () => {
        const { doc } = await openDetailAsStaff();
        doc.querySelector('input[value="APPROVE"]').checked = true;
        doc.querySelector('#btnDecide').click();
        await flush();
        expect(doc.querySelector('#vDone').hidden).toBe(false);
        expect(doc.querySelector('#doneMsg').textContent).toContain('ส่งต่อให้อาจารย์');
    });

    test('UI-15 server ตอบ 409 แสดงข้อความจาก server อยู่หน้าเดิม', async () => {
        const { doc } = await openDetailAsStaff({
            ...listRoutes,
            'POST /api/approvals/50/decision': [409, { code: 'INVALID_STATUS', message: 'คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: UNDER_REVIEW)' }]
        });
        doc.querySelector('input[value="APPROVE"]').checked = true;
        doc.querySelector('#btnDecide').click();
        await flush();
        expect(doc.querySelector('#vDetail').hidden).toBe(false);
        expect(doc.querySelector('#decErr').textContent).toContain('ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้');
    });

    test('UI-13 อาจารย์เปิดหน้า โหลดสถานะ UNDER_REVIEW และไม่มีเมนูแบบฟอร์มคำร้อง', async () => {
        const { doc, calls } = await open('staff-review.html', USERS.advisor, { 'GET /api/applications?status=UNDER_REVIEW': [200, []] });
        expect(calls[0].path).toBe('/api/applications?status=UNDER_REVIEW');
        expect(doc.querySelector('#listTitle').textContent).toBe('พิจารณาใบสมัคร');
        expect(doc.querySelector('#u5nav').textContent).not.toContain('แบบฟอร์มคำร้อง');
    });

    test('UI-16 นักศึกษาเปิดหน้านี้ไม่ได้', async () => {
        const { doc, calls } = await open('staff-review.html', USERS.student);
        expect(doc.querySelector('#vDenied').hidden).toBe(false);
        // เมนูนักศึกษาเรียก /api/requests/me เพื่อดูว่าทำถึงขั้นไหน แต่ต้องไม่เรียก API ของเจ้าหน้าที่
        expect(calls.filter(c => c.path.startsWith('/api/applications')).length).toBe(0);
    });

    test('UI-17 backend ไม่ทำงาน แสดงข้อความเชื่อมต่อไม่ได้', async () => {
        const { doc } = await open('staff-review.html', USERS.staff, { 'GET /api/applications?status=SUBMITTED': 'DOWN' });
        expect(doc.querySelector('#listErr').textContent).toContain('ไม่สามารถเชื่อมต่อระบบได้');
    });

    test('UI-18 ยังไม่ login (ไม่มี user ใน coop-state) ไม่เรียก API และไม่แสดงหน้าใด ๆ (ถูกส่งไป login.html)', async () => {
        const { doc, calls } = await open('staff-review.html', null);
        expect(calls.length).toBe(0);
        expect(doc.querySelector('#vList').hidden).toBe(true);
    });

    test('UI-36 login จาก login.html แล้ว (มีแต่ username) หา id ด้วย GET /api/users/{username} แล้วใช้ id นั้นใน X-User-Id', async () => {
        const { doc, calls } = await open('staff-review.html', null, {
            ...listRoutes, 'GET /api/users/staff01': [200, { id: 1, username: 'staff01', name: 'สมศรี ทำงานดี', role: 'STAFF' }]
        }, 'staff01');
        expect(calls[0].path).toBe('/api/users/staff01');
        expect(calls[1].path).toBe('/api/applications?status=SUBMITTED');
        expect(calls[1].headers['X-User-Id']).toBe('1');
        expect(doc.querySelector('#who').textContent).toBe('เจ้าหน้าที่ สมศรี ทำงานดี');
    });

    test('UI-38 username ที่ไม่มีในระบบ แสดงข้อความจาก server และปุ่มกลับไปหน้า Login', async () => {
        const { doc } = await open('staff-review.html', null, {
            'GET /api/users/nobody': [404, { code: 'NOT_FOUND', message: 'ไม่พบบัญชีผู้ใช้ nobody ในระบบ' }]
        }, 'nobody');
        expect(doc.querySelector('#vPick').textContent).toContain('ไม่พบบัญชีผู้ใช้ nobody ในระบบ');
        expect(doc.querySelector('#btnRelogin')).not.toBeNull();
    });
});

describe('U5:T7 หน้าแบบฟอร์มคำร้อง (staff-forms.html)', () => {
    const formRoutes = { 'GET /api/forms': [200, []] };

    async function openEditor() {
        const page = await open('staff-forms.html', USERS.staff, formRoutes);
        page.doc.querySelector('#btnNew').click();
        return page;
    }

    test('UI-21 ไม่กรอกชื่อแบบฟอร์ม แจ้งเตือนและไม่เรียก API', async () => {
        const { doc, calls } = await openEditor();
        doc.querySelector('#btnSaveDraft').click();
        await flush();
        expect(doc.querySelector('#editErr').textContent).toBe('กรุณาระบุชื่อแบบฟอร์ม');
        expect(calls.filter(c => c.method === 'POST').length).toBe(0);
    });

    test('UI-22 ช่องที่ไม่มีชื่อ แจ้งเลขช่อง', async () => {
        const { doc } = await openEditor();
        doc.querySelector('#fName').value = 'คำร้อง 3/2569';
        doc.querySelector('#btnAddField').click();
        doc.querySelector('#btnSaveDraft').click();
        await flush();
        expect(doc.querySelector('#editErr').textContent).toBe('ช่องที่ 2 ยังไม่มีชื่อช่อง');
    });

    test('UI-26 เปิดใช้งานโดยไม่กำหนดวันหมดเขต แจ้งเตือน', async () => {
        const { doc } = await openEditor();
        doc.querySelector('#fName').value = 'คำร้อง 3/2569';
        doc.querySelector('#btnSavePublish').click(); // ครั้งแรก = ขอยืนยัน
        expect(doc.querySelector('#btnSavePublish').textContent).toContain('ยืนยัน');
        doc.querySelector('#btnSavePublish').click();
        await flush();
        expect(doc.querySelector('#editErr').textContent).toContain('วันหมดเขต');
    });

    test('UI-27 บันทึกฉบับร่าง ส่ง POST /api/forms พร้อมช่องและวันหมดเขต', async () => {
        const { window, doc, calls } = await open('staff-forms.html', USERS.staff, {
            ...formRoutes,
            'POST /api/forms': [201, { id: 12, name: 'คำร้อง 3/2569', status: 'DRAFT', fields: [] }]
        });
        doc.querySelector('#btnNew').click();
        doc.querySelector('#fName').value = 'คำร้อง 3/2569';
        doc.querySelector('#fDeadline').value = '2026-12-31T23:59';
        doc.querySelector('#btnSaveDraft').click();
        await flush();
        const post = calls.find(c => c.method === 'POST');
        expect(post.path).toBe('/api/forms');
        expect(post.body.name).toBe('คำร้อง 3/2569');
        expect(post.body.deadline).toBe('2026-12-31T23:59:00');
        expect(post.body.fields).toEqual([{ label: 'ชื่อ-นามสกุล', fieldType: 'TEXT', required: true, options: null }]);
        expect(doc.querySelector('#formsMsg').textContent).toContain('บันทึกฉบับร่าง');
        expect(window.U5.validateForm({ name: 'x', fields: [] }, false)).toBe('แบบฟอร์มต้องมีอย่างน้อย 1 ช่อง');
    });

    test('UI-30 อาจารย์เปิดหน้าแบบฟอร์มไม่ได้', async () => {
        const { doc } = await open('staff-forms.html', USERS.advisor);
        expect(doc.querySelector('#vDenied').hidden).toBe(false);
    });
});

describe('U5:T7 หน้าการแจ้งเตือน (notifications.html)', () => {
    test('UI-31/32 เห็นการแจ้งเตือน กดอ่านแล้วเรียก PATCH', async () => {
        const { doc, calls } = await open('notifications.html', USERS.student, {
            'GET /api/notifications': [200, [
                { id: 3, title: 'เจ้าหน้าที่ตรวจใบสมัครแล้ว', message: 'ส่งต่อให้อาจารย์พิจารณาแล้ว', applicationId: 50, isRead: false, createdAt: '2026-10-16T11:00:00' },
                { id: 2, title: 'ยื่นใบสมัครสำเร็จ', message: 'รอเจ้าหน้าที่ตรวจสอบ', applicationId: 50, isRead: true, createdAt: '2026-10-15T10:24:00' }]],
            'PATCH /api/notifications/3/read': [200, { id: 3, isRead: true }]
        });
        expect(doc.querySelectorAll('.notif').length).toBe(2);
        expect(doc.querySelector('.notif').classList.contains('read')).toBe(false);
        doc.querySelector('.notif').click();
        await flush();
        expect(calls.some(c => c.method === 'PATCH' && c.path === '/api/notifications/3/read')).toBe(true);
        expect(doc.querySelector('.notif').classList.contains('read')).toBe(true);
    });
});

describe('U5 เพิ่มเติม: รับคำร้อง · พิจารณา · แบบฟอร์ม', () => {
    const APP2 = { ...APP, id: 51, studentId: '6609650002', studentName: 'สมหญิง รักเรียน' };
    const rows = [
        { id: 50, studentId: '6609650001', studentName: 'สมชาย ใจดี', status: 'SUBMITTED', submittedAt: '2026-10-11T10:00:00', documents: [], attention: 'OVERDUE' },
        { id: 51, studentId: '6609650002', studentName: 'สมหญิง รักเรียน', status: 'SUBMITTED', submittedAt: '2026-10-15T10:00:00', documents: [], attention: null }
    ];
    const routes = {
        'GET /api/applications?status=SUBMITTED': [200, rows],
        'GET /api/applications/summary': [200, { DRAFT: 0, SUBMITTED: 2, UNDER_REVIEW: 5, NEED_CORRECTION: 1, APPROVED: 9, REJECTED: 0 }],
        'POST /api/approvals/bulk': [200, { approved: [50], skipped: [{ applicationId: 51, reason: 'คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้' }] }]
    };

    test('UI-40 ตัวเลขบนทุกแท็บมาจาก /api/applications/summary (เรียกหลังโหลดรายการ)', async () => {
        const { doc, calls } = await open('staff-review.html', USERS.staff, routes);
        expect(calls[0].path).toBe('/api/applications?status=SUBMITTED');
        expect(calls[1].path).toBe('/api/applications/summary');
        const count = s => doc.querySelector(`#tabs .chip[data-status="${s}"] b`)?.textContent;
        expect(count('SUBMITTED')).toBe('2');
        expect(count('UNDER_REVIEW')).toBe('5');
        expect(count('NEED_CORRECTION')).toBe('1');
        expect(count('REJECTED')).toBeUndefined();
    });

    test('UI-41 คำร้องที่รอนาน มีป้าย "รอนาน" และไฮไลต์แถว', async () => {
        const { doc } = await open('staff-review.html', USERS.staff, routes);
        const first = doc.querySelector('#appRows tr');
        expect(first.classList.contains('attn')).toBe(true);
        expect(first.textContent).toContain('รอนาน');
        expect(doc.querySelectorAll('#appRows tr')[1].classList.contains('attn')).toBe(false);
    });

    test('UI-42 อนุมัติหลายรายการ: เลือก 2 แถว กดยืนยัน 2 ครั้ง ส่ง POST /api/approvals/bulk และแสดงรายการที่ถูกข้าม', async () => {
        const { doc, calls } = await open('staff-review.html', USERS.staff, routes);
        expect(doc.querySelector('#bulkBar').hidden).toBe(true);
        const ck = doc.querySelector('#ckAll');
        ck.checked = true;
        ck.dispatchEvent(new doc.defaultView.Event('change'));
        expect(doc.querySelector('#bulkBar').hidden).toBe(false);
        expect(doc.querySelector('#bulkCount').textContent).toContain('2');
        doc.querySelector('#btnBulk').click();      // ขอยืนยัน
        expect(calls.some(c => c.path === '/api/approvals/bulk')).toBe(false);
        doc.querySelector('#btnBulk').click();
        await flush();
        const post = calls.find(c => c.path === '/api/approvals/bulk');
        expect(post.body).toEqual({ applicationIds: [50, 51] });
        expect(doc.querySelector('#listMsg').textContent).toContain('อนุมัติแล้ว 1 รายการ');
        expect(doc.querySelector('#listMsg').textContent).toContain('เลขที่ 51');
    });

    test('UI-43 แท็บที่อนุมัติไม่ได้ (ส่งกลับแก้ไข) ไม่มีช่องเลือกหลายรายการ', async () => {
        const { doc } = await open('staff-review.html', USERS.staff, {
            ...routes, 'GET /api/applications?status=NEED_CORRECTION': [200, rows.map(r => ({ ...r, status: 'NEED_CORRECTION' }))]
        });
        doc.querySelector('#tabs .chip[data-status="NEED_CORRECTION"]').click();
        await flush();
        expect(doc.querySelector('#ckAll')).toBeNull();
        expect(doc.querySelector('[data-pick]')).toBeNull();
    });

    const detail = {
        ...APP2,
        answers: [{ fieldId: 101, label: 'ชื่อ-นามสกุล', value: 'สมหญิง รักเรียน', previousValue: null },
            { fieldId: 102, label: 'เบอร์โทรศัพท์', value: '0899999999', previousValue: '0812' },
            { fieldId: 105, label: 'Transcript', value: 'transcript.pdf', previousValue: null }]
    };
    const detailRoutes = {
        ...routes,
        'GET /api/applications/51/detail': [200, detail],
        'POST /api/approvals/51/decision': [200, { id: 901, applicationId: 51, approvalLevel: 'STAFF', decision: 'RETURN', applicationStatus: 'NEED_CORRECTION' }]
    };
    async function openDetail51() {
        const page = await open('staff-review.html', USERS.staff, detailRoutes);
        page.doc.querySelector('[data-open="51"]').click();
        await flush();
        return page;
    }

    test('UI-44 ฉบับที่แก้แล้วส่งใหม่ แสดงช่องที่เปลี่ยนพร้อมค่าเดิม', async () => {
        const { doc } = await openDetail51();
        const changed = doc.querySelectorAll('#dAnswers dd.changed');
        expect(changed.length).toBe(1);
        expect(changed[0].textContent).toContain('0899999999');
        expect(changed[0].querySelector('.was').textContent).toBe('เดิม: 0812');
    });

    test('UI-45 ส่งกลับแก้ไข: เลือกเหตุผลที่ใช้บ่อย + เลือกช่องที่ต้องแก้ ส่ง fieldIds ไปด้วย', async () => {
        const { window, doc, calls } = await openDetail51();
        expect(doc.querySelector('#fixFields').hidden).toBe(true);   // ค่าเริ่มต้น = อนุมัติ
        const ret = doc.querySelector('input[value="RETURN"]');
        ret.checked = true;
        ret.dispatchEvent(new window.Event('change'));
        expect(doc.querySelector('#fixFields').hidden).toBe(false);
        const sel = doc.querySelector('#presetReason');
        sel.value = window.U5.PRESET_REASONS[0];
        sel.dispatchEvent(new window.Event('change'));
        expect(doc.querySelector('#comment').value).toBe(window.U5.PRESET_REASONS[0]);
        doc.querySelector('#fixList .fix[value="105"]').checked = true;
        doc.querySelector('#btnDecide').click();
        await flush();
        const post = calls.find(c => c.path === '/api/approvals/51/decision');
        expect(post.body).toEqual({ decision: 'RETURN', comment: window.U5.PRESET_REASONS[0], fieldIds: [105] });
    });

    const formList = [
        { id: 11, name: 'คำร้อง 2/2569', version: 2, status: 'PUBLISHED', deadline: '2099-12-31T23:59:00', fields: [{ label: 'ชื่อ', fieldType: 'TEXT', required: true }] },
        { id: 10, name: 'คำร้อง 1/2569', version: 1, status: 'ARCHIVED', deadline: '2020-01-01T00:00:00', fields: [] }
    ];

    test('UI-46 ปิดรับก่อนกำหนด: มีปุ่มเฉพาะฉบับที่เปิดใช้งานอยู่ กดยืนยันแล้วเรียก POST /api/forms/{id}/close', async () => {
        const { doc, calls } = await open('staff-forms.html', USERS.staff, {
            'GET /api/forms': [200, formList],
            'POST /api/forms/11/close': [200, { ...formList[0], deadline: '2026-10-24T10:00:00' }]
        });
        expect(doc.querySelectorAll('[data-close]').length).toBe(1);
        const b = doc.querySelector('[data-close="11"]');
        b.click();
        expect(calls.some(c => c.path === '/api/forms/11/close')).toBe(false);
        b.click();
        await flush();
        expect(calls.some(c => c.method === 'POST' && c.path === '/api/forms/11/close')).toBe(true);
        expect(doc.querySelector('#formsMsg').textContent).toContain('ปิดรับคำร้อง');
    });

    test('UI-47 ดูตัวอย่างหน้านักศึกษา: แสดงช่องตามชนิด (ไฟล์ = input file, ตัวเลือก = select) และช่องบังคับมี *', async () => {
        const { doc, calls } = await open('staff-forms.html', USERS.staff, { 'GET /api/forms': [200, []] });
        doc.querySelector('#btnNew').click();
        doc.querySelector('#fName').value = 'คำร้อง 3/2569';
        doc.querySelector('#btnAddField').click();
        const rowsEl = doc.querySelectorAll('#fieldList .frow');
        rowsEl[1].querySelector('.f-label').value = 'หนังสือรับรอง';
        rowsEl[1].querySelector('.f-type').value = 'FILE';
        const before = calls.length;
        doc.querySelector('#btnPreview').click();
        const box = doc.querySelector('#previewBox');
        expect(box.hidden).toBe(false);
        expect(box.textContent).toContain('คำร้อง 3/2569');
        expect(box.querySelectorAll('input[type=file]').length).toBe(1);
        expect(box.querySelector('label').innerHTML).toContain('<em>*</em>');
        expect(calls.length).toBe(before);   // ไม่บันทึกอะไร
        doc.querySelector('#btnPreview').click();
        expect(box.hidden).toBe(true);
    });
});
