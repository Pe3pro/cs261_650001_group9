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

const STAFF_HTML = fs.readFileSync(
    'frontend/student-status.html',
    'utf8'
);

const STUDENT_HTML = fs.readFileSync(
    'frontend/my-status.html',
    'utf8'
);

const U7_JS = fs.readFileSync(
    'frontend/js/u7.js',
    'utf8'
);

const openedWindows = [];

const STUDENT = {
    studentId: '6609650001',
    fullName: 'สมชาย ใจดี',
    email: 'somchai@dome.tu.ac.th',
    faculty: 'วิทยาศาสตร์และเทคโนโลยี',
    major: 'วิทยาการคอมพิวเตอร์',
    gpa: 3.1
};

const APPLICATION = {
    id: 1,
    studentId: '6609650001',
    formTemplateId: 1,
    formName: 'คำร้องขอฝึกสหกิจศึกษา 2/2569',
    status: 'APPROVED',
    createdAt: '2026-11-01T09:00:00',
    submittedAt: '2026-11-02T09:00:00',
    updatedAt: '2026-11-03T09:00:00',
    deadline: '2026-12-10T23:59:59',
    answers: {},
    documents: [],
    correctionReason: null
};

const MATCH = {
    id: 10,
    applicationId: 1,
    studentId: '6609650001',
    studentName: 'สมชาย ใจดี',
    positionId: 2,
    positionName: 'Software Developer',
    companyName: 'บริษัท ทดสอบ จำกัด',
    rank: 1,
    status: 'CONFIRMED',
    roundId: 1,
    roundNo: 1,
    matchedAt: '2026-11-05T09:00:00',
    respondedAt: '2026-11-06T09:00:00',
    confirmDeadline: '2026-11-10T23:59:59',
    declineReason: null
};

const INTERNSHIP = {
    id: 20,
    matchId: 10,
    studentId: 3,
    startDate: '2026-12-01',
    endDate: '2027-03-31',
    supervisorName: 'คุณผู้ดูแล',
    supervisorEmail: 'supervisor@example.com',
    supervisorPhone: '0812345678',
    status: 'NOT_STARTED',
    terminatedReason: null,
    updatedBy: 1,
    createdAt: '2026-11-06T09:00:00',
    updatedAt: '2026-11-06T09:00:00'
};

const TIMELINE = [{
        id: 1,
        studentId: 3,
        type: 'APPLICATION',
        fromStatus: null,
        toStatus: 'SUBMITTED',
        changedBy: 3,
        changedByName: 'สมชาย ใจดี',
        changedAt: '2026-11-02T09:00:00',
        note: null
    },
    {
        id: 2,
        studentId: 3,
        type: 'INTERNSHIP',
        fromStatus: null,
        toStatus: 'NOT_STARTED',
        changedBy: 3,
        changedByName: 'สมชาย ใจดี',
        changedAt: '2026-11-06T09:00:00',
        note: null
    }
];

const DETAIL = {
    student: STUDENT,
    application: APPLICATION,
    preferences: [{
        rank: 1,
        positionId: 2,
        positionName: 'Software Developer',
        companyName: 'บริษัท ทดสอบ จำกัด',
        resultStatus: 'MATCHED'
    }],
    match: MATCH,
    internship: INTERNSHIP,
    timeline: TIMELINE,
    stage: 'READY',
    attention: null
};

