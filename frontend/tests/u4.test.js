// @vitest-environment node
/*
 * U4:T7 Unit test ส่วน UI : หน้าจับคู่บริษัท (ranking.html) UI ใหม่ 5 อันดับ + เรียก API
 * test cases อ้างอิง .doc/UserStory/U4/T11.md (ลำดับ 1–10)
 * จำลอง API ด้วย fake fetch (ไม่เรียก Backend จริง)
 * รัน: npm run test:ui -- frontend/tests/u4.test.js
 */

import { describe, test, expect, vi, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(testDir, '..', 'ranking.html'), 'utf8');
const script = fs.readFileSync(path.join(testDir, '..', 'js', 'script.js'), 'utf8');

const API = 'http://localhost:8080';
const COMPANIES = [
    { id: 1, name: 'SCB TechX', address: 'กรุงเทพฯ', status: 'OPEN' },
    { id: 2, name: 'Agoda Services', address: 'กรุงเทพฯ', status: 'OPEN' },
    { id: 3, name: 'KBTG', address: 'นนทบุรี', status: 'OPEN' },
    { id: 4, name: 'LINE MAN Wongnai', address: 'กรุงเทพฯ', status: 'OPEN' },
    { id: 5, name: 'Bitkub', address: 'กรุงเทพฯ', status: 'OPEN' },
    { id: 6, name: 'True Digital Group', address: 'กรุงเทพฯ', status: 'OPEN' }
];

const openedWindows = [];

afterEach(() => {
    for (const window of openedWindows) {
        window.close();
    }
    openedWindows.length = 0;
});

const reply = (status, body) => ({
    status,
    ok: status >= 200 && status < 300,
    json: vi.fn().mockResolvedValue(body)
});

/*
 * เปิดหน้า ranking ของนักศึกษาที่ผ่านขั้นก่อนหน้าแล้ว (unlocked = 3)
 * api(method, url, body) คืน reply(...) ตาม request ที่หน้าเว็บส่งมา
 */
async function openRankingPage(api = defaultApi()) {
    const dom = new JSDOM(html, {
        url: 'http://localhost/ranking.html',
        runScripts: 'outside-only',
        pretendToBeVisual: true
    });
    const { window } = dom;
    openedWindows.push(window);

    window.sessionStorage.setItem('coop-state', JSON.stringify({ user: '6609650003', unlocked: 3 }));
    window.sessionStorage.setItem('u1-auth', JSON.stringify({ userId: 3, role: 'STUDENT' }));

    window.fetch = vi.fn((url, options = {}) => {
        const method = options.method || 'GET';
        const body = options.body ? JSON.parse(options.body) : undefined;
        return Promise.resolve(api(method, url, body, options.headers || {}));
    });

    window.eval(script);
    window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
    await window.U4Ranking.ready;
    return window;
}

function defaultApi(saved = []) {
    return (method, url, body) => {
        if (method === 'GET' && url === API + '/api/companies') return reply(200, COMPANIES);
        if (method === 'GET' && url === API + '/api/students/me/company-ranking') return reply(200, saved);
        if (method === 'PUT' && url === API + '/api/students/me/company-ranking') {
            return reply(200, body.companyIds.map((id, i) => ({
                rank: i + 1,
                companyId: id,
                companyName: COMPANIES.find(c => c.id === id).name
            })));
        }
        return reply(404, { code: 'NOT_FOUND', message: 'Not Found' });
    };
}

const $ = (window, selector) => window.document.querySelector(selector);
const $$ = (window, selector) => [...window.document.querySelectorAll(selector)];

function openSlot(window, slot) {
    const input = $(window, `.rk-search[data-slot="${slot}"]`);
    expect(input).not.toBeNull();
    input.dispatchEvent(new window.FocusEvent('focusin', { bubbles: true }));
}

function type(window, slot, text) {
    const input = $(window, `.rk-search[data-slot="${slot}"]`);
    input.value = text;
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
}

function press(window, slot, key) {
    $(window, `.rk-search[data-slot="${slot}"]`)
        .dispatchEvent(new window.KeyboardEvent('keydown', { key, bubbles: true }));
}

function pickByClick(window, name) {
    const opt = $$(window, '.rk-opt').find(o => o.textContent.includes(name));
    expect(opt).toBeDefined();
    opt.dispatchEvent(new window.MouseEvent('mousedown', { bubbles: true }));
}

function pick(window, slot, name) {
    openSlot(window, slot);
    type(window, slot, name);
    press(window, slot, 'Enter');
}

const options = window => $$(window, '.rk-opt b').map(b => b.textContent);
const chosen = window => $$(window, '.rk-row').map(r => (r.querySelector('.rk-co b') || {}).textContent || '');
const flush = () => new Promise(r => setTimeout(r, 0));

