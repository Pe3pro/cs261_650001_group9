// @vitest-environment node
import { test, expect, vi, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
const html = fs.readFileSync(new URL('../check.html', import.meta.url), 'utf8');
const script = fs.readFileSync(new URL('../js/script.js', import.meta.url), 'utf8');
const windows = [];
const passed = { valid: true, eligible: true, message: 'ผ่านคุณสมบัติ', errors: [], reasons: [], warnings: [] };
const flush = () => new Promise(resolve => setTimeout(resolve, 40));
async function open({ response = passed, status = 200, profileFails = false, legacy = false } = {}) {
    const w = new JSDOM(html, { url: 'http://localhost/check.html', runScripts: 'outside-only' }).window;
    windows.push(w);
    w.sessionStorage.setItem('u1-auth', JSON.stringify({ userId: 42, username: '6610000002', role: 'STUDENT' }));
    w.sessionStorage.setItem('coop-state', JSON.stringify({ user: '6610000002', unlocked: 1,
        ...(legacy ? { form: { id: '6410000001', gpax: '3.10' }, val: { '61g0': 'B' }, ack: [true, true, true] } : {}) }));
    w.fetch = vi.fn(async url => url.endsWith('/api/students/me')
        ? { ok: !profileFails, json: async () => ({ studentId: '6610000002', faculty: 'วิทยาศาสตร์และเทคโนโลยี', major: 'วิทยาการคอมพิวเตอร์' }) }
        : { status, ok: status === 200, json: async () => response });
    const timer = w.setTimeout.bind(w);
    w.setTimeout = (fn, delay, ...args) => timer(fn, delay >= 15000 ? delay : 0, ...args);
    await flush(); // Let JSDOM's automatic DOMContentLoaded finish first.
    w.eval(script);
    w.document.dispatchEvent(new w.Event('DOMContentLoaded'));
    await flush();
    return w;
}
function change(w, selector, value) {
    const el = w.document.querySelector(selector);
    el.value = value;
    el.dispatchEvent(new w.Event('input', { bubbles: true }));
}
function confirm(w) {
    w.document.querySelectorAll('.cf').forEach(el => {
        el.checked = true;
        el.dispatchEvent(new w.Event('change', { bubbles: true }));
    });
}
function fill(w, gpa = '3.10') {
    change(w, '#f_gpax', gpa);
    w.document.querySelectorAll('select[data-t="val"]').forEach(el => {
        el.value = el.closest('#coreRows') ? 'B' : 'กำลังศึกษา';
        el.dispatchEvent(new w.Event('change', { bubbles: true }));
    });
    confirm(w);
}
async function submit(w) { w.document.querySelector('#btnCheck').click(); await flush(); }
const posts = w => w.fetch.mock.calls.filter(([url]) => url.endsWith('/eligibility/check'));
afterEach(() => windows.splice(0).forEach(w => w.close()));

test('profile identity is readonly and faculty comes from DB', async () => {
    const w = await open();
    expect(w.document.querySelector('#f_id').value).toBe('6610000002');
    expect(w.document.querySelector('#f_id').readOnly).toBe(true);
    expect(w.document.querySelector('#f_fac').value).toBe('วิทยาศาสตร์และเทคโนโลยี');
    expect(w.fetch.mock.calls[0][1].headers['X-User-Id']).toBe('42');
});
test('initial GPA, grades and statuses are blank and button disabled', async () => {
    const w = await open();
    expect(w.document.querySelector('#f_gpax').value).toBe('');
    w.document.querySelectorAll('select[data-t="val"]').forEach(el => expect(el.value).toBe(''));
    expect(w.document.querySelector('#btnCheck').disabled).toBe(true);
});
test('confirming alone cannot POST and highlights missing fields', async () => {
    const w = await open(); confirm(w);
    expect(w.document.querySelector('#btnCheck').disabled).toBe(false);
    await submit(w);
    expect(posts(w)).toHaveLength(0);
    expect(w.document.querySelector('#f_gpax').getAttribute('aria-invalid')).toBe('true');
    expect(w.document.querySelectorAll('select[aria-invalid="true"]').length).toBeGreaterThan(0);
});
test('GPA 2.75 sends profile ID despite DOM tampering and displays mocked pass', async () => {
    const w = await open(); fill(w, '2.75');
    w.document.querySelector('#f_id').value = '9999999999';
    await submit(w);
    expect(posts(w)).toHaveLength(1);
    expect(posts(w)[0][1].method).toBe('POST');
    expect(JSON.parse(posts(w)[0][1].body)).toMatchObject({ studentId: '6610000002', gpax: '2.75', acknowledgements: [true, true, true] });
    expect(w.document.querySelector('#rAlert').textContent).toContain('ผ่านคุณสมบัติ');
});
test('GPA 2.74 displays mocked failure and reason', async () => {
    const w = await open({ response: { ...passed, eligible: false, message: 'ไม่ผ่านคุณสมบัติ', reasons: ['GPA ต้องไม่ต่ำกว่า 2.75'] } });
    fill(w, '2.74'); await submit(w);
    expect(w.document.querySelector('#rAlert').textContent).toContain('ไม่ผ่านคุณสมบัติ');
    expect(w.document.querySelector('#rList').textContent).toContain('GPA ต้องไม่ต่ำกว่า 2.75');
});
test('invalid GPA is highlighted and focused without POST', async () => {
    const w = await open(); fill(w, '3.10abc'); await submit(w);
    expect(posts(w)).toHaveLength(0);
    expect(w.document.activeElement.id).toBe('f_gpax');
    expect(w.document.querySelector('#f_gpax').getAttribute('aria-invalid')).toBe('true');
});
test('correction clears highlight on resubmit', async () => {
    const w = await open(); fill(w, 'abc'); await submit(w);
    change(w, '#f_gpax', '3.10'); await submit(w);
    expect(w.document.querySelector('#f_gpax').hasAttribute('aria-invalid')).toBe(false);
    expect(posts(w)).toHaveLength(1);
});
test('profile failure blocks checking', async () => {
    const w = await open({ profileFails: true }); fill(w); await submit(w);
    expect(w.document.querySelector('#btnCheck').disabled).toBe(true);
    expect(posts(w)).toHaveLength(0);
    expect(w.document.querySelector('#formError').hidden).toBe(false);
});
test('legacy sample values are discarded', async () => {
    const w = await open({ legacy: true });
    expect(w.document.querySelector('#f_gpax').value).toBe('');
    expect(w.document.querySelector('select[data-t="val"]').value).toBe('');
    expect(w.document.querySelector('.cf').checked).toBe(false);
});
test('unsupported HTTP status displays server error', async () => {
    const w = await open({ status: 500 }); fill(w); await submit(w);
    expect(w.document.querySelector('#rAlert').textContent).toContain('ไม่สามารถตรวจสอบคุณสมบัติได้');
});
test('malformed response displays error', async () => {
    const w = await open({ response: {} }); fill(w); await submit(w);
    expect(w.document.querySelector('#rList').textContent).toContain('รูปแบบข้อมูลตอบกลับ');
});
test('network failure does not display success', async () => {
    const w = await open(); fill(w);
    w.fetch.mockRejectedValueOnce(new w.TypeError('Network error'));
    await submit(w);
    expect(w.document.querySelector('#rAlert').textContent).toContain('ไม่สามารถตรวจสอบคุณสมบัติได้');
});
test('HTTP 400 shows API validation errors', async () => {
    const w = await open({ status: 400, response: { ...passed, valid: false, eligible: null, message: 'ข้อมูลไม่ถูกต้อง', errors: ['ไม่พบผลการเรียนวิชา คพ.101'] } });
    fill(w); await submit(w);
    expect(w.document.querySelector('#formError').textContent).toContain('ไม่พบผลการเรียนวิชา คพ.101');
    expect(w.document.querySelector('select[data-k="61g0"]').getAttribute('aria-invalid')).toBe('true');
});