const ROWS = [{
        studentId: 3,
        studentCode: '6609650001',
        studentName: 'สมชาย ใจดี',
        advisorName: 'อ.ดร.วิชัย สอนดี',
        companyId: null,
        companyName: null,
        positionTitle: null,
        stage: 'APPLYING',
        internshipStatus: null,
        attention: null,
        startDate: null,
        updatedAt: '2026-11-03T09:00:00'
    },
    {
        studentId: 4,
        studentCode: '6609650002',
        studentName: 'สมหญิง รักเรียน',
        advisorName: 'อ.ดร.วิชัย สอนดี',
        companyId: 2,
        companyName: 'บริษัท ทดสอบ จำกัด',
        positionTitle: 'Software Developer',
        stage: 'MATCHING',
        internshipStatus: null,
        attention: 'NOT_CONFIRMED',
        startDate: null,
        updatedAt: '2026-11-05T09:00:00'
    },
    {
        studentId: 5,
        studentCode: '6609650003',
        studentName: 'สมปอง ตั้งใจ',
        advisorName: 'อ.ดร.วิชัย สอนดี',
        companyId: 2,
        companyName: 'บริษัท ทดสอบ จำกัด',
        positionTitle: 'QA Engineer',
        stage: 'IN_PROGRESS',
        internshipStatus: 'IN_PROGRESS',
        attention: null,
        startDate: '2026-11-01',
        updatedAt: '2026-11-08T09:00:00'
    }
];

const SUMMARY = {
    NOT_APPLIED: 1,
    APPLYING: 4,
    WAITING_MATCH: 2,
    MATCHING: 2,
    READY: 1,
    IN_PROGRESS: 5,
    COMPLETED: 1,
    REJECTED: 0,
    TERMINATED: 0
};

function pageResponse(
    content = ROWS,
    page = 0,
    totalPages = 3,
    totalElements = 45
) {
    return {
        content,
        page,
        size: 20,
        totalElements,
        totalPages
    };
}

function reply(status, body) {
    return {
        status,
        ok: status >= 200 && status < 300,

        json: vi.fn()
            .mockResolvedValue(body),

        blob: vi.fn()
            .mockResolvedValue(
                new Blob(['test csv'])
            )
    };
}

function pathOf(url) {
    const parsed = new URL(
        String(url),
        'http://localhost'
    );

    return parsed.pathname + parsed.search;
}

function defaultApi(url, options = {}) {
    const path = pathOf(url);
    const method = options.method || 'GET';

    if (
        path ===
        '/api/students/status/summary'
    ) {
        return Promise.resolve(
            reply(200, SUMMARY)
        );
    }

    if (
        path.startsWith(
            '/api/students/status/export'
        )
    ) {
        return Promise.resolve(
            reply(200, 'csv')
        );
    }

    if (
        path.startsWith(
            '/api/students/status?'
        )
    ) {
        const parsed = new URL(
            String(url),
            'http://localhost'
        );

        const page = Number(
            parsed.searchParams.get('page') || 0
        );

        return Promise.resolve(
            reply(
                200,
                pageResponse(
                    ROWS,
                    page,
                    3,
                    45
                )
            )
        );
    }

    if (
        path ===
        '/api/students/3/status'
    ) {
        return Promise.resolve(
            reply(200, DETAIL)
        );
    }

    if (
        path ===
        '/api/students/me/status'
    ) {
        return Promise.resolve(
            reply(200, DETAIL)
        );
    }

    if (
        path === '/api/internships/20' &&
        method === 'PUT'
    ) {
        const body = JSON.parse(
            options.body
        );

        return Promise.resolve(
            reply(
                200, {
                    ...INTERNSHIP,
                    ...body
                }
            )
        );
    }

    if (
        path ===
        '/api/internships/20/start'
    ) {
        return Promise.resolve(
            reply(
                200, {
                    ...INTERNSHIP,
                    status: 'IN_PROGRESS'
                }
            )
        );
    }

    if (
        path ===
        '/api/internships/20/complete'
    ) {
        return Promise.resolve(
            reply(
                200, {
                    ...INTERNSHIP,
                    status: 'COMPLETED'
                }
            )
        );
    }

    if (
        path ===
        '/api/internships/20/terminate'
    ) {
        return Promise.resolve(
            reply(
                200, {
                    ...INTERNSHIP,
                    status: 'TERMINATED'
                }
            )
        );
    }

    return Promise.resolve(
        reply(
            404, {
                code: 'NOT_FOUND',
                message: 'ไม่พบ API'
            }
        )
    );
}

