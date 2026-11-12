/* =========================================================
   U7: ติดตามสถานะนักศึกษาและการฝึกงาน
   ========================================================= */

(function() {
    'use strict';

    const API_BASE =
        window.U7_API_BASE || 'http://localhost:8080';

    const STAGE_LABEL = {
        NOT_APPLIED: 'ยังไม่ยื่นใบสมัคร',
        APPLYING: 'กำลังยื่นใบสมัคร',
        WAITING_MATCH: 'รอการจับคู่',
        MATCHING: 'กำลังจับคู่',
        READY: 'พร้อมฝึกงาน',
        IN_PROGRESS: 'กำลังฝึกงาน',
        COMPLETED: 'เสร็จสิ้น',
        REJECTED: 'ไม่ได้รับการอนุมัติ',
        TERMINATED: 'ยุติการฝึกงาน'
    };

    const STAGE_MESSAGE = {
        NOT_APPLIED: 'ยังไม่มีใบสมัครฝึกงานหรือสหกิจศึกษา',
        APPLYING: 'ใบสมัครอยู่ระหว่างดำเนินการและพิจารณา',
        WAITING_MATCH: 'ใบสมัครได้รับการอนุมัติแล้ว กำลังรอจับคู่บริษัท',
        MATCHING: 'มีผลจับคู่แล้ว กรุณาตรวจสอบและยืนยันสิทธิ์',
        READY: 'ยืนยันผลจับคู่แล้ว กรุณารอวันเริ่มฝึกงาน',
        IN_PROGRESS: 'กำลังฝึกงานหรือสหกิจศึกษาตามกำหนด',
        COMPLETED: 'ดำเนินการฝึกงานหรือสหกิจศึกษาเสร็จสิ้นแล้ว',
        REJECTED: 'ใบสมัครไม่ได้รับการอนุมัติ',
        TERMINATED: 'การฝึกงานถูกยุติก่อนกำหนด'
    };

    const ATTENTION_LABEL = {
        NO_MATCH: 'อนุมัติเกิน 14 วัน แต่ยังไม่ได้จับคู่',
        NOT_CONFIRMED: 'ยังไม่ยืนยันผลจับคู่',
        MISSING_INFO: 'ข้อมูลการฝึกงานยังไม่ครบ',
        START_OVERDUE: 'เลยวันเริ่มฝึกงานแล้ว'
    };

    const INTERNSHIP_LABEL = {
        NOT_STARTED: 'ยังไม่เริ่ม',
        IN_PROGRESS: 'กำลังฝึกงาน',
        COMPLETED: 'เสร็จสิ้น',
        TERMINATED: 'ยุติก่อนกำหนด'
    };

    const APPLICATION_LABEL = {
        DRAFT: 'ฉบับร่าง',
        SUBMITTED: 'ยื่นแล้ว รอเจ้าหน้าที่ตรวจสอบ',
        UNDER_REVIEW: 'รออาจารย์พิจารณา',
        NEED_CORRECTION: 'ส่งกลับให้แก้ไข',
        APPROVED: 'อนุมัติแล้ว',
        REJECTED: 'ไม่อนุมัติ'
    };

    const HISTORY_LABEL = {
        APPLICATION: 'ใบสมัคร',
        MATCH: 'ผลการจับคู่',
        INTERNSHIP: 'การฝึกงาน'
    };

    const $ = (selector, root = document) =>
        root.querySelector(selector);

    const $$ = (selector, root = document) =>
        Array.from(root.querySelectorAll(selector));

    function readJson(key) {
        try {
            return JSON.parse(
                sessionStorage.getItem(key) || 'null'
            );
        } catch (error) {
            return null;
        }
    }

    function currentUser() {
        const user = readJson('u5-user');

        if (user && user.id != null) {
            return {
                id: user.id,
                role: user.role,
                name: user.name ||
                    user.fullName ||
                    user.username ||
                    '',
                username: user.username || ''
            };
        }

        const auth = readJson('u1-auth');

        if (auth && auth.userId != null) {
            return {
                id: auth.userId,
                role: auth.role,
                name: auth.fullName ||
                    auth.username ||
                    '',
                username: auth.username || ''
            };
        }

        return null;
    }

    function logout() {
        sessionStorage.removeItem('u1-auth');
        sessionStorage.removeItem('u5-user');
        sessionStorage.removeItem('coop-state');

        window.U7.go('login.html');
    }

    function setupShell(user) {
        const who = $('#who');
        const logoutButton = $('#logout');

        if (who) {
            who.textContent =
                user.name || user.username || '';
        }

        if (logoutButton) {
            logoutButton.addEventListener(
                'click',
                logout
            );
        }
    }

    async function request(
        path,
        options = {},
        asBlob = false
    ) {
        const user = currentUser();

        const headers = {
            ...(options.headers || {})
        };

        if (user && user.id != null) {
            headers['X-User-Id'] =
                String(user.id);
        }

        if (
            options.body != null &&
            !headers['Content-Type']
        ) {
            headers['Content-Type'] =
                'application/json';
        }

        let response;

        try {
            response = await fetch(
                API_BASE + path, {
                    ...options,
                    headers
                }
            );
        } catch (error) {
            const networkError =
                new Error(
                    'ไม่สามารถเชื่อมต่อระบบได้'
                );

            networkError.network = true;
            throw networkError;
        }

        if (asBlob && response.ok) {
            return {
                status: response.status,
                data: await response.blob(),
                response
            };
        }

        let data = null;

        try {
            data = await response.json();
        } catch (error) {
            data = null;
        }

        if (!response.ok) {
            const apiError =
                new Error(
                    data && data.message ?
                    data.message :
                    'เกิดข้อผิดพลาดจากระบบ'
                );

            apiError.status = response.status;

            apiError.code =
                data && data.code ?
                data.code :
                null;

            throw apiError;
        }

        return {
            status: response.status,
            data,
            response
        };
    }

    function escapeHtml(value) {
        return String(
                value == null ? '' : value
            )
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString(
            'th-TH', {
                dateStyle: 'medium',
                timeStyle: 'short'
            }
        );
    }

    function formatDate(value) {
        if (!value) {
            return '-';
        }

        const text = String(value);

        const date = new Date(
            text.includes('T') ?
            text :
            text + 'T00:00:00'
        );

        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleDateString(
            'th-TH', {
                dateStyle: 'medium'
            }
        );
    }

    function stageClass(stage) {
        return String(stage || '')
            .toLowerCase()
            .replace(/_/g, '-');
    }

    function badgeHtml(stage) {
        return (
            '<span class="badge ' +
            stageClass(stage) +
            '">' +
            escapeHtml(
                STAGE_LABEL[stage] ||
                stage ||
                '-'
            ) +
            '</span>'
        );
    }

    function kvHtml(items) {
        return items.map(item =>
            '<dt>' +
            escapeHtml(item[0]) +
            '</dt><dd>' +
            escapeHtml(
                item[1] == null ||
                item[1] === '' ?
                '-' :
                item[1]
            ) +
            '</dd>'
        ).join('');
    }

    function showMessage(
        element,
        message
    ) {
        if (!element) {
            return;
        }

        element.textContent = message;
        element.hidden = false;
    }

    function hideMessage(element) {
        if (!element) {
            return;
        }

        element.hidden = true;
        element.textContent = '';
    }

    function errorMessage(error) {
        if (error && error.network) {
            return (
                'ไม่สามารถเชื่อมต่อระบบได้ ' +
                'กรุณาลองใหม่อีกครั้ง'
            );
        }

        return error && error.message ?
            error.message :
            'เกิดข้อผิดพลาด กรุณาลองใหม่';
    }

    /* =====================================================
       หน้ารายการ STAFF / ADVISOR
       ===================================================== */

    const listState = {
        stage: '',
        companyId: '',
        q: '',
        page: 0,
        totalPages: 1,
        selectedStudentId: null,
        detail: null
    };

    async function initStudentStatus(user) {
        if (
            user.role !== 'STAFF' &&
            user.role !== 'ADVISOR'
        ) {
            $('#vDenied').hidden = false;
            return;
        }

        $('#vList').hidden = false;

        bindListEvents(user);
        bindDetailEvents(user);

        await loadList();
    }

    function bindListEvents(user) {
        $$('.stage-tab').forEach(button => {
            button.addEventListener(
                'click',
                async() => {
                    $$('.stage-tab')
                        .forEach(item =>
                            item.classList.remove('on')
                        );

                    button.classList.add('on');

                    listState.stage =
                        button.dataset.stage || '';

                    listState.page = 0;

                    await loadList();
                }
            );
        });

        $('#btnSearch').addEventListener(
            'click',
            async() => {
                listState.q =
                    $('#search').value.trim();

                listState.companyId =
                    $('#companyFilter')
                    .value
                    .trim();

                listState.page = 0;

                await loadList();
            }
        );

        $('#search').addEventListener(
            'keydown',
            event => {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    $('#btnSearch').click();
                }
            }
        );

        $('#btnPrev').addEventListener(
            'click',
            async() => {
                if (listState.page > 0) {
                    listState.page--;
                    await loadList();
                }
            }
        );

        $('#btnNext').addEventListener(
            'click',
            async() => {
                if (
                    listState.page + 1 <
                    listState.totalPages
                ) {
                    listState.page++;
                    await loadList();
                }
            }
        );

        $('#btnExport').addEventListener(
            'click',
            downloadCsv
        );

        if (user.role === 'ADVISOR') {
            $('#companyFilter').setAttribute(
                'title',
                'รายการถูกจำกัดเฉพาะนักศึกษาที่ท่านดูแล'
            );
        }
    }

    async function loadList() {
        const loading = $('#listLoading');
        const content = $('#listContent');
        const errorBox = $('#listErr');

        loading.hidden = false;
        content.hidden = true;

        hideMessage(errorBox);

        const params = new URLSearchParams();

        if (listState.stage) {
            params.set(
                'stage',
                listState.stage
            );
        }

        if (listState.companyId) {
            params.set(
                'companyId',
                listState.companyId
            );
        }

        if (listState.q) {
            params.set(
                'q',
                listState.q
            );
        }

        params.set(
            'page',
            String(listState.page)
        );

        try {
            const [
                listResponse,
                summaryResponse
            ] = await Promise.all([
                request(
                    '/api/students/status?' +
                    params.toString()
                ),
                request(
                    '/api/students/status/summary'
                )
            ]);

            renderSummary(
                summaryResponse.data || {}
            );

            renderRows(
                listResponse.data || {}
            );

            loading.hidden = true;
            content.hidden = false;

        } catch (error) {
            loading.hidden = true;

            if (error.status === 401) {
                window.U7.go('login.html');
                return;
            }

            if (error.status === 403) {
                $('#vList').hidden = true;
                $('#vDenied').hidden = false;
                return;
            }

            showMessage(
                errorBox,
                errorMessage(error)
            );
        }
    }

    function renderSummary(summary) {
        let total = 0;

        Object.keys(STAGE_LABEL)
            .forEach(stage => {
                const count =
                    Number(summary[stage]) || 0;

                total += count;

                const element =
                    $('[data-count="' +
                        stage +
                        '"]');

                if (element) {
                    element.textContent =
                        String(count);
                }
            });

        $('#countAll').textContent =
            String(total);
    }

    function renderRows(pageData) {
        const rows = Array.isArray(
                pageData.content
            ) ?
            pageData.content :
            [];

        const tbody = $('#studentRows');

        tbody.innerHTML = rows
            .map(row => {
                const place = [
                        row.companyName,
                        row.positionTitle
                    ]
                    .filter(Boolean)
                    .join(' · ') || '-';

                const attention =
                    row.attention ?
                    '<span class="attention">' +
                    escapeHtml(
                        ATTENTION_LABEL[
                            row.attention
                        ] ||
                        row.attention
                    ) +
                    '</span>' :
                    '-';

                return (
                    '<tr class="' +
                    (row.attention ?
                        'attn' :
                        '') +
                    '">' +
                    '<td>' +
                    escapeHtml(row.studentCode) +
                    '</td>' +
                    '<td>' +
                    '<button type="button" ' +
                    'class="student-link" ' +
                    'data-student-id="' +
                    escapeHtml(row.studentId) +
                    '">' +
                    escapeHtml(row.studentName) +
                    '</button>' +
                    '</td>' +
                    '<td>' +
                    escapeHtml(
                        row.advisorName || '-'
                    ) +
                    '</td>' +
                    '<td>' +
                    escapeHtml(place) +
                    '</td>' +
                    '<td>' +
                    badgeHtml(row.stage) +
                    '</td>' +
                    '<td>' +
                    escapeHtml(
                        formatDateTime(
                            row.updatedAt
                        )
                    ) +
                    '</td>' +
                    '<td>' +
                    attention +
                    '</td>' +
                    '</tr>'
                );
            })
            .join('');

        $('#emptyMsg').hidden =
            rows.length !== 0;

        listState.page =
            Number(pageData.page) || 0;

        listState.totalPages =
            Math.max(
                Number(pageData.totalPages) || 1,
                1
            );

        $('#pageText').textContent =
            'หน้า ' +
            (listState.page + 1) +
            ' จาก ' +
            listState.totalPages;

        $('#btnPrev').disabled =
            listState.page <= 0;

        $('#btnNext').disabled =
            listState.page + 1 >=
            listState.totalPages;

        $$('.student-link', tbody)
            .forEach(button => {
                button.addEventListener(
                    'click',
                    () => openDetail(
                        button.dataset.studentId
                    )
                );
            });
    }

    async function downloadCsv() {
        const button = $('#btnExport');
        const oldLabel = button.textContent;

        button.disabled = true;
        button.textContent =
            'กำลังดาวน์โหลด...';

        let path =
            '/api/students/status/export';

        if (listState.stage) {
            path +=
                '?stage=' +
                encodeURIComponent(
                    listState.stage
                );
        }

        try {
            const result =
                await request(
                    path, {},
                    true
                );

            const url =
                URL.createObjectURL(
                    result.data
                );

            const link =
                document.createElement('a');

            link.href = url;

            link.download =
                listState.stage ?
                'student-status-' +
                listState.stage
                .toLowerCase() +
                '.csv' :
                'student-status.csv';

            document.body.appendChild(link);
            link.click();
            link.remove();

            URL.revokeObjectURL(url);

        } catch (error) {
            showMessage(
                $('#listErr'),
                errorMessage(error)
            );
        } finally {
            button.disabled = false;
            button.textContent = oldLabel;
        }
    }

    /* =====================================================
       หน้ารายละเอียด
       ===================================================== */

    function bindDetailEvents(user) {
        $('#btnBack').addEventListener(
            'click',
            () => {
                $('#vDetail').hidden = true;
                $('#vList').hidden = false;

                listState.selectedStudentId =
                    null;

                listState.detail = null;
            }
        );

        $('#btnSaveInternship')
            .addEventListener(
                'click',
                saveInternship
            );

        $('#btnStartInternship')
            .addEventListener(
                'click',
                () => changeInternshipStatus(
                    'start'
                )
            );

        $('#btnCompleteInternship')
            .addEventListener(
                'click',
                () => changeInternshipStatus(
                    'complete'
                )
            );

        $('#btnShowTerminate')
            .addEventListener(
                'click',
                () => {
                    $('#terminateBox').hidden =
                        false;

                    $('#terminateReason').focus();
                }
            );

        $('#btnCancelTerminate')
            .addEventListener(
                'click',
                () => {
                    $('#terminateBox').hidden =
                        true;

                    $('#terminateReason').value =
                        '';

                    $('#terminateError').hidden =
                        true;
                }
            );

        $('#btnTerminateInternship')
            .addEventListener(
                'click',
                terminateInternship
            );

        if (user.role === 'ADVISOR') {
            $('#readonlyNote').hidden = false;
        }
    }

    async function openDetail(studentId) {
        listState.selectedStudentId =
            Number(studentId);

        $('#vList').hidden = true;
        $('#vDetail').hidden = false;

        $('#detailLoading').hidden = false;
        $('#detailContent').hidden = true;

        hideMessage($('#detailErr'));
        hideMessage($('#detailMsg'));

        try {
            const response = await request(
                '/api/students/' +
                encodeURIComponent(studentId) +
                '/status'
            );

            listState.detail =
                response.data;

            renderDetail(response.data);

            $('#detailLoading').hidden = true;
            $('#detailContent').hidden = false;

        } catch (error) {
            $('#detailLoading').hidden = true;

            showMessage(
                $('#detailErr'),
                error.status === 403 ?
                'คุณไม่มีสิทธิ์ดูข้อมูลนักศึกษาคนนี้' :
                errorMessage(error)
            );
        }
    }

    function renderDetail(detail) {
        const student =
            detail.student || {};

        $('#dStudent').innerHTML =
            kvHtml([
                [
                    'รหัสนักศึกษา',
                    student.studentId
                ],
                [
                    'ชื่อ-นามสกุล',
                    student.fullName
                ],
                [
                    'คณะ',
                    student.faculty
                ],
                [
                    'สาขาวิชา',
                    student.major
                ],
                [
                    'เกรดเฉลี่ย',
                    student.gpa
                ],
                [
                    'อีเมล',
                    student.email
                ]
            ]);

        const stageElement = $('#dStage');

        stageElement.className =
            'badge ' +
            stageClass(detail.stage);

        stageElement.textContent =
            STAGE_LABEL[detail.stage] ||
            detail.stage ||
            '-';

        if (detail.attention) {
            $('#dAttention').textContent =
                ATTENTION_LABEL[
                    detail.attention
                ] ||
                detail.attention;

            $('#dAttention').hidden = false;
        } else {
            $('#dAttention').hidden = true;
        }

        renderApplication(
            $('#dApplication'),
            detail.application
        );

        renderPreferences(
            $('#dPreferences'),
            detail.preferences
        );

        renderMatch(
            $('#dMatch'),
            detail.match
        );

        renderInternship(
            detail.internship
        );

        renderTimeline(
            $('#timeline'),
            $('#timelineEmpty'),
            detail.timeline
        );
    }

    function renderApplication(
        container,
        application
    ) {
        if (!application) {
            container.textContent =
                'ยังไม่ยื่นใบสมัคร';
            return;
        }

        container.innerHTML =
            '<dl class="kv">' +
            kvHtml([
                [
                    'เลขที่คำร้อง',
                    application.id
                ],
                [
                    'ชื่อแบบฟอร์ม',
                    application.formName
                ],
                [
                    'สถานะ',
                    APPLICATION_LABEL[
                        application.status
                    ] ||
                    application.status
                ],
                [
                    'วันที่ยื่น',
                    formatDateTime(
                        application.submittedAt
                    )
                ],
                [
                    'เหตุผลที่ส่งกลับ',
                    application.correctionReason
                ]
            ]) +
            '</dl>';
    }

    function renderPreferences(
        container,
        preferences
    ) {
        const rows =
            Array.isArray(preferences) ?
            preferences :
            [];

        if (!rows.length) {
            container.textContent =
                'ยังไม่มีอันดับที่เลือก';
            return;
        }

        container.innerHTML =
            '<ol>' +
            rows.map(item =>
                '<li>' +
                escapeHtml(
                    item.companyName || '-'
                ) +
                ' · ' +
                escapeHtml(
                    item.positionName || '-'
                ) +
                (
                    item.resultStatus ?
                    ' (' +
                    escapeHtml(
                        item.resultStatus
                    ) +
                    ')' :
                    ''
                ) +
                '</li>'
            ).join('') +
            '</ol>';
    }

    function renderMatch(
        container,
        match
    ) {
        if (!match) {
            container.textContent =
                'ยังไม่มีผลการจับคู่';
            return;
        }

        container.innerHTML =
            '<dl class="kv">' +
            kvHtml([
                [
                    'บริษัท',
                    match.companyName
                ],
                [
                    'ตำแหน่ง',
                    match.positionName
                ],
                [
                    'อันดับที่ได้',
                    match.rank
                ],
                [
                    'สถานะ',
                    match.status
                ],
                [
                    'วันที่จับคู่',
                    formatDateTime(
                        match.matchedAt
                    )
                ],
                [
                    'วันที่ตอบรับ',
                    formatDateTime(
                        match.respondedAt
                    )
                ]
            ]) +
            '</dl>';
    }

    function renderInternship(internship) {
        const user = currentUser();

        $('#terminateBox').hidden = true;
        $('#dateError').hidden = true;
        $('#terminateError').hidden = true;

        if (!internship) {
            $('#noInternship').hidden = false;
            $('#internshipPanel').hidden = true;
            return;
        }

        $('#noInternship').hidden = true;
        $('#internshipPanel').hidden = false;

        const status = internship.status;

        const closed =
            status === 'COMPLETED' ||
            status === 'TERMINATED';

        const readOnly = !user ||
            user.role !== 'STAFF' ||
            closed;

        $('#internshipStatus').className =
            'badge ' +
            stageClass(status);

        $('#internshipStatus').textContent =
            INTERNSHIP_LABEL[status] ||
            status ||
            '-';

        $('#iStartDate').value =
            internship.startDate || '';

        $('#iEndDate').value =
            internship.endDate || '';

        $('#iSupervisorName').value =
            internship.supervisorName || '';

        $('#iSupervisorEmail').value =
            internship.supervisorEmail || '';

        $('#iSupervisorPhone').value =
            internship.supervisorPhone || '';

        [
            '#iStartDate',
            '#iEndDate',
            '#iSupervisorName',
            '#iSupervisorEmail',
            '#iSupervisorPhone'
        ].forEach(selector => {
            $(selector).disabled = readOnly;
        });

        $('#readonlyNote').hidden = !user ||
            user.role !== 'ADVISOR';

        $('#internshipActions').hidden =
            readOnly;

        if (readOnly) {
            return;
        }

        $('#btnSaveInternship').hidden =
            false;

        $('#btnStartInternship').hidden =
            status !== 'NOT_STARTED';

        $('#btnCompleteInternship').hidden =
            status !== 'IN_PROGRESS';

        $('#btnShowTerminate').hidden = !(
            status === 'NOT_STARTED' ||
            status === 'IN_PROGRESS'
        );
    }

    async function saveInternship() {
        const internship =
            listState.detail &&
            listState.detail.internship;

        if (!internship) {
            return;
        }

        hideMessage($('#detailErr'));
        hideMessage($('#detailMsg'));

        const startDate =
            $('#iStartDate').value || null;

        const endDate =
            $('#iEndDate').value || null;

        $('#dateError').hidden = true;

        $('#iStartDate')
            .removeAttribute('aria-invalid');

        $('#iEndDate')
            .removeAttribute('aria-invalid');

        if (
            startDate &&
            endDate &&
            endDate <= startDate
        ) {
            $('#dateError').hidden = false;

            $('#iStartDate').setAttribute(
                'aria-invalid',
                'true'
            );

            $('#iEndDate').setAttribute(
                'aria-invalid',
                'true'
            );

            return;
        }

        const body = {
            startDate,
            endDate,
            supervisorName: $('#iSupervisorName')
                .value
                .trim() || null,
            supervisorEmail: $('#iSupervisorEmail')
                .value
                .trim() || null,
            supervisorPhone: $('#iSupervisorPhone')
                .value
                .trim() || null
        };

        const button =
            $('#btnSaveInternship');

        button.disabled = true;

        try {
            await request(
                '/api/internships/' +
                internship.id, {
                    method: 'PUT',
                    body: JSON.stringify(body)
                }
            );

            showMessage(
                $('#detailMsg'),
                'บันทึกข้อมูลการฝึกงานแล้ว'
            );

            await reloadDetail();

        } catch (error) {
            showMessage(
                $('#detailErr'),
                errorMessage(error)
            );
        } finally {
            button.disabled = false;
        }
    }

    async function changeInternshipStatus(
        action
    ) {
        const internship =
            listState.detail &&
            listState.detail.internship;

        if (!internship) {
            return;
        }

        const question =
            action === 'start' ?
            'ยืนยันเริ่มฝึกงานหรือไม่' :
            'ยืนยันว่าการฝึกงานเสร็จสิ้นแล้วหรือไม่';

        if (!window.confirm(question)) {
            return;
        }

        hideMessage($('#detailErr'));
        hideMessage($('#detailMsg'));

        try {
            await request(
                '/api/internships/' +
                internship.id +
                '/' +
                action, {
                    method: 'POST'
                }
            );

            showMessage(
                $('#detailMsg'),
                action === 'start' ?
                'เริ่มฝึกงานเรียบร้อยแล้ว' :
                'บันทึกการฝึกงานเสร็จสิ้นแล้ว'
            );

            await reloadDetail();

        } catch (error) {
            showMessage(
                $('#detailErr'),
                errorMessage(error)
            );
        }
    }

    async function terminateInternship() {
        const internship =
            listState.detail &&
            listState.detail.internship;

        if (!internship) {
            return;
        }

        const reason =
            $('#terminateReason')
            .value
            .trim();

        if (!reason) {
            $('#terminateError').hidden =
                false;
            return;
        }

        $('#terminateError').hidden = true;

        if (!window.confirm(
                'ยืนยันยุติการฝึกงานหรือไม่'
            )) {
            return;
        }

        try {
            await request(
                '/api/internships/' +
                internship.id +
                '/terminate', {
                    method: 'POST',
                    body: JSON.stringify({
                        reason
                    })
                }
            );

            $('#terminateBox').hidden = true;
            $('#terminateReason').value = '';

            showMessage(
                $('#detailMsg'),
                'ยุติการฝึกงานเรียบร้อยแล้ว'
            );

            await reloadDetail();

        } catch (error) {
            showMessage(
                $('#detailErr'),
                errorMessage(error)
            );
        }
    }

    async function reloadDetail() {
        if (
            listState.selectedStudentId == null
        ) {
            return;
        }

        const response = await request(
            '/api/students/' +
            listState.selectedStudentId +
            '/status'
        );

        listState.detail = response.data;

        renderDetail(response.data);
    }

    function renderTimeline(
        container,
        emptyElement,
        timeline
    ) {
        const rows =
            Array.isArray(timeline) ?
            timeline :
            [];

        container.innerHTML = rows
            .map(item =>
                '<li>' +
                '<strong>' +
                escapeHtml(
                    HISTORY_LABEL[item.type] ||
                    item.type ||
                    'สถานะ'
                ) +
                ': ' +
                escapeHtml(
                    item.fromStatus ?
                    item.fromStatus +
                    ' → ' +
                    item.toStatus :
                    item.toStatus
                ) +
                '</strong>' +
                '<time>' +
                escapeHtml(
                    formatDateTime(
                        item.changedAt
                    )
                ) +
                (
                    item.changedByName ?
                    ' · ' +
                    escapeHtml(
                        item.changedByName
                    ) :
                    ''
                ) +
                '</time>' +
                (
                    item.note ?
                    '<div>' +
                    escapeHtml(item.note) +
                    '</div>' :
                    ''
                ) +
                '</li>'
            )
            .join('');

        emptyElement.hidden =
            rows.length !== 0;
    }

    /* =====================================================
       หน้าสถานะของนักศึกษา
       ===================================================== */

    async function initMyStatus(user) {
        if (user.role !== 'STUDENT') {
            $('#myDenied').hidden = false;
            return;
        }

        $('#myView').hidden = false;

        try {
            const response =
                await request(
                    '/api/students/me/status'
                );

            renderMyStatus(response.data);

            $('#myLoading').hidden = true;
            $('#myContent').hidden = false;

        } catch (error) {
            $('#myLoading').hidden = true;

            if (error.status === 401) {
                window.U7.go('login.html');
                return;
            }

            showMessage(
                $('#myError'),
                errorMessage(error)
            );
        }
    }

    function renderMyStatus(detail) {
        renderProgress(detail.stage);

        $('#myStageTitle').textContent =
            STAGE_LABEL[detail.stage] ||
            detail.stage ||
            '-';

        $('#myStageMessage').textContent =
            STAGE_MESSAGE[detail.stage] ||
            'กำลังดำเนินการ';

        $('#myStatusIcon').textContent =
            detail.stage === 'REJECTED' ||
            detail.stage === 'TERMINATED' ?
            '!' :
            detail.stage === 'COMPLETED' ?
            '✓' :
            '•';

        if (detail.attention) {
            $('#myAttention').textContent =
                ATTENTION_LABEL[
                    detail.attention
                ] ||
                detail.attention;

            $('#myAttention').hidden = false;
        } else {
            $('#myAttention').hidden = true;
        }

        const student =
            detail.student || {};

        $('#myStudent').innerHTML =
            kvHtml([
                [
                    'รหัสนักศึกษา',
                    student.studentId
                ],
                [
                    'ชื่อ-นามสกุล',
                    student.fullName
                ],
                [
                    'คณะ',
                    student.faculty
                ],
                [
                    'สาขาวิชา',
                    student.major
                ],
                [
                    'เกรดเฉลี่ย',
                    student.gpa
                ]
            ]);

        renderApplication(
            $('#myApplication'),
            detail.application
        );

        renderMatch(
            $('#myMatch'),
            detail.match
        );

        renderPreferences(
            $('#myPreferences'),
            detail.preferences
        );

        renderMyInternship(
            detail.internship
        );

        renderTimeline(
            $('#myTimeline'),
            $('#myTimelineEmpty'),
            detail.timeline
        );
    }

    function renderProgress(stage) {
        const normalStages = [
            'NOT_APPLIED',
            'APPLYING',
            'WAITING_MATCH',
            'MATCHING',
            'READY',
            'IN_PROGRESS',
            'COMPLETED'
        ];

        const currentIndex =
            normalStages.indexOf(stage);

        $$('.progress-item')
            .forEach(item => {
                const itemStage =
                    item.dataset.progress;

                item.classList.remove(
                    'done',
                    'current'
                );

                if (itemStage === stage) {
                    item.classList.add('current');
                    return;
                }

                const itemIndex =
                    normalStages.indexOf(
                        itemStage
                    );

                if (
                    currentIndex >= 0 &&
                    itemIndex >= 0 &&
                    itemIndex < currentIndex
                ) {
                    item.classList.add('done');
                }

                if (
                    stage === 'TERMINATED' &&
                    itemIndex >= 0 &&
                    itemIndex <= 5
                ) {
                    item.classList.add('done');
                }

                if (
                    stage === 'REJECTED' &&
                    (
                        itemStage ===
                        'NOT_APPLIED' ||
                        itemStage ===
                        'APPLYING'
                    )
                ) {
                    item.classList.add('done');
                }
            });
    }

    function renderMyInternship(internship) {
        const container =
            $('#myInternship');

        if (!internship) {
            container.textContent =
                'ยังไม่มีข้อมูลการฝึกงาน';
            return;
        }

        container.innerHTML =
            '<dl class="kv">' +
            kvHtml([
                [
                    'สถานะ',
                    INTERNSHIP_LABEL[
                        internship.status
                    ] ||
                    internship.status
                ],
                [
                    'วันเริ่มฝึกงาน',
                    formatDate(
                        internship.startDate
                    )
                ],
                [
                    'วันสิ้นสุด',
                    formatDate(
                        internship.endDate
                    )
                ],
                [
                    'ผู้ดูแลที่บริษัท',
                    internship.supervisorName
                ],
                [
                    'อีเมลผู้ดูแล',
                    internship.supervisorEmail
                ],
                [
                    'เบอร์โทรผู้ดูแล',
                    internship.supervisorPhone
                ],
                [
                    'เหตุผลที่ยุติ',
                    internship.terminatedReason
                ]
            ]) +
            '</dl>';
    }

    /* =====================================================
       เริ่มทำงาน — ป้องกัน Init ซ้ำ
       ===================================================== */

    let started = false;

    async function init() {
        if (started) {
            return;
        }

        started = true;

        const user = currentUser();

        if (!user) {
            window.U7.go('login.html');
            return;
        }

        setupShell(user);

        const page =
            document.body.dataset.page;

        if (page === 'student-status') {
            await initStudentStatus(user);
            return;
        }

        if (page === 'my-status') {
            await initMyStatus(user);
        }
    }

    window.U7 = {
        go: url =>
            window.location.assign(url),

        request,
        currentUser,
        stageClass,
        formatDate,
        formatDateTime,
        init
    };

    if (document.readyState === 'loading') {
        document.addEventListener(
            'DOMContentLoaded',
            init
        );
    } else {
        init();
    }
})();