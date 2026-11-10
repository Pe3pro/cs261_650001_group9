// @vitest-environment node
// U6:T4 Unit test หน้า UI (matching.html, staff-matching.html) — จำลอง backend ด้วย fake fetch
// รัน: npm install (ครั้งแรก) แล้ว npx vitest run frontend/tests/u6.test.js
import { describe, test, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const read = f => fs.readFileSync(path.join(testDir, '..', f), 'utf8');
const script = read('js/u6.js');

const USERS = {
    staff: { id: 1, role: 'STAFF', name: 'สมศรี ทำงานดี', username: 'staff01' },
    student: { id: 9, role: 'STUDENT', name: 'พิมพ์ชนก ตั้งใจ', username: '6609650012' }
};

const COMPANIES = [{ id: 1, name: 'SCB TechX', address: 'กรุงเทพฯ', status: 'OPEN', openPositions: 2, remainingQuota: 3 },
    { id: 2, name: 'Agoda Services', address: 'กรุงเทพฯ', status: 'OPEN', openPositions: 1, remainingQuota: 2 }];
const DETAIL = {
    1: { id: 1, name: 'SCB TechX', address: 'กรุงเทพฯ', status: 'OPEN', positions: [
        { id: 11, companyId: 1, companyName: 'SCB TechX', positionName: 'Backend Developer', quota: 1, availableSlots: 1, status: 'OPEN' },
        { id: 12, companyId: 1, companyName: 'SCB TechX', positionName: 'Data Analyst', quota: 2, availableSlots: 2, status: 'OPEN' },
        { id: 13, companyId: 1, companyName: 'SCB TechX', positionName: 'QA (ปิดรับ)', quota: 1, availableSlots: 1, status: 'CLOSED' }] },
    2: { id: 2, name: 'Agoda Services', address: 'กรุงเทพฯ', status: 'OPEN', positions: [
        { id: 21, companyId: 2, companyName: 'Agoda Services', positionName: 'Software Engineer', quota: 2, availableSlots: 2, status: 'OPEN' }] }
};
const PREFS = [{ rank: 1, positionId: 11, positionName: 'Backend Developer', companyName: 'SCB TechX', resultStatus: 'PENDING' },
    { rank: 2, positionId: 21, positionName: 'Software Engineer', companyName: 'Agoda Services', resultStatus: 'PENDING' }];
const ROUND = { id: 5, roundNo: 1, status: 'ANNOUNCED', openedAt: '2026-11-10T09:00:00', announcedAt: '2026-11-11T09:00:00', confirmDeadline: '2026-11-20T23:59:00', matchedCount: 1 };
const MATCH = { id: 70, applicationId: 40, studentId: '6609650012', studentName: 'พิมพ์ชนก ตั้งใจ', positionId: 11, positionName: 'Backend Developer', companyName: 'SCB TechX', rank: 1, status: 'PROPOSED', roundId: 5, roundNo: 1, matchedAt: '2026-11-10T10:00:00', respondedAt: null, confirmDeadline: '2026-11-20T23:59:00', declineReason: null };
const SUMMARY = { round: ROUND, preferences: PREFS, match: MATCH, quota: 1, availableSlots: 0, summary: 'คุณได้รับการจับคู่กับ ตำแหน่ง Backend Developer บริษัท SCB TechX (อันดับ 1) กรุณายืนยันภายใน 20/11/2026 23:59 น.' };

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
const flush = () => new Promise(r => setTimeout(r, 30));

async function open(file, user, routes = {}) {
    const dom = new JSDOM(read(file), { url: 'http://localhost/' + file, runScripts: 'outside-only' });
    const { window } = dom;
    const calls = [];
    window.sessionStorage.setItem('coop-state', JSON.stringify({ user: user.username, unlocked: 3 }));
    window.sessionStorage.setItem('u5-user', JSON.stringify(user));
    window.fetch = fakeFetch(routes, calls);
    window.eval(script);
    window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
    await flush();
    return { window, doc: window.document, calls };
}

const studentRoutes = (extra = {}) => ({
    'GET /api/requests/me': [200, [{ id: 40, status: 'APPROVED' }]],
    'GET /api/companies': [200, COMPANIES],
    'GET /api/companies/1': [200, DETAIL[1]],
    'GET /api/companies/2': [200, DETAIL[2]],
    'GET /api/applications/40/preferences': [200, PREFS],
    'GET /api/matches/me/summary': [404, { code: 'NOT_FOUND', message: 'ยังไม่ประกาศผลการจับคู่' }],
    ...extra
});
const pick = (doc, rank, value) => { const s = doc.querySelector(`select[data-rank="${rank}"]`); s.value = String(value); };

describe('U6:T4 หน้าเลือกตำแหน่งและผลการจับคู่ (matching.html)', () => {

    test('UI-01 ยังไม่มีใบสมัคร APPROVED แสดงข้อความรออนุมัติ และไม่โหลดตำแหน่ง', async () => {
        const { doc, calls } = await open('matching.html', USERS.student, { 'GET /api/requests/me': [200, [{ id: 40, status: 'UNDER_REVIEW' }]] });
        expect(doc.querySelector('#vNoApp').hidden).toBe(false);
        expect(doc.querySelector('#vPrefs').hidden).toBe(true);
        expect(calls.some(c => c.path === '/api/companies')).toBe(false);
    });

    test('UI-02 มีใบสมัคร APPROVED แสดง 5 อันดับ เฉพาะตำแหน่งที่เปิดรับ และเลือกอันดับเดิมไว้', async () => {
        const { doc, calls } = await open('matching.html', USERS.student, studentRoutes());
        const selects = doc.querySelectorAll('#rankList select');
        expect(selects.length).toBe(5);
        expect(selects[0].value).toBe('11');
        expect(selects[1].value).toBe('21');
        expect(selects[2].value).toBe('');
        expect(doc.querySelector('#rankList').innerHTML).not.toContain('QA (ปิดรับ)');
        expect(selects[0].querySelectorAll('optgroup').length).toBe(2);
        expect(calls[0].headers['X-User-Id']).toBe('9');
    });

    test('UI-03 ตรวจอันดับก่อนส่ง: ว่างทั้งหมด / เว้นอันดับ / ซ้ำ ไม่ผ่าน', async () => {
        const { window } = await open('matching.html', USERS.student, studentRoutes());
        const v = window.U6.validatePreferences;
        expect(v(['', '', '', '', ''])).toEqual({ ok: false, msg: window.U6.MSG.needOne });
        expect(v(['11', '', '21', '', ''])).toEqual({ ok: false, msg: window.U6.MSG.gap });
        expect(v(['11', '11', '', '', ''])).toEqual({ ok: false, msg: window.U6.MSG.dup });
        expect(v(['12', '21', '11', '', ''])).toEqual({ ok: true, ids: [12, 21, 11] });
    });

    test('UI-04 บันทึกอันดับ ส่ง PUT positionIds ตามลำดับ แล้วแจ้งบันทึกสำเร็จ', async () => {
        const saved = [{ rank: 1, positionId: 12 }, { rank: 2, positionId: 11 }, { rank: 3, positionId: 21 }];
        const page = await open('matching.html', USERS.student, studentRoutes({ 'PUT /api/applications/40/preferences': [200, saved] }));
        pick(page.doc, 1, 12); pick(page.doc, 2, 11); pick(page.doc, 3, 21);
        page.doc.querySelector('#btnSave').click();
        await flush();
        const put = page.calls.find(c => c.method === 'PUT');
        expect(put.path).toBe('/api/applications/40/preferences');
        expect(put.body).toEqual({ positionIds: [12, 11, 21] });
        expect(page.doc.querySelector('#prefOk').hidden).toBe(false);
        expect(page.doc.querySelector('#prefOk').textContent).toContain('3 อันดับ');
    });

    test('UI-05 เลือกซ้ำ แจ้งเตือนและไม่เรียก API', async () => {
        const page = await open('matching.html', USERS.student, studentRoutes());
        pick(page.doc, 1, 11); pick(page.doc, 2, 11);
        page.doc.querySelector('#btnSave').click();
        await flush();
        expect(page.doc.querySelector('#prefErr').textContent).toBe(page.window.U6.MSG.dup);
        expect(page.calls.some(c => c.method === 'PUT')).toBe(false);
    });

    test('UI-06 server ตอบ 409 แสดงข้อความจาก server', async () => {
        const page = await open('matching.html', USERS.student, studentRoutes({
            'PUT /api/applications/40/preferences': [409, { code: 'ALREADY_MATCHED', message: 'คุณได้รับการจับคู่แล้ว แก้อันดับไม่ได้' }] }));
        page.doc.querySelector('#btnSave').click();
        await flush();
        expect(page.doc.querySelector('#prefErr').textContent).toBe('คุณได้รับการจับคู่แล้ว แก้อันดับไม่ได้');
    });

    test('UI-07 ยังไม่ประกาศผล (summary 404) ซ่อนผลการจับคู่ และแก้อันดับได้', async () => {
        const { doc } = await open('matching.html', USERS.student, studentRoutes());
        expect(doc.querySelector('#vResult').hidden).toBe(true);
        expect(doc.querySelector('#prefActs').hidden).toBe(false);
        expect(doc.querySelector('select[data-rank="1"]').disabled).toBe(false);
        expect(doc.querySelector('#err').hidden).toBe(true);
    });

    test('UI-08 ประกาศผลแล้วได้ PROPOSED แสดงผล ปุ่มตอบรับ และล็อกอันดับ', async () => {
        const { doc } = await open('matching.html', USERS.student, studentRoutes({ 'GET /api/matches/me/summary': [200, SUMMARY] }));
        expect(doc.querySelector('#vResult').hidden).toBe(false);
        expect(doc.querySelector('#summaryText').textContent).toContain('Backend Developer');
        expect(doc.querySelector('#matchInfo').textContent).toContain('SCB TechX');
        expect(doc.querySelector('#respondBox').hidden).toBe(false);
        expect(doc.querySelector('select[data-rank="1"]').disabled).toBe(true);
        expect(doc.querySelector('#prefActs').hidden).toBe(true);
    });

    test('UI-09 สละสิทธิ์โดยไม่กรอกเหตุผล แจ้งเตือนและไม่เรียก API', async () => {
        const page = await open('matching.html', USERS.student, studentRoutes({ 'GET /api/matches/me/summary': [200, SUMMARY] }));
        page.doc.querySelector('input[name=accept][value=no]').checked = true;
        page.doc.querySelector('#btnRespond').click();
        await flush();
        expect(page.doc.querySelector('#respondErr').textContent).toBe(page.window.U6.MSG.needReason);
        expect(page.calls.some(c => c.path === '/api/matches/70/respond')).toBe(false);
    });

    test('UI-10 สละสิทธิ์พร้อมเหตุผล ส่ง accept=false และ reason', async () => {
        const page = await open('matching.html', USERS.student, studentRoutes({
            'GET /api/matches/me/summary': [200, SUMMARY],
            'POST /api/matches/70/respond': [200, { ...MATCH, status: 'DECLINED', declineReason: 'ได้ที่ฝึกงานอื่นแล้ว' }] }));
        page.doc.querySelector('input[name=accept][value=no]').checked = true;
        page.doc.querySelector('#reason').value = 'ได้ที่ฝึกงานอื่นแล้ว';
        page.doc.querySelector('#btnRespond').click();
        await flush();
        const post = page.calls.find(c => c.path === '/api/matches/70/respond');
        expect(post.body).toEqual({ accept: false, reason: 'ได้ที่ฝึกงานอื่นแล้ว' });
        expect(page.doc.querySelector('#respondOk').hidden).toBe(false);
    });

    test('UI-11 ยืนยันสิทธิ์ ส่ง accept=true', async () => {
        const page = await open('matching.html', USERS.student, studentRoutes({
            'GET /api/matches/me/summary': [200, SUMMARY],
            'POST /api/matches/70/respond': [200, { ...MATCH, status: 'CONFIRMED' }] }));
        page.doc.querySelector('input[name=accept][value=yes]').checked = true;
        page.doc.querySelector('#btnRespond').click();
        await flush();
        expect(page.calls.find(c => c.path === '/api/matches/70/respond').body).toEqual({ accept: true, reason: null });
    });

    test('UI-12 เมนูนักศึกษามีลิงก์ "เลือกตำแหน่ง / ผลจับคู่" และเป็นหน้าปัจจุบัน', async () => {
        const { doc } = await open('matching.html', USERS.student, studentRoutes());
        const on = doc.querySelector('#u6nav a.on');
        expect(on.getAttribute('href')).toBe('matching.html');
        expect(doc.querySelector('#u6nav').textContent).not.toContain('แบบฟอร์มคำร้อง');
    });
});

const staffRoutes = (extra = {}) => ({
    'GET /api/matching/rounds/current': [200, { ...ROUND, status: 'OPEN', announcedAt: null }],
    'GET /api/matches?roundId=5': [200, [MATCH]],
    'GET /api/companies': [200, COMPANIES],
    'GET /api/companies/1': [200, DETAIL[1]],
    'GET /api/companies/2': [200, DETAIL[2]],
    ...extra
});

describe('U6:T4 หน้าเจ้าหน้าที่ (staff-matching.html)', () => {

    test('UI-13 นักศึกษาเปิดหน้าเจ้าหน้าที่ไม่ได้', async () => {
        const { doc, calls } = await open('staff-matching.html', USERS.student, staffRoutes());
        expect(doc.querySelector('#vDenied').hidden).toBe(false);
        expect(doc.querySelector('#vStaff').hidden).toBe(true);
        expect(calls.length).toBe(0);
    });

    test('UI-14 ยังไม่มีรอบ แสดงข้อความ ซ่อนปุ่มจับคู่และประกาศผล', async () => {
        const { doc } = await open('staff-matching.html', USERS.staff, staffRoutes({ 'GET /api/matching/rounds/current': [404, { code: 'NOT_FOUND', message: 'ยังไม่มีรอบการจับคู่' }] }));
        expect(doc.querySelector('#roundText').textContent).toContain('ยังไม่มีรอบ');
        expect(doc.querySelector('#btnAuto').hidden).toBe(true);
        expect(doc.querySelector('#btnAnnounce').hidden).toBe(true);
        expect(doc.querySelector('#msg').hidden).toBe(true);
    });

    test('UI-15 เปิดรอบโดยไม่กำหนดวันหมดเขต แจ้งเตือนและไม่เรียก API', async () => {
        const page = await open('staff-matching.html', USERS.staff, staffRoutes());
        page.doc.querySelector('#openForm').dispatchEvent(new page.window.Event('submit', { cancelable: true }));
        await flush();
        expect(page.doc.querySelector('#msg').textContent).toBe(page.window.U6.MSG.needDeadline);
        expect(page.calls.some(c => c.method === 'POST' && c.path === '/api/matching/rounds')).toBe(false);
    });

    test('UI-16 เปิดรอบใหม่ ส่ง confirmDeadline แบบมีวินาที', async () => {
        const page = await open('staff-matching.html', USERS.staff, staffRoutes({
            'POST /api/matching/rounds': [201, { ...ROUND, id: 6, roundNo: 2, status: 'OPEN' }] }));
        page.doc.querySelector('#deadline').value = '2026-11-30T23:59';
        page.doc.querySelector('#openForm').dispatchEvent(new page.window.Event('submit', { cancelable: true }));
        await flush();
        const post = page.calls.find(c => c.method === 'POST' && c.path === '/api/matching/rounds');
        expect(post.body).toEqual({ confirmDeadline: '2026-11-30T23:59:00' });
        expect(page.doc.querySelector('#msg').textContent).toContain('รอบที่ 2');
    });

    test('UI-17 รอบ OPEN กดจับคู่อัตโนมัติ แสดงจำนวนและเหตุผลของใบที่จับไม่ได้', async () => {
        const page = await open('staff-matching.html', USERS.staff, staffRoutes({
            'POST /api/matching/auto': [200, { roundNo: 1, matched: [MATCH], unmatched: [{ applicationId: 41, studentName: 'ธนกร ขยันดี', reason: 'ตำแหน่งที่เลือกเต็มหรือปิดรับทั้งหมด' }] }] }));
        expect(page.doc.querySelector('#btnAuto').hidden).toBe(false);
        page.doc.querySelector('#btnAuto').click();
        await flush();
        const box = page.doc.querySelector('#autoResult');
        expect(box.hidden).toBe(false);
        expect(box.textContent).toContain('จับคู่ได้ 1 คน');
        expect(box.textContent).toContain('ธนกร ขยันดี');
    });

    test('UI-18 ตารางผลจับคู่ของรอบ และกดยกเลิกส่ง DELETE', async () => {
        const page = await open('staff-matching.html', USERS.staff, staffRoutes({ 'DELETE /api/matches/70': [200, { ...MATCH, status: 'CANCELLED' }] }));
        expect(page.doc.querySelector('#matchRows').textContent).toContain('พิมพ์ชนก ตั้งใจ');
        page.doc.querySelector('[data-cancel="70"]').click();
        await flush();
        expect(page.calls.some(c => c.method === 'DELETE' && c.path === '/api/matches/70')).toBe(true);
    });

    test('UI-19 ดูผู้เลือกตำแหน่ง แล้วจับคู่เอง ส่ง applicationId และ positionId', async () => {
        const page = await open('staff-matching.html', USERS.staff, staffRoutes({
            'GET /api/matching/candidates?positionId=12': [200, [{ applicationId: 42, studentId: '6609650014', studentName: 'กมลชนก ใฝ่รู้', rank: 2, alreadyMatched: false }]],
            'POST /api/matches': [201, { ...MATCH, id: 71, applicationId: 42, studentName: 'กมลชนก ใฝ่รู้', positionName: 'Data Analyst' }] }));
        page.doc.querySelector('[data-cand="12"]').click();
        await flush();
        expect(page.doc.querySelector('#candidateCard').hidden).toBe(false);
        page.doc.querySelector('[data-match="42"]').click();
        await flush();
        expect(page.calls.find(c => c.method === 'POST' && c.path === '/api/matches').body).toEqual({ applicationId: 42, positionId: 12 });
    });

    test('UI-20 แก้โควตาต่ำกว่าที่จับคู่แล้ว แสดงข้อความ 400 จาก server', async () => {
        const page = await open('staff-matching.html', USERS.staff, staffRoutes({
            'PUT /api/positions/11': [400, { code: 'QUOTA_BELOW_MATCHED', message: 'ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (1 คน) ไม่ได้' }] }));
        page.doc.querySelector('[data-quota="11"]').value = '0';
        page.doc.querySelector('[data-save="11"]').click();
        await flush();
        const put = page.calls.find(c => c.method === 'PUT');
        expect(put.body.quota).toBe(0);
        expect(page.doc.querySelector('#msg').textContent).toContain('ลดโควตาต่ำกว่า');
    });
});