function waitForPage(milliseconds = 40) {
    return new Promise(resolve => {
        setTimeout(resolve, milliseconds);
    });
}

async function openPage({
    page = 'staff',
    role = 'STAFF',
    api = defaultApi
} = {}) {
    const html =
        page === 'student' ?
        STUDENT_HTML :
        STAFF_HTML;

    const filename =
        page === 'student' ?
        'my-status.html' :
        'student-status.html';

    const dom = new JSDOM(
        html, {
            url: 'http://localhost/' +
                filename,

            runScripts: 'outside-only'
        }
    );

    const window = dom.window;

    openedWindows.push(window);

    if (role) {
        const userId =
            role === 'STUDENT' ?
            3 :
            role === 'ADVISOR' ?
            2 :
            1;

        const username =
            role === 'STUDENT' ?
            '6609650001' :
            role === 'ADVISOR' ?
            'advisor01' :
            'staff01';

        window.sessionStorage.setItem(
            'u5-user',
            JSON.stringify({
                id: userId,
                role,
                name: role === 'STUDENT' ?
                    'สมชาย ใจดี' :
                    'ผู้ทดสอบ',
                username
            })
        );
    }

    window.fetch = vi.fn(
        (url, options = {}) =>
        api(url, options)
    );

    window.confirm = vi.fn(
        () => true
    );

    window.URL.createObjectURL =
        vi.fn(() => 'blob:test');

    window.URL.revokeObjectURL =
        vi.fn();

    window.HTMLAnchorElement
        .prototype
        .click = vi.fn();

    window.eval(U7_JS);

    const go = vi.fn();

    window.U7.go = go;

    window.document.dispatchEvent(
        new window.Event(
            'DOMContentLoaded'
        )
    );

    await waitForPage();

    return {
        window,
        go
    };
}

function listApiCalls(window) {
    return window.fetch.mock.calls
        .filter(call => {
            const path = pathOf(call[0]);

            return path.startsWith(
                '/api/students/status?'
            );
        });
}

function mutationCalls(
    window,
    method,
    ending
) {
    return window.fetch.mock.calls
        .filter(call => {
            const path = pathOf(call[0]);
            const options = call[1] || {};

            return (
                path.endsWith(ending) &&
                options.method === method
            );
        });
}

async function openDetail(
    window
) {
    const button =
        window.document.querySelector(
            '[data-student-id="3"]'
        );

    button.click();

    await waitForPage();
}

afterEach(() => {
    for (const window of openedWindows) {
        window.close();
    }

    openedWindows.length = 0;
    vi.restoreAllMocks();
});

