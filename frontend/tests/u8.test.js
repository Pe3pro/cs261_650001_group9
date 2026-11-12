// @vitest-environment node
/*
 * U8:T4 Unit test ส่วน UI : หน้าบันทึกการทำงาน (worklog.html)
 * API ถูกจำลองด้วย fetch ปลอม (mock API service) ไม่ต้องเปิด Backend
 * รัน: npm run test:ui -- frontend/tests/u8.test.js
 */

import { describe, test, expect, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(testDir, '..', 'worklog.html'), 'utf8');
const script = fs.readFileSync(path.join(testDir, '..', 'js', 'script.js'), 'utf8');

const openedWindows = [];

afterEach(() => {
    for (const window of openedWindows) {
        window.close();
    }
    openedWindows.length = 0;
});

const pad = n => String(n).padStart(2, '0');
const isoOf = d => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
const TODAY = isoOf(new Date());
const TOMORROW = isoOf(new Date(Date.now() + 864e5));

/* ---------- API จำลอง: เก็บบันทึกในหน่วยความจำ และจดทุก request ที่หน้าเว็บเรียก ---------- */
function mockApi(options = {}) {
    const db = { logs: options.logs ? [...options.logs] : [], seq: 100, calls: [] };
    const view = l => ({ attachmentCount: 0, lateSubmitted: false, ...l });
    const reply = (status, data) => ({
        status,
        json: async () => {
            if (data === undefined) throw new Error('no body');
            return data;
        },
        blob: async () => new Blob(['%PDF-1.4'], { type: 'application/pdf' })
    });

    async function fetch(url, init = {}) {
        const method = init.method || 'GET';
        const p = new URL(url).pathname;
        db.calls.push({ method, path: p, url, headers: init.headers || {}, body: init.body });

        if (p === '/api/work-logs/my-internship') {
            return options.noInternship
                ? reply(404, { code: 'NOT_FOUND', message: 'ยังไม่มีข้อมูลการฝึกงาน' })
                : reply(200, { id: 7, studentId: 3, startDate: '2026-01-05', endDate: null, status: 'IN_PROGRESS' });
        }
        if (p === '/api/internships/7/work-logs' && method === 'GET') {
            return reply(200, db.logs.map(view));
        }
        if (p === '/api/internships/7/work-logs/summary') {
            return reply(200, { totalHours: 3, daysLogged: db.logs.length, workingDays: 10,
                missingDates: ['2026-11-06'], countByStatus: {} });
        }
        if (p === '/api/internships/7/work-logs' && method === 'POST') {
            const log = { id: ++db.seq, internshipId: 7, ...JSON.parse(init.body), hours: 3, status: 'DRAFT' };
            db.logs.push(log);
            return reply(201, view(log));
        }
        const file = p.match(/^\/api\/work-logs\/(\d+)\/attachments\/(\d+)$/);
        if (file && method === 'GET') {
            return init.headers && init.headers['X-User-Id'] === '3'
                ? reply(200)
                : reply(401, { code: 'UNAUTHORIZED', message: 'กรุณาเข้าสู่ระบบก่อนใช้งาน' });
        }
        const m = p.match(/^\/api\/work-logs\/(\d+)(\/\w+)?$/);
        const log = m && db.logs.find(l => String(l.id) === m[1]);
        if (m && !log) return reply(404, { code: 'NOT_FOUND', message: 'ไม่พบบันทึกการทำงาน' });
        if (m && !m[2] && method === 'GET') return reply(200, { attachments: [], ...view(log) });
        if (m && !m[2] && method === 'PUT') {
            Object.assign(log, JSON.parse(init.body));
            return reply(200, view(log));
        }
        if (m && !m[2] && method === 'DELETE') {
            db.logs = db.logs.filter(l => l !== log);
            return reply(204);
        }
        if (m && m[2] === '/submit') {
            if (options.submitStatus) return reply(options.submitStatus, { code: 'ERROR', message: 'ระบบขัดข้อง' });
            log.status = 'SUBMITTED';
            return reply(200, view({ ...log, lateSubmitted: Boolean(options.late) }));
        }
        return reply(500, { message: 'ไม่ได้จำลอง ' + method + ' ' + p });
    }

    return { db, fetch };
}

