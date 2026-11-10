/* =========================================================
   U7: ตรวจสอบข้อมูลและสถานะการฝึกงาน/สหกิจศึกษา
   ========================================================= */

const API_BASE =
    window.U7_API_BASE || 'http://localhost:8080';

const STATUS_TH = {
    DRAFT: 'ฉบับร่าง',
    SUBMITTED: 'ยื่นแล้ว รอเจ้าหน้าที่ตรวจสอบ',
    UNDER_REVIEW: 'รออาจารย์พิจารณา',
    NEED_CORRECTION: 'ส่งกลับให้แก้ไข',
    APPROVED: 'อนุมัติแล้ว',
    REJECTED: 'ไม่อนุมัติ'
};

const STATUS_MESSAGE = {
    DRAFT: 'คำร้องยังเป็นฉบับร่าง กรุณากรอกข้อมูลและส่งคำร้องให้เรียบร้อย',

    SUBMITTED: 'ระบบได้รับคำร้องแล้ว และกำลังรอเจ้าหน้าที่ตรวจสอบ',

    UNDER_REVIEW: 'เจ้าหน้าที่ตรวจสอบแล้ว และกำลังรออาจารย์พิจารณา',

    NEED_CORRECTION: 'คำร้องถูกส่งกลับ กรุณาตรวจสอบเหตุผลและแก้ไขข้อมูล',

    APPROVED: 'คำร้องได้รับการอนุมัติแล้ว',

    REJECTED: 'คำร้องไม่ได้รับการอนุมัติ'
};

const $ = selector =>
    document.querySelector(selector);

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
    return readJson('u5-user');
}

function currentUserId() {
    const user = currentUser();

    if (user && user.id != null) {
        return String(user.id);
    }

    const auth = readJson('u1-auth');

    if (auth && auth.userId != null) {
        return String(auth.userId);
    }

    return '';
}

function currentUsername() {
    const user = currentUser();

    if (user) {
        return (
            user.fullName ||
            user.username ||
            user.name ||
            ''
        );
    }

    const state = readJson('coop-state');

    return state && state.user ?
        state.user :
        '';
}

async function callApi(path) {
    const response = await fetch(API_BASE + path, {
        method: 'GET',

        headers: {
            'X-User-Id': currentUserId()
        }
    });

    let data = null;

    try {
        data = await response.json();
    } catch (error) {
        data = null;
    }

    return {
        status: response.status,
        data
    };
}

function formatDate(value) {
    if (!value) {
        return '-';
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return date.toLocaleString('th-TH', {
        dateStyle: 'medium',
        timeStyle: 'short'
    });
}

function showError(message) {
    $('#statusLoading').hidden = true;
    $('#studentSection').hidden = true;
    $('#requestSection').hidden = true;
    $('#statusEmpty').hidden = true;

    $('#statusError').textContent = message;
    $('#statusError').hidden = false;
}

function showProfile(profile) {
    $('#studentId').textContent =
        profile.studentId || '-';

    $('#studentName').textContent =
        profile.fullName || '-';

    $('#faculty').textContent =
        profile.faculty || '-';

    $('#major').textContent =
        profile.major || '-';

    $('#gpa').textContent =
        profile.gpa !== null && profile.gpa !== undefined ?
        profile.gpa :
        '-';

    $('#studentSection').hidden = false;
}

function showRequest(request) {
    const status = request.status;

    $('#requestId').textContent =
        request.id ?? '-';

    $('#formName').textContent =
        request.formName || '-';

    $('#requestStatus').textContent =
        STATUS_TH[status] || status || '-';

    $('#submittedAt').textContent =
        formatDate(request.submittedAt);

    $('#statusMessage').textContent =
        STATUS_MESSAGE[status] ||
        'กำลังดำเนินการตามขั้นตอนของระบบ';

    if (
        status === 'NEED_CORRECTION' &&
        request.correctionReason
    ) {
        $('#correctionReason').textContent =
            request.correctionReason;

        $('#correctionBox').hidden = false;
    } else {
        $('#correctionBox').hidden = true;
    }

    $('#requestSection').hidden = false;
}

async function loadStatus() {
    if (!currentUserId()) {
        showError(
            'ไม่พบข้อมูลผู้ใช้ กรุณาเข้าสู่ระบบใหม่อีกครั้ง'
        );

        return;
    }

    try {
        const [
            profileResponse,
            requestsResponse
        ] = await Promise.all([
            callApi('/api/students/me'),
            callApi('/api/requests/me')
        ]);

        if (
            profileResponse.status === 401 ||
            requestsResponse.status === 401
        ) {
            location.replace('login.html');
            return;
        }

        if (profileResponse.status !== 200) {
            showError(
                profileResponse.data ?.message ||
                'ไม่สามารถโหลดข้อมูลนักศึกษาได้'
            );

            return;
        }

        if (requestsResponse.status !== 200) {
            showError(
                requestsResponse.data ?.message ||
                'ไม่สามารถโหลดสถานะคำร้องได้'
            );

            return;
        }

        $('#statusLoading').hidden = true;
        $('#statusError').hidden = true;

        showProfile(profileResponse.data);

        const requests =
            Array.isArray(requestsResponse.data) ?
            requestsResponse.data : [];

        if (requests.length === 0) {
            $('#requestSection').hidden = true;
            $('#statusEmpty').hidden = false;

            return;
        }

        $('#statusEmpty').hidden = true;

        showRequest(requests[0]);
    } catch (error) {
        showError(
            'ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง'
        );
    }
}

function logout() {
    sessionStorage.removeItem('u1-auth');
    sessionStorage.removeItem('u5-user');
    sessionStorage.removeItem('coop-state');

    location.href = 'login.html';
}

document.addEventListener('DOMContentLoaded', () => {
    $('#who').textContent = currentUsername();
    $('#logout').addEventListener('click', logout);

    loadStatus();
});