describe(
    'U7 หน้ารายการสถานะนักศึกษา',
    () => {
        test(
            'UI-01 แสดงรายการและเรียก API พร้อม X-User-Id',
            async() => {
                const { window } =
                await openPage();

                const text =
                    window.document
                    .querySelector(
                        '#studentRows'
                    )
                    .textContent;

                expect(text)
                    .toContain('6609650001');

                expect(text)
                    .toContain('สมชาย ใจดี');

                expect(text)
                    .toContain(
                        'บริษัท ทดสอบ จำกัด'
                    );

                const call =
                    listApiCalls(window)[0];

                expect(pathOf(call[0]))
                    .toBe(
                        '/api/students/status?page=0'
                    );

                expect(
                    call[1].headers[
                        'X-User-Id'
                    ]
                ).toBe('1');
            }
        );

        test(
            'UI-02 แสดงจำนวน Summary แต่ละขั้น',
            async() => {
                const { window } =
                await openPage();

                expect(
                    window.document
                    .querySelector(
                        '[data-count="APPLYING"]'
                    )
                    .textContent
                ).toBe('4');

                expect(
                    window.document
                    .querySelector(
                        '[data-count="IN_PROGRESS"]'
                    )
                    .textContent
                ).toBe('5');

                expect(
                    window.document
                    .querySelector(
                        '[data-count="REJECTED"]'
                    )
                    .textContent
                ).toBe('0');
            }
        );

        test(
            'UI-03 กรอง Stage และค้นหารหัสนักศึกษา',
            async() => {
                const { window } =
                await openPage();

                window.document
                    .querySelector(
                        '[data-stage="MATCHING"]'
                    )
                    .click();

                await waitForPage();

                window.document
                    .querySelector('#search')
                    .value = '6501';

                window.document
                    .querySelector(
                        '#btnSearch'
                    )
                    .click();

                await waitForPage();

                const calls =
                    listApiCalls(window);

                const lastCall =
                    calls[calls.length - 1];

                const path =
                    pathOf(lastCall[0]);

                expect(path)
                    .toContain(
                        'stage=MATCHING'
                    );

                expect(path)
                    .toContain('q=6501');

                expect(path)
                    .toContain('page=0');
            }
        );

        test(
            'UI-04 แบ่งหน้าและกดหน้าถัดไป',
            async() => {
                const { window } =
                await openPage();

                expect(
                    window.document
                    .querySelector(
                        '#pageText'
                    )
                    .textContent
                ).toContain('หน้า 1 จาก 3');

                window.document
                    .querySelector(
                        '#btnNext'
                    )
                    .click();

                await waitForPage();

                const calls =
                    listApiCalls(window);

                const lastCall =
                    calls[calls.length - 1];

                expect(pathOf(lastCall[0]))
                    .toContain('page=1');
            }
        );

        test(
            'UI-05 แสดงป้ายเตือนและไฮไลต์แถว',
            async() => {
                const { window } =
                await openPage();

                const row =
                    window.document
                    .querySelector(
                        'tr.attn'
                    );

                expect(row)
                    .not.toBeNull();

                expect(row.textContent)
                    .toContain(
                        'ยังไม่ยืนยันผลจับคู่'
                    );
            }
        );

        test(
            'UI-06 แสดงข้อความเมื่อไม่พบรายการ',
            async() => {
                const api =
                    (url, options) => {
                        const path =
                            pathOf(url);

                        if (
                            path ===
                            '/api/students/status/summary'
                        ) {
                            return Promise.resolve(
                                reply(
                                    200,
                                    SUMMARY
                                )
                            );
                        }

                        return Promise.resolve(
                            reply(
                                200,
                                pageResponse(
                                    [],
                                    0,
                                    1,
                                    0
                                )
                            )
                        );
                    };

                const { window } =
                await openPage({ api });

                expect(
                    window.document
                    .querySelector(
                        '#emptyMsg'
                    )
                    .hidden
                ).toBe(false);

                expect(
                    window.document
                    .querySelector(
                        '#emptyMsg'
                    )
                    .textContent
                ).toContain(
                    'ไม่พบนักศึกษา'
                );
            }
        );

        test(
            'UI-07 อาจารย์ดูได้อย่างเดียว',
            async() => {
                const { window } =
                await openPage({
                    role: 'ADVISOR'
                });

                await openDetail(window);

                expect(
                    window.document
                    .querySelector(
                        '#internshipActions'
                    )
                    .hidden
                ).toBe(true);

                expect(
                    window.document
                    .querySelector(
                        '#iStartDate'
                    )
                    .disabled
                ).toBe(true);

                expect(
                    window.document
                    .querySelector(
                        '#advisorFilter'
                    )
                ).toBeNull();
            }
        );

        test(
            'UI-08 ดาวน์โหลด CSV ตาม Stage',
            async() => {
                const { window } =
                await openPage();

                window.document
                    .querySelector(
                        '[data-stage="IN_PROGRESS"]'
                    )
                    .click();

                await waitForPage();

                window.document
                    .querySelector(
                        '#btnExport'
                    )
                    .click();

                await waitForPage();

                const call =
                    window.fetch.mock.calls
                    .find(item =>
                        pathOf(item[0])
                        .includes(
                            '/status/export'
                        )
                    );

                expect(pathOf(call[0]))
                    .toContain(
                        'stage=IN_PROGRESS'
                    );

                expect(
                    call[1].headers[
                        'X-User-Id'
                    ]
                ).toBe('1');
            }
        );
    }
);