/* นักศึกษา userId 3 login แล้ว เปิดหน้าบันทึกการทำงาน */
async function openWorklogPage(options = {}) {
    const dom = new JSDOM(html, { url: 'http://localhost/worklog.html', runScripts: 'outside-only' });
    const { window } = dom;
    openedWindows.push(window);

    window.sessionStorage.setItem('coop-state', JSON.stringify({ user: '6609650001', unlocked: 3 }));
    window.sessionStorage.setItem('u5-user', JSON.stringify({ id: 3, role: 'STUDENT', username: '6609650001' }));
    const api = mockApi(options);
    window.fetch = api.fetch;
    window.confirm = () => options.confirm !== false;
    // jsdom ไม่มี URL.createObjectURL และเปิดลิงก์ไม่ได้ จึงจำลองไว้แล้วจดลิงก์ที่หน้าเว็บกด
    const opened = [];
    window.URL.createObjectURL = () => 'blob:http://localhost/file';
    window.URL.revokeObjectURL = () => {};
    window.HTMLAnchorElement.prototype.click = function () {
        opened.push({ href: this.href, download: this.download });
    };
    api.opened = opened;

    window.eval(script);
    window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
    await window.U8Worklog.ready;
    return { window, api };
}

const $ = (window, selector) => window.document.querySelector(selector);
const settle = () => new Promise(resolve => setTimeout(resolve, 30));

function type(window, selector, value) {
    const el = $(window, selector);
    el.value = value;
    el.dispatchEvent(new window.Event('input', { bubbles: true }));
    el.dispatchEvent(new window.Event('change', { bubbles: true }));
}

function fillValid(window) {
    type(window, '#wStart', '09:00');
    type(window, '#wEnd', '12:00');
    type(window, '#wTask', 'ออกแบบหน้าจอ');
    type(window, '#wSkill', 'ใช้ Figma');
}

const writes = api => api.db.calls.filter(c => c.method !== 'GET');

