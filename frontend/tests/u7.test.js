// @vitest-environment node

import {
    describe,
    test,
    expect,
    vi,
    afterEach
} from 'vitest';

import { JSDOM } from 'jsdom';
import fs from 'node:fs';

const html = fs.readFileSync(
    new URL('../status.html',
        import.meta.url),
    'utf8'
);

const script = fs.readFileSync(
    new URL('../js/status.js',
        import.meta.url),
    'utf8'
);

const openedWindows = [];

const PROFILE = {
    studentId: '6609650001',
    fullName: 'สมชาย ใจดี',
    email: 'somchai@dome.tu.ac.th',
    faculty: 'วิทยาศาสตร์และเทคโนโลยี',
    major: 'วิทยาการคอมพิวเตอร์',
    gpa: 3.10
};

const REQUEST = {
    id: 1,
    studentId: '6609650001',
    formTemplateId: 1,
    formName: 'คำร้องขอฝึกสหกิจศึกษา 2/2569',
    status: 'SUBMITTED',
    createdAt: '2026-11-08T15:40:00',
    submittedAt: '2026-11-08T15:49:00',
    updatedAt: '2026-11-08T15:49:00',
    correctionReason: null
};

function reply(status, body) {
    return {
        status,
        ok: status >= 200 && status < 300,
        json: vi.fn().mockResolvedValue(body)
    };
}

function waitForPage() {
    return new Promise(resolve => {
        setTimeout(resolve, 50);
    });
}

function defaultApi(url) {
    if (url.endsWith('/api/students/me')) {
        return Promise.resolve(reply(200, PROFILE));
    }

    if (url.endsWith('/api/requests/me')) {
        return Promise.resolve(reply(200, [REQUEST]));
    }

    return Promise.resolve(
        reply(404, {
            message: 'ไม่พบ API'
        })
    );
}

async function openStatusPage(api = defaultApi) {
    const dom = new JSDOM(html, {
        url: 'http://localhost/status.html',
        runScripts: 'outside-only'
    });

    const window = dom.window;
    openedWindows.push(window);

    window.sessionStorage.setItem(
        'u5-user',
        JSON.stringify({
            id: 3,
            role: 'STUDENT',
            name: 'สมชาย ใจดี',
            username: '6609650001'
        })
    );

    window.sessionStorage.setItem(
        'u1-auth',
        JSON.stringify({
            userId: 3,
            username: '6609650001',
            role: 'STUDENT'
        })
    );

    window.sessionStorage.setItem(
        'coop-state',
        JSON.stringify({
            user: '6609650001',
            unlocked: 1
        })
    );

    window.fetch = vi.fn((url, options = {}) => {
        return api(url, options);
    });

    window.eval(script);

    window.document.dispatchEvent(
        new window.Event('DOMContentLoaded')
    );

    await waitForPage();

    return window;
}

afterEach(() => {
    for (const window of openedWindows) {
        window.close();
    }

    openedWindows.length = 0;
});

describe('U7 หน้าแสดงข้อมูลและสถานะนักศึกษา', () => {
    test(
        'UI-01 แสดงข้อมูลนักศึกษาและสถานะคำร้องล่าสุด',
        async() => {
            const window = await openStatusPage();

            expect(
                window.document.querySelector('#studentId')
                .textContent
            ).toBe('6609650001');

            expect(
                window.document.querySelector('#studentName')
                .textContent
            ).toBe('สมชาย ใจดี');

            expect(
                window.document.querySelector('#faculty')
                .textContent
            ).toBe('วิทยาศาสตร์และเทคโนโลยี');

            expect(
                window.document.querySelector('#major')
                .textContent
            ).toBe('วิทยาการคอมพิวเตอร์');

            expect(
                window.document.querySelector('#requestId')
                .textContent
            ).toBe('1');

            expect(
                window.document.querySelector('#requestStatus')
                .textContent
            ).toContain('ยื่นแล้ว');

            expect(
                window.document.querySelector('#requestSection')
                .hidden
            ).toBe(false);
        }
    );

    test(
        'UI-02 ส่ง X-User-Id ไปยัง API ทั้งสองรายการ',
        async() => {
            const window = await openStatusPage();

            const calls = window.fetch.mock.calls;

            expect(calls.length).toBeGreaterThanOrEqual(2);

            const profileCall = calls.find(call =>
                call[0].endsWith('/api/students/me')
            );

            const requestsCall = calls.find(call =>
                call[0].endsWith('/api/requests/me')
            );

            expect(profileCall[1].headers['X-User-Id'])
                .toBe('3');

            expect(requestsCall[1].headers['X-User-Id'])
                .toBe('3');
        }
    );

    test(
        'UI-03 นักศึกษาที่ยังไม่มีคำร้องเห็นข้อความแจ้ง',
        async() => {
            const api = url => {
                if (url.endsWith('/api/students/me')) {
                    return Promise.resolve(
                        reply(200, PROFILE)
                    );
                }

                if (url.endsWith('/api/requests/me')) {
                    return Promise.resolve(
                        reply(200, [])
                    );
                }

                return Promise.resolve(
                    reply(404, {})
                );
            };

            const window = await openStatusPage(api);

            expect(
                window.document.querySelector('#statusEmpty')
                .hidden
            ).toBe(false);

            expect(
                window.document.querySelector('#statusEmpty')
                .textContent
            ).toContain('ยังไม่มีข้อมูล');

            expect(
                window.document.querySelector('#requestSection')
                .hidden
            ).toBe(true);
        }
    );

    test(
        'UI-04 แสดงเหตุผลเมื่อคำร้องถูกส่งกลับแก้ไข',
        async() => {
            const correctionRequest = {
                ...REQUEST,
                status: 'NEED_CORRECTION',
                correctionReason: 'กรุณาแนบ Transcript ฉบับล่าสุด'
            };

            const api = url => {
                if (url.endsWith('/api/students/me')) {
                    return Promise.resolve(
                        reply(200, PROFILE)
                    );
                }

                return Promise.resolve(
                    reply(200, [correctionRequest])
                );
            };

            const window = await openStatusPage(api);

            expect(
                window.document.querySelector('#requestStatus')
                .textContent
            ).toContain('ส่งกลับให้แก้ไข');

            expect(
                window.document.querySelector('#correctionBox')
                .hidden
            ).toBe(false);

            expect(
                window.document.querySelector('#correctionReason')
                .textContent
            ).toContain('Transcript');
        }
    );

    test(
        'UI-05 แจ้งข้อผิดพลาดเมื่อเชื่อมต่อ API ไม่ได้',
        async() => {
            const api = () =>
                Promise.reject(
                    new Error('network error')
                );

            const window = await openStatusPage(api);

            expect(
                window.document.querySelector('#statusError')
                .hidden
            ).toBe(false);

            expect(
                window.document.querySelector('#statusError')
                .textContent
            ).toContain('ไม่สามารถเชื่อมต่อระบบได้');
        }
    );
});