describe(
    'U7 หน้ารายละเอียดและข้อมูลการฝึกงาน',
    () => {
        test(
            'UI-09 แสดงรายละเอียดและ Timeline',
            async() => {
                const { window } =
                await openPage();

                await openDetail(window);

                expect(
                    window.document
                    .querySelector(
                        '#dStudent'
                    )
                    .textContent
                ).toContain('สมชาย ใจดี');

                expect(
                    window.document
                    .querySelector(
                        '#dApplication'
                    )
                    .textContent
                ).toContain(
                    'คำร้องขอฝึกสหกิจศึกษา'
                );

                expect(
                    window.document
                    .querySelectorAll(
                        '#timeline li'
                    ).length
                ).toBe(2);
            }
        );

        test(
            'UI-10 ไม่มีใบสมัครหรือผลจับคู่ไม่เกิด Error',
            async() => {
                const emptyDetail = {
                    ...DETAIL,
                    application: null,
                    preferences: [],
                    match: null,
                    internship: null,
                    timeline: [],
                    stage: 'NOT_APPLIED'
                };

                const api =
                    (url, options) => {
                        const path =
                            pathOf(url);

                        if (
                            path ===
                            '/api/students/3/status'
                        ) {
                            return Promise.resolve(
                                reply(
                                    200,
                                    emptyDetail
                                )
                            );
                        }

                        return defaultApi(
                            url,
                            options
                        );
                    };

                const { window } =
                await openPage({ api });

                await openDetail(window);

                expect(
                    window.document
                    .querySelector(
                        '#dApplication'
                    )
                    .textContent
                ).toContain(
                    'ยังไม่ยื่นใบสมัคร'
                );

                expect(
                    window.document
                    .querySelector(
                        '#noInternship'
                    )
                    .hidden
                ).toBe(false);
            }
        );

        test(
            'UI-11 วันสิ้นสุดก่อนวันเริ่มไม่เรียก API',
            async() => {
                const { window } =
                await openPage();

                await openDetail(window);

                window.document
                    .querySelector(
                        '#iStartDate'
                    )
                    .value = '2027-03-31';

                window.document
                    .querySelector(
                        '#iEndDate'
                    )
                    .value = '2026-12-01';

                window.document
                    .querySelector(
                        '#btnSaveInternship'
                    )
                    .click();

                await waitForPage();

                expect(
                    window.document
                    .querySelector(
                        '#dateError'
                    )
                    .hidden
                ).toBe(false);

                expect(
                    mutationCalls(
                        window,
                        'PUT',
                        '/api/internships/20'
                    ).length
                ).toBe(0);
            }
        );

        test(
            'UI-12 บันทึกข้อมูลการฝึกงานครบ 5 Field',
            async() => {
                const { window } =
                await openPage();

                await openDetail(window);

                window.document
                    .querySelector(
                        '#iStartDate'
                    )
                    .value = '2026-12-01';

                window.document
                    .querySelector(
                        '#iEndDate'
                    )
                    .value = '2027-03-31';

                window.document
                    .querySelector(
                        '#iSupervisorName'
                    )
                    .value = 'คุณทดสอบ';

                window.document
                    .querySelector(
                        '#iSupervisorEmail'
                    )
                    .value =
                    'test@example.com';

                window.document
                    .querySelector(
                        '#iSupervisorPhone'
                    )
                    .value = '0899999999';

                window.document
                    .querySelector(
                        '#btnSaveInternship'
                    )
                    .click();

                await waitForPage();

                const call =
                    mutationCalls(
                        window,
                        'PUT',
                        '/api/internships/20'
                    )[0];

                const body =
                    JSON.parse(
                        call[1].body
                    );

                expect(body).toEqual({
                    startDate: '2026-12-01',
                    endDate: '2027-03-31',
                    supervisorName: 'คุณทดสอบ',
                    supervisorEmail: 'test@example.com',
                    supervisorPhone: '0899999999'
                });
            }
        );

        test(
            'UI-13 เริ่มฝึกงานหลังยืนยัน',
            async() => {
                const { window } =
                await openPage();

                await openDetail(window);

                window.confirm =
                    vi.fn()
                    .mockReturnValueOnce(false)
                    .mockReturnValueOnce(true);

                window.document
                    .querySelector(
                        '#btnStartInternship'
                    )
                    .click();

                await waitForPage();

                expect(
                    mutationCalls(
                        window,
                        'POST',
                        '/api/internships/20/start'
                    ).length
                ).toBe(0);

                window.document
                    .querySelector(
                        '#btnStartInternship'
                    )
                    .click();

                await waitForPage();

                expect(
                    mutationCalls(
                        window,
                        'POST',
                        '/api/internships/20/start'
                    ).length
                ).toBe(1);
            }
        );

        test(
            'UI-14 ยุติการฝึกงานต้องกรอกเหตุผล',
            async() => {
                const { window } =
                await openPage();

                await openDetail(window);

                window.document
                    .querySelector(
                        '#btnShowTerminate'
                    )
                    .click();

                window.document
                    .querySelector(
                        '#btnTerminateInternship'
                    )
                    .click();

                await waitForPage();

                expect(
                    window.document
                    .querySelector(
                        '#terminateError'
                    )
                    .hidden
                ).toBe(false);

                expect(
                    mutationCalls(
                        window,
                        'POST',
                        '/api/internships/20/terminate'
                    ).length
                ).toBe(0);

                window.document
                    .querySelector(
                        '#terminateReason'
                    )
                    .value =
                    'บริษัทยกเลิกโครงการ';

                window.document
                    .querySelector(
                        '#btnTerminateInternship'
                    )
                    .click();

                await waitForPage();

                const call =
                    mutationCalls(
                        window,
                        'POST',
                        '/api/internships/20/terminate'
                    )[0];

                expect(
                    JSON.parse(
                        call[1].body
                    ).reason
                ).toBe(
                    'บริษัทยกเลิกโครงการ'
                );
            }
        );

        test(
            'UI-15 สถานะปิดแล้วแก้ไขไม่ได้',
            async() => {
                const closedDetail = {
                    ...DETAIL,
                    stage: 'COMPLETED',
                    internship: {
                        ...INTERNSHIP,
                        status: 'COMPLETED'
                    }
                };

                const api =
                    (url, options) => {
                        if (
                            pathOf(url) ===
                            '/api/students/3/status'
                        ) {
                            return Promise.resolve(
                                reply(
                                    200,
                                    closedDetail
                                )
                            );
                        }

                        return defaultApi(
                            url,
                            options
                        );
                    };

                const { window } =
                await openPage({ api });

                await openDetail(window);

                expect(
                    window.document
                    .querySelector(
                        '#internshipActions'
                    )
                    .hidden
                ).toBe(true);

                expect(
                    window.document
                    .querySelector(
                        '#iStartDate'
                    )
                    .disabled
                ).toBe(true);
            }
        );

        test(
            'UI-16 แสดง Error เมื่อเริ่มฝึกงานไม่ได้',
            async() => {
                const api =
                    (url, options = {}) => {
                        if (
                            pathOf(url) ===
                            '/api/internships/20/start'
                        ) {
                            return Promise.resolve(
                                reply(
                                    400, {
                                        code: 'MISSING_INFO',
                                        message: 'กรุณากรอกข้อมูลให้ครบ'
                                    }
                                )
                            );
                        }

                        return defaultApi(
                            url,
                            options
                        );
                    };

                const { window } =
                await openPage({ api });

                await openDetail(window);

                window.document
                    .querySelector(
                        '#btnStartInternship'
                    )
                    .click();

                await waitForPage();

                expect(
                    window.document
                    .querySelector(
                        '#detailErr'
                    )
                    .hidden
                ).toBe(false);

                expect(
                    window.document
                    .querySelector(
                        '#detailErr'
                    )
                    .textContent
                ).toContain(
                    'กรุณากรอกข้อมูลให้ครบ'
                );

                expect(
                    window.document
                    .querySelector(
                        '#internshipStatus'
                    )
                    .textContent
                ).toContain('ยังไม่เริ่ม');
            }
        );

        test(
            'UI-17 อาจารย์เปิดนักศึกษาที่ไม่มีสิทธิ์ได้ 403',
            async() => {
                const api =
                    (url, options) => {
                        if (
                            pathOf(url) ===
                            '/api/students/3/status'
                        ) {
                            return Promise.resolve(
                                reply(
                                    403, {
                                        code: 'FORBIDDEN',
                                        message: 'คุณไม่มีสิทธิ์'
                                    }
                                )
                            );
                        }

                        return defaultApi(
                            url,
                            options
                        );
                    };

                const { window } =
                await openPage({
                    role: 'ADVISOR',
                    api
                });

                await openDetail(window);

                expect(
                    window.document
                    .querySelector(
                        '#detailErr'
                    )
                    .textContent
                ).toContain(
                    'คุณไม่มีสิทธิ์ดูข้อมูลนักศึกษาคนนี้'
                );

                expect(
                    window.document
                    .querySelector(
                        '#detailContent'
                    )
                    .hidden
                ).toBe(true);
            }
        );
    }
);

describe(
    'U7 หน้าสถานะของนักศึกษาและกรณีทั่วไป',
    () => {
        test(
            'UI-18 นักศึกษาเห็นขั้น READY เป็นขั้นปัจจุบัน',
            async() => {
                const { window } =
                await openPage({
                    page: 'student',
                    role: 'STUDENT'
                });

                const current =
                    window.document
                    .querySelector(
                        '[data-progress="READY"]'
                    );

                expect(
                    current.classList
                    .contains('current')
                ).toBe(true);

                expect(
                    window.document
                    .querySelector(
                        '#myStageTitle'
                    )
                    .textContent
                ).toContain(
                    'พร้อมฝึกงาน'
                );

                expect(
                    window.document
                    .querySelector(
                        '#myInternship'
                    )
                    .textContent
                ).toContain(
                    'คุณผู้ดูแล'
                );
            }
        );

        test(
            'UI-19 นักศึกษาเปิดหน้ารายการไม่ได้',
            async() => {
                const { window } =
                await openPage({
                    role: 'STUDENT'
                });

                expect(
                    window.document
                    .querySelector(
                        '#vDenied'
                    )
                    .hidden
                ).toBe(false);

                expect(
                    listApiCalls(window).length
                ).toBe(0);
            }
        );

        test(
            'UI-20 ไม่มีข้อมูล Login ถูกส่งไปหน้า Login',
            async() => {
                const { go } =
                await openPage({
                    role: null
                });

                expect(go)
                    .toHaveBeenCalledWith(
                        'login.html'
                    );
            }
        );

        test(
            'UI-21 Backend ไม่ทำงานแสดงข้อความเชื่อมต่อไม่ได้',
            async() => {
                const api = () =>
                    Promise.reject(
                        new Error(
                            'network error'
                        )
                    );

                const { window } =
                await openPage({
                    page: 'student',
                    role: 'STUDENT',
                    api
                });

                expect(
                    window.document
                    .querySelector(
                        '#myError'
                    )
                    .hidden
                ).toBe(false);

                expect(
                    window.document
                    .querySelector(
                        '#myError'
                    )
                    .textContent
                ).toContain(
                    'ไม่สามารถเชื่อมต่อระบบได้'
                );
            }
        );
    }
);