describe('U4-UI หน้าจับคู่บริษัท (ranking.html)', () => {
    test('1 เปิดหน้า เรียก GET /api/companies พร้อม X-User-Id แสดง 5 ช่องอันดับ และ "เลือกแล้ว 0 จาก 5 อันดับ"', async () => {
        const window = await openRankingPage();
        const [url, opts] = window.fetch.mock.calls.find(c => c[0] === API + '/api/companies');

        expect(url).toBe(API + '/api/companies');
        expect(opts.headers['X-User-Id']).toBe('3');
        expect($$(window, '.rk-row')).toHaveLength(5);
        expect($(window, '#rkCount').textContent).toBe('เลือกแล้ว 0 จาก 5 อันดับ');
    });

    test('2 คลิกช่องอันดับ 1 แสดงรายชื่อบริษัทที่เปิดรับจาก API ครบ 6 แห่ง', async () => {
        const window = await openRankingPage();
        openSlot(window, 0);

        expect(options(window)).toEqual(COMPANIES.map(c => c.name));
    });

    test('3 พิมพ์ "kb" ค้นหาเจอเฉพาะ KBTG แล้วกด Enter เลือกเป็นอันดับ 1', async () => {
        const window = await openRankingPage();
        openSlot(window, 0);
        type(window, 0, 'kb');

        expect(options(window)).toEqual(['KBTG']);

        press(window, 0, 'Enter');
        expect(chosen(window)[0]).toBe('KBTG');
        expect($(window, '#rkCount').textContent).toBe('เลือกแล้ว 1 จาก 5 อันดับ');
    });

    test('4 บริษัทที่เลือกแล้วไม่แสดงในอันดับอื่น และมีข้อความแจ้ง', async () => {
        const window = await openRankingPage();
        pick(window, 0, 'KBTG');
        openSlot(window, 1);

        expect(options(window)).not.toContain('KBTG');
        expect(options(window)).toHaveLength(5);
        expect($(window, '.rk-menu').textContent)
            .toContain('ไม่แสดงบริษัทที่เลือกในอันดับอื่นแล้ว (KBTG)');
    });

    test('5 กด "ล้าง" ที่อันดับ 1 ช่องกลับเป็นช่องค้นหา', async () => {
        const window = await openRankingPage();
        pick(window, 0, 'KBTG');
        $(window, '[data-clear="0"]').click();

        expect(chosen(window)[0]).toBe('');
        expect($(window, '.rk-search[data-slot="0"]')).not.toBeNull();
    });

    test('6 กด "ล้างทั้งหมด" ทุกอันดับว่าง และตัวนับกลับเป็น 0', async () => {
        const window = await openRankingPage();
        pick(window, 0, 'KBTG');
        pick(window, 1, 'Bitkub');
        $(window, '#btnClear').click();

        expect(chosen(window)).toEqual(['', '', '', '', '']);
        expect($(window, '#rkCount').textContent).toBe('เลือกแล้ว 0 จาก 5 อันดับ');
    });

    test('7 เลือกไม่ครบ 5 อันดับแล้วกดยืนยัน แสดงข้อความ และไม่ส่ง PUT', async () => {
        const window = await openRankingPage();
        pick(window, 0, 'KBTG');
        $(window, '#btnConfirm').click();
        await flush();

        expect($(window, '#msgBad').hidden).toBe(false);
        expect($(window, '#msgBadTxt').textContent).toBe('กรุณาเลือกบริษัทให้ครบ 5 อันดับ (เลือกแล้ว 1 อันดับ)');
        expect(window.fetch.mock.calls.some(c => (c[1] || {}).method === 'PUT')).toBe(false);
    });

    test('8 เลือกครบ 5 อันดับแล้วกดยืนยัน ส่ง PUT companyIds ตามลำดับ และแสดง "สำเร็จ"', async () => {
        const window = await openRankingPage();
        pick(window, 0, 'Bitkub');
        openSlot(window, 1);
        pickByClick(window, 'SCB TechX');
        pick(window, 2, 'Agoda');
        pick(window, 3, 'LINE');
        pick(window, 4, 'True');
        $(window, '#btnConfirm').click();
        await flush();
        await flush();

        const put = window.fetch.mock.calls.find(c => (c[1] || {}).method === 'PUT');
        expect(put[0]).toBe(API + '/api/students/me/company-ranking');
        expect(JSON.parse(put[1].body)).toEqual({ companyIds: [5, 1, 2, 4, 6] });
        expect($(window, '#msgOk').hidden).toBe(false);
        expect($(window, '#msgOk').textContent).toContain('บันทึกลำดับเรียบร้อยแล้ว');
    });

    test('9 เคยบันทึกไว้แล้ว เปิดหน้าใหม่แสดงอันดับจาก GET /api/students/me/company-ranking', async () => {
        const saved = [5, 1, 2, 4, 6].map((id, i) => ({
            rank: i + 1, companyId: id, companyName: COMPANIES.find(c => c.id === id).name
        }));
        const window = await openRankingPage(defaultApi(saved));

        expect(chosen(window)).toEqual(['Bitkub', 'SCB TechX', 'Agoda Services', 'LINE MAN Wongnai', 'True Digital Group']);
        expect($(window, '#rkCount').textContent).toBe('เลือกแล้ว 5 จาก 5 อันดับ');
    });

    test('10 API ตอบ 409 RANKING_LOCKED แสดงข้อความจาก server และล็อกการแก้ไข', async () => {
        const saved = [5, 1, 2, 4, 6].map((id, i) => ({
            rank: i + 1, companyId: id, companyName: COMPANIES.find(c => c.id === id).name
        }));
        const base = defaultApi(saved);
        const window = await openRankingPage((method, url, body) => method === 'PUT'
            ? reply(409, { code: 'RANKING_LOCKED', message: 'คุณยื่นคำร้องแล้ว ไม่สามารถแก้ไขอันดับบริษัทได้' })
            : base(method, url, body));
        $(window, '#btnConfirm').click();
        await flush();
        await flush();

        expect($(window, '#rkLock').hidden).toBe(false);
        expect($(window, '#rkLock').textContent).toBe('คุณยื่นคำร้องแล้ว ไม่สามารถแก้ไขอันดับบริษัทได้');
        expect($(window, '#btnConfirm').disabled).toBe(true);
        expect($(window, '[data-clear]')).toBeNull();
    });

    test('11 เชื่อมต่อ backend ไม่ได้ แสดง "ไม่สามารถเชื่อมต่อระบบได้"', async () => {
        const window = await openRankingPage(() => { throw new TypeError('Failed to fetch'); });

        expect($(window, '#rkLoadErr').hidden).toBe(false);
        expect($(window, '#rkLoadErr').textContent).toBe('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
    });
});