describe('U8-UI หน้าบันทึกการทำงาน (worklog.html)', () => {
    test('UT-UI-01 เปิดหน้า แสดงฟอร์ม วันที่ตั้งต้นเป็นวันนี้ และส่ง X-User-Id ไปกับ API', async () => {
        const { window, api } = await openWorklogPage();

        for (const id of ['#wDate', '#wStart', '#wEnd', '#wHours', '#wTask', '#wSkill', '#wProb', '#wFile', '#wSend', '#wDraft']) {
            expect($(window, id)).not.toBeNull();
        }
        expect($(window, '#wDate').value).toBe(TODAY);
        expect($(window, '#wHist').textContent).toContain('ยังไม่มีบันทึก');
        expect(api.db.calls[0].headers['X-User-Id']).toBe('3');
    });

    test('UT-UI-02 กรอกข้อมูลครบแล้วกดส่ง เรียก POST แล้ว submit แสดง "ส่งบันทึกสำเร็จ"', async () => {
        const { window, api } = await openWorklogPage();
        fillValid(window);
        $(window, '#wSend').click();
        await settle();

        const post = api.db.calls.find(c => c.method === 'POST' && c.path === '/api/internships/7/work-logs');
        expect(JSON.parse(post.body)).toMatchObject({ logDate: TODAY, startTime: '09:00', endTime: '12:00',
            activity: 'ออกแบบหน้าจอ', learning: 'ใช้ Figma' });
        expect(api.db.calls.some(c => c.path === '/api/work-logs/101/submit')).toBe(true);
        expect($(window, '#wOk').hidden).toBe(false);
        expect($(window, '#wHist').textContent).toContain('ส่งแล้ว');
    });

    test('UT-UI-03 ไม่กรอกงานที่ทำ แสดง error และไม่เรียก API', async () => {
        const { window, api } = await openWorklogPage();
        fillValid(window);
        type(window, '#wTask', '   ');
        $(window, '#wSend').click();
        await settle();

        expect($(window, '#wErr').textContent).toBe('กรุณากรอกงานที่ทำ');
        expect(writes(api)).toHaveLength(0);
    });

    test('UT-UI-04 เวลาสิ้นสุดก่อนเวลาเริ่ม แสดง error และไม่เรียก API', async () => {
        const { window, api } = await openWorklogPage();
        fillValid(window);
        type(window, '#wStart', '13:00');
        type(window, '#wEnd', '10:00');
        $(window, '#wSend').click();
        await settle();

        expect($(window, '#wErr').textContent).toBe('เวลาสิ้นสุดต้องหลังเวลาเริ่ม');
        expect(writes(api)).toHaveLength(0);
    });

    test('UT-UI-05 เวลาเริ่มเท่ากับเวลาสิ้นสุด แสดง error เดียวกับ UT-UI-04', async () => {
        const { window, api } = await openWorklogPage();
        fillValid(window);
        type(window, '#wStart', '10:00');
        type(window, '#wEnd', '10:00');
        $(window, '#wDraft').click();
        await settle();

        expect($(window, '#wErr').textContent).toBe('เวลาสิ้นสุดต้องหลังเวลาเริ่ม');
        expect(writes(api)).toHaveLength(0);
    });

    test('UT-UI-06 เลือกวันที่ในอนาคต แสดง "ไม่สามารถบันทึกวันที่ในอนาคตได้"', async () => {
        const { window, api } = await openWorklogPage();
        fillValid(window);
        type(window, '#wDate', TOMORROW);
        $(window, '#wSend').click();
        await settle();

        expect($(window, '#wErr').textContent).toBe('ไม่สามารถบันทึกวันที่ในอนาคตได้');
        expect(writes(api)).toHaveLength(0);
    });

    test('UT-UI-07 ชั่วโมงเกิน 12 ชั่วโมง แสดง error', async () => {
        const { window } = await openWorklogPage();
        fillValid(window);
        type(window, '#wStart', '06:00');
        type(window, '#wEnd', '18:30');
        $(window, '#wSend').click();
        await settle();

        expect($(window, '#wErr').textContent).toBe('ชั่วโมงทำงานต้องอยู่ระหว่าง 0.5 ถึง 12 ชั่วโมง');
    });

    test('UT-UI-08 คำนวณชั่วโมงอัตโนมัติ 09:00–17:30 = 8.5 ชั่วโมง', async () => {
        const { window } = await openWorklogPage();
        type(window, '#wStart', '09:00');
        type(window, '#wEnd', '17:30');

        expect($(window, '#wHours').value).toBe('8.5 ชั่วโมง');
    });

    test('UT-UI-09 API ตอบ error ตอนส่ง แสดงข้อความจาก API และข้อมูลในฟอร์มยังอยู่', async () => {
        const { window } = await openWorklogPage({ submitStatus: 500 });
        fillValid(window);
        $(window, '#wSend').click();
        await settle();

        expect($(window, '#wErr').textContent).toBe('ระบบขัดข้อง');
        expect($(window, '#wOk').hidden).toBe(true);
        expect($(window, '#wTask').value).toBe('ออกแบบหน้าจอ');
    });

    test('UT-UI-10 กดส่ง 2 ครั้งติดกัน เรียกสร้างบันทึกครั้งเดียว และปุ่มถูกปิดระหว่างรอ', async () => {
        const { window, api } = await openWorklogPage();
        fillValid(window);
        $(window, '#wSend').click();
        expect($(window, '#wSend').disabled).toBe(true);
        $(window, '#wSend').click();
        await settle();

        expect(api.db.calls.filter(c => c.method === 'POST' && c.path.endsWith('/work-logs'))).toHaveLength(1);
    });

    test('UT-UI-11 แสดงประวัติบันทึกเรียงจากวันล่าสุด พร้อมสถานะ (ส่งล่าช้า / ขอให้แก้ไข / อนุมัติแล้ว)', async () => {
        const logs = [
            { id: 1, logDate: '2026-11-03', hours: 8, status: 'APPROVED' },
            { id: 2, logDate: '2026-11-05', hours: 7.5, status: 'SUBMITTED', lateSubmitted: true },
            { id: 3, logDate: '2026-11-04', hours: 6, status: 'NEED_REVISION' }
        ];
        const { window } = await openWorklogPage({ logs });
        const rows = [...window.document.querySelectorAll('#wHist button.wl-hist')];

        expect(rows.map(r => r.dataset.id)).toEqual(['2', '3', '1']);
        expect(rows.map(r => r.querySelector('.pill').textContent)).toEqual(['ส่งล่าช้า', 'ขอให้แก้ไข', 'อนุมัติแล้ว']);
    });

    test('UT-UI-12 ลบบันทึกร่าง: ยืนยันแล้วเรียก DELETE · กดยกเลิกต้องไม่เรียก API', async () => {
        const logs = [{ id: 9, logDate: '2026-11-03', startTime: '09:00:00', endTime: '12:00:00', hours: 3,
            activity: 'งาน', learning: 'เรียนรู้', status: 'DRAFT' }];

        const cancel = await openWorklogPage({ logs, confirm: false });
        $(cancel.window, '#wHist button.wl-hist').click();
        await settle();
        $(cancel.window, '#wDelete').click();
        await settle();
        expect(cancel.api.db.calls.some(c => c.method === 'DELETE')).toBe(false);

        const ok = await openWorklogPage({ logs });
        $(ok.window, '#wHist button.wl-hist').click();
        await settle();
        expect($(ok.window, '#wDelete').hidden).toBe(false);
        $(ok.window, '#wDelete').click();
        await settle();
        expect(ok.api.db.calls.filter(c => c.method === 'DELETE').map(c => c.path)).toEqual(['/api/work-logs/9']);
        expect($(ok.window, '#wHist').textContent).toContain('ยังไม่มีบันทึก');
    });

    test('UT-UI-13 บันทึกที่ส่งแล้วเปิดดูได้แต่แก้ไขไม่ได้', async () => {
        const logs = [{ id: 4, logDate: '2026-11-03', startTime: '09:00:00', endTime: '12:00:00', hours: 3,
            activity: 'งาน', learning: 'เรียนรู้', status: 'SUBMITTED' }];
        const { window } = await openWorklogPage({ logs });
        $(window, '#wHist button.wl-hist').click();
        await settle();

        expect($(window, '#wTask').value).toBe('งาน');
        expect($(window, '#wTask').disabled).toBe(true);
        expect($(window, '#wSend').disabled).toBe(true);
    });

    test('UT-UI-14 ยังไม่มีข้อมูลการฝึกงาน แสดงข้อความและปิดฟอร์ม', async () => {
        const { window } = await openWorklogPage({ noInternship: true });

        expect($(window, '#wState').hidden).toBe(false);
        expect($(window, '#wState').textContent).toContain('ยังไม่มีข้อมูลการฝึกงาน');
        expect($(window, '#wSend').disabled).toBe(true);
    });

    test('UT-UI-15 เปิดไฟล์แนบ: โหลดด้วย fetch + header X-User-Id ไม่ส่ง userId ทาง URL', async () => {
        const logs = [{ id: 9, logDate: '2026-11-03', startTime: '09:00:00', endTime: '12:00:00', hours: 3,
            activity: 'งาน', learning: 'เรียนรู้', status: 'SUBMITTED',
            attachments: [{ id: 5, fileName: 'proof.pdf', contentType: 'application/pdf', size: 8 }] }];
        const { window, api } = await openWorklogPage({ logs });
        window.open = () => { throw new Error('ห้ามเปิดไฟล์ด้วย window.open'); };
        $(window, '#wHist button.wl-hist').click();
        await settle();

        $(window, '#wFiles button[data-attachment="5"]').click();
        await settle();

        const call = api.db.calls.find(c => c.path === '/api/work-logs/9/attachments/5');
        expect(call.method).toBe('GET');
        expect(call.headers['X-User-Id']).toBe('3');
        expect(api.db.calls.some(c => c.url.includes('userId='))).toBe(false);
        expect(api.opened).toEqual([{ href: 'blob:http://localhost/file', download: 'proof.pdf' }]);
        expect($(window, '#wErr').hidden).toBe(true);
    });
});
