// @vitest-environment node
// U1:T7 Unit test หน้า UI (login.html + js/login.js) — จำลอง backend ด้วย fake fetch
// รัน: npm install (ครั้งแรก) แล้ว npm run test:ui  หรือ  npx vitest run frontend/tests/u1.test.js
import { describe, test, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const read = f => fs.readFileSync(path.join(testDir, '..', f), 'utf8');
const html = read('login.html');
const script = read('js/login.js');

const OK_STUDENT = {
    accessToken: 'token-1', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
    userId: 3, username: '6609650001', fullName: 'A arizona', role: 'STUDENT'
};
const OK_STAFF = { ...OK_STUDENT, userId: 1, username: 'staff01', fullName: 'S staff', role: 'STAFF' };
const ERR = (code, message) => ({ code, message });

/* reply = [status, body] | 'DOWN' | (body) => [status, body] */
function fakeFetch(reply, calls) {
    return async (url, opts = {}) => {
        calls.push({ url, method: opts.method || 'GET', headers: opts.headers, body: opts.body ? JSON.parse(opts.body) : null });
        const r = typeof reply === 'function' ? reply(calls.at(-1).body) : reply;
        if (r === 'DOWN') throw new TypeError('Failed to fetch');
        const [status, body] = r;
        return { ok: status < 400, status, json: async () => body };
    };
}

const flush = () => new Promise(r => setTimeout(r, 20));

async function open(reply = [500, null], session = {}) {
    const dom = new JSDOM(html, { url: 'http://localhost/login.html', runScripts: 'outside-only' });
    const { window } = dom;
    const calls = [], nav = [];
    for (const [k, v] of Object.entries(session)) window.sessionStorage.setItem(k, JSON.stringify(v));
    window.fetch = fakeFetch(reply, calls);
    window.eval(script);
    window.U1Login.go = url => nav.push(url);
    window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
    await flush();
    const doc = window.document;
    const ss = k => JSON.parse(window.sessionStorage.getItem(k) || 'null');
    async function login(u, p) {
        doc.querySelector('#u').value = u;
        doc.querySelector('#p').value = p;
        doc.querySelector('#loginForm').dispatchEvent(new window.Event('submit', { cancelable: true, bubbles: true }));
        await flush();
    }
    return { window, doc, calls, nav, ss, login };
}

const errBox = doc => doc.querySelector('#loginErr');
const errText = doc => doc.querySelector('#loginErrMsg').textContent;

describe('U1 login.html', () => {
    test('UI-L01 เปิดหน้า: มีช่อง Username/Password ปุ่มเข้าสู่ระบบ และยังไม่แสดงกล่องแจ้งเตือน', async () => {
        const { doc, calls } = await open();
        expect(doc.querySelector('#u')).not.toBeNull();
        expect(doc.querySelector('#p').type).toBe('password');
        expect(doc.querySelector('#loginBtn').textContent).toBe('เข้าสู่ระบบ');
        expect(errBox(doc).hidden).toBe(true);
        expect(calls).toHaveLength(0);
    });

    test('UI-L02 ไม่กรอก Username หรือ Password: แจ้งเตือน และไม่เรียก API', async () => {
        const { doc, calls, login } = await open();
        await login('', 'secret');
        expect(errBox(doc).hidden).toBe(false);
        expect(errText(doc)).toBe('กรุณากรอก Username และ Password');
        await login('6609650001', '');
        await login('   ', 'secret');
        expect(calls).toHaveLength(0);
    });

    test('UI-L03 นักศึกษา login สำเร็จ: ส่ง POST /api/auth/login ถูกต้อง เก็บ session และไปหน้าตรวจสอบคุณสมบัติ', async () => {
        const { calls, nav, ss, login } = await open([200, OK_STUDENT]);
        await login('  6609650001 ', 'secret');
        expect(calls).toHaveLength(1);
        expect(calls[0].url).toBe('http://localhost:8080/api/auth/login');
        expect(calls[0].method).toBe('POST');
        expect(calls[0].headers['Content-Type']).toBe('application/json');
        expect(calls[0].body).toEqual({ username: '6609650001', password: 'secret' });
        expect(ss('u1-auth')).toMatchObject({ accessToken: 'token-1', userId: 3, role: 'STUDENT' });
        expect(ss('u5-user')).toEqual({ id: 3, role: 'STUDENT', name: 'A arizona', username: '6609650001' });
        expect(ss('coop-state').user).toBe('6609650001');
        expect(nav).toEqual(['check.html']);
    });

    test('UI-L04 นักศึกษาที่ทำขั้นตอนไปแล้ว: ไปต่อขั้นเดิม และไม่ลบความคืบหน้า', async () => {
        const { nav, ss, login } = await open([200, OK_STUDENT], { 'coop-state': { user: null, unlocked: 2, form: { gpax: '3.50' } } });
        await login('6609650001', 'secret');
        expect(nav).toEqual(['submit.html']);
        expect(ss('coop-state')).toMatchObject({ user: '6609650001', unlocked: 2, form: { gpax: '3.50' } });
    });

    test('UI-L05 เจ้าหน้าที่ / อาจารย์ login สำเร็จ: ไปหน้าตรวจใบสมัคร', async () => {
        const staff = await open([200, OK_STAFF]);
        await staff.login('staff01', '1234');
        expect(staff.nav).toEqual(['staff-review.html']);
        const advisor = await open([200, { ...OK_STAFF, userId: 2, username: 'advisor01', role: 'ADVISOR' }]);
        await advisor.login('advisor01', '1234');
        expect(advisor.nav).toEqual(['staff-review.html']);
    });

    test('UI-L06 รหัสผ่านผิด (401): แสดงข้อความจาก server ล้างช่อง Password และไม่เปลี่ยนหน้า', async () => {
        const { doc, nav, ss, login } = await open([401, ERR('INVALID_CREDENTIALS', 'Username หรือ Password ไม่ถูกต้อง')]);
        await login('6609650001', 'wrong');
        expect(errBox(doc).hidden).toBe(false);
        expect(errText(doc)).toBe('Username หรือ Password ไม่ถูกต้อง');
        expect(doc.querySelector('#p').value).toBe('');
        expect(doc.querySelector('#u').value).toBe('6609650001');
        expect(nav).toHaveLength(0);
        expect(ss('u1-auth')).toBeNull();
    });

    test('UI-L07 บัญชีถูกปิดใช้งาน (403) และระบบ มธ. ขัดข้อง (502): แสดงข้อความจาก server', async () => {
        const off = await open([403, ERR('FORBIDDEN', 'บัญชีนี้ถูกปิดการใช้งาน กรุณาติดต่อเจ้าหน้าที่')]);
        await off.login('advisor03', 'secret');
        expect(errText(off.doc)).toBe('บัญชีนี้ถูกปิดการใช้งาน กรุณาติดต่อเจ้าหน้าที่');
        const down = await open([502, ERR('TU_API_UNAVAILABLE', 'ไม่สามารถเชื่อมต่อระบบของมหาวิทยาลัยได้ กรุณาลองใหม่อีกครั้ง')]);
        await down.login('6609650001', 'secret');
        expect(errText(down.doc)).toBe('ไม่สามารถเชื่อมต่อระบบของมหาวิทยาลัยได้ กรุณาลองใหม่อีกครั้ง');
        expect(down.nav).toHaveLength(0);
    });

    test('UI-L08 เปิด backend ไม่ได้ (network error): แจ้งว่าเชื่อมต่อระบบไม่ได้ และกดใหม่ได้', async () => {
        const { doc, login } = await open('DOWN');
        await login('6609650001', 'secret');
        expect(errText(doc)).toBe('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
        expect(doc.querySelector('#loginBtn').disabled).toBe(false);
        expect(doc.querySelector('#loginBtn').textContent).toBe('เข้าสู่ระบบ');
    });

    test('UI-L09 ระหว่างรอ server: ปุ่มกดไม่ได้และแสดง "กำลังเข้าสู่ระบบ..."', async () => {
        let release;
        const { window, doc, calls } = await open();
        window.fetch = (url, opts) => { calls.push(opts); return new Promise(r => { release = () => r({ ok: true, status: 200, json: async () => OK_STUDENT }); }); };
        doc.querySelector('#u').value = '6609650001';
        doc.querySelector('#p').value = 'secret';
        doc.querySelector('#loginForm').dispatchEvent(new window.Event('submit', { cancelable: true, bubbles: true }));
        await flush();
        expect(doc.querySelector('#loginBtn').disabled).toBe(true);
        expect(doc.querySelector('#loginBtn').textContent).toBe('กำลังเข้าสู่ระบบ...');
        release();
        await flush();
        expect(doc.querySelector('#loginBtn').disabled).toBe(false);
    });

    test('UI-L10 login ค้างอยู่และ token ยังไม่หมดอายุ: เปิดหน้า login แล้วไปหน้าหลักทันที', async () => {
        const { nav, calls } = await open([500, null], { 'u1-auth': OK_STAFF, 'coop-state': { user: 'staff01' } });
        expect(nav).toEqual(['staff-review.html']);
        expect(calls).toHaveLength(0);
    });

    test('UI-L11 token หมดอายุ หรือ logout ไปแล้ว: อยู่หน้า login และล้างข้อมูลเก่า', async () => {
        const expired = await open([500, null], { 'u1-auth': { ...OK_STAFF, expiresAt: '2000-01-01T00:00:00Z' }, 'coop-state': { user: 'staff01' }, 'u5-user': { id: 1 } });
        expect(expired.nav).toHaveLength(0);
        expect(expired.ss('u1-auth')).toBeNull();
        expect(expired.ss('u5-user')).toBeNull();
        const loggedOut = await open([500, null], { 'u1-auth': OK_STAFF, 'coop-state': { user: null } });
        expect(loggedOut.nav).toHaveLength(0);
    });

    test('UI-L12 response 200 แต่ข้อมูลไม่ครบ: ไม่ถือว่า login สำเร็จ', async () => {
        const { doc, nav, login } = await open([200, { accessToken: 'x' }]);
        await login('6609650001', 'secret');
        expect(nav).toHaveLength(0);
        expect(errBox(doc).hidden).toBe(false);
    });
});