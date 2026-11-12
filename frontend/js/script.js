/* =========================================================
   script.js
   ใช้ร่วมกัน: login, check, submit, ranking, worklog
   ========================================================= */

/* ---------- ตัวช่วยค้นหา HTML ---------- */
const $ = (selector, root = document) =>
    root.querySelector(selector);

const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

/* ---------- API สำหรับพัฒนาในเครื่อง ---------- */
const ELIGIBILITY_API =
    'http://localhost:8080/api/eligibility/check';

/* ---------- สถานะของระบบ ---------- */
const KEY = 'coop-state';

const DEF = {
    user: null,
    unlocked: 1,

    form: {
        id: '',
        fac: '',
        major: 'วิทยาการคอมพิวเตอร์',
        gpax: ''
    },

    cur: '61',
    val: {},
    alt: {},
    ack: [false, false, false],

    prio: {
        A: '',
        B: '',
        C: '',
        D: ''
    }
};

const fresh = () =>
    JSON.parse(JSON.stringify(DEF));

function load() {
    try {
        const defaults = fresh();
        const stored =
            JSON.parse(sessionStorage.getItem(KEY) || '{}');

        return {
            ...defaults,
            ...stored,

            form: {
                ...defaults.form,
                ...(stored.form || {})
            },

            val: stored.val || {},
            alt: stored.alt || {},

            ack: Array.isArray(stored.ack) &&
                stored.ack.length === 3 ?
                stored.ack :
                defaults.ack,

            prio: {
                ...defaults.prio,
                ...(stored.prio || {})
            }
        };
    } catch (error) {
        return fresh();
    }
}

let S = load();

function save() {
    try {
        sessionStorage.setItem(KEY, JSON.stringify(S));
    } catch (error) {
        // หากบันทึกไม่ได้ ยังคงใช้สถานะในหน่วยความจำ
    }
}

/* ---------- ส่วนประกอบร่วม ---------- */
function setStepper(cur, errAt = 0, allDone = false) {
    $$('.steps .st').forEach((element, index) => {
        const step = index + 1;
        let className = '';
        let label = step;

        if (errAt === step) {
            className = 'err';
            label = '✕';
        } else if (
            step < cur ||
            (step === cur && allDone)
        ) {
            className = 'done';
            label = '✓';
        } else if (step === cur) {
            className = 'cur';
        }

        element.className = 'st ' + className;

        const dot = $('.dot', element);

        if (dot) {
            dot.textContent = label;
        }
    });
}

const showView = (ids, currentId) => {
    ids.forEach(id => {
        const element = $('#' + id);

        if (element) {
            element.hidden = id !== currentId;
        }
    });
};

const DELAY = 1400;

const NEED = {
    check: 1,
    submit: 2,
    ranking: 3
};

const HOME = () => {
    const index =
        Math.min(Math.max(S.unlocked, 1), 3) - 1;

    return ['check', 'submit', 'ranking'][index] + '.html';
};

/* U1: role มาจาก backend (POST /api/auth/login) เก็บไว้ใน sessionStorage 'u5-user' โดย js/login.js
   ไม่ใช่นักศึกษา → ไปหน้าตรวจใบสมัคร (ถ้ายังไม่มี role ใช้วิธีเดิมของ U5: ดูจากชื่อ staff/advisor) */
const loginRole = u => { try { const x = JSON.parse(sessionStorage.getItem('u5-user') || 'null'); return x && x.username === u ? x.role : null } catch (e) { return null } };
const isStaffLogin = u => { const r = loginRole(u); return r ? r !== 'STUDENT' : /^(staff|advisor)/i.test(u || '') };
const HOME_OF = u => isStaffLogin(u) ? 'staff-review.html' : HOME();

function initShell() {
    $('#who').textContent = S.user;

    $$('nav a:not([data-free])').forEach((link, index) => {
        if (index + 1 > S.unlocked) {
            link.classList.add('disabled');
            link.setAttribute('aria-disabled', 'true');
            link.tabIndex = -1;
        }
    });

    $('#logout').onclick = () => {
        S.user = null;
        save();
        try { sessionStorage.removeItem('u1-auth'); sessionStorage.removeItem('u5-user') } catch (e) { }
        location.href = 'login.html';
    };
}

/* ---------- เริ่มทำงานตามหน้าที่เปิด ---------- */
document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.page;

    if (page === 'login') {
        return initLogin();
    }

    if (!S.user) {
        return location.replace('login.html');
    }

    // U5: หน้านักศึกษาไม่ใช่ของเจ้าหน้าที่
    if (isStaffLogin(S.user)) {
        return location.replace('staff-review.html');
    }

    if (S.unlocked < NEED[page]) {
        return location.replace('check.html');
    }

    const initializers = {
        check: initCheck,
        submit: initSubmit,
        ranking: initRanking,
        worklog: initWorklog
    };

    if (!initializers[page]) {
        return;
    }

    initShell();
    initializers[page]();
});

/* =========================================================
   หน้า Login
   ========================================================= */
function initLogin() {
    if (S.user) {
        return location.replace(HOME_OF(S.user));
    }

    $('#loginForm').addEventListener('submit', event => {
        event.preventDefault();

        const username = $('#u').value.trim();
        const password = $('#p').value;

        // การล็อกอินจำลองสำหรับต้นแบบ
        if (username && password === '1234') {
            S.user = username;
            save();
            location.href = HOME_OF(username);
        } else {
            $('#loginErr').hidden = false;
        }
    });
}

/* =========================================================
   หน้า U2: ตรวจสอบคุณสมบัติ
   ========================================================= */

/* ---------- รายวิชาของแต่ละหลักสูตร ---------- */
const CUR = {
    '61': {
        core: [
            'คพ.101',
            'คพ.102',
            'คพ.111', ['คพ.213', 'คพ.216'],
            'คพ.251',
            'คพ.264'
        ],

        now: [
            'คพ.384', ['คพ.266', 'คพ.322', 'คพ.348']
        ],

        must: 'คพ.302'
    },

    '66': {
        core: [
            'คพ.100',
            'คพ.101',
            'คพ.102',
            'คพ.111', ['คพ.213', 'คพ.216'],
            'คพ.251',
            'คพ.261'
        ],

        now: [
            'คพ.180', ['คพ.262', 'คพ.331', 'คพ.240']
        ],

        must: 'คพ.301'
    }
};

const GRADE = {
    'A': 4,
    'B+': 3.5,
    'B': 3,
    'C+': 2.5,
    'C': 2,
    'D+': 1.5,
    'D': 1,
    'F': 0
};

const GRADE_OPTS = [
    'ยังไม่เคยเรียน',
    ...Object.keys(GRADE)
];

const MIN_GRADE = 2.5;

const STAT_OPTS = [
    'ยังไม่เคยลง',
    'กำลังศึกษา',
    'เคยศึกษาแล้ว'
];

/* ---------- ตัวช่วยอ่านข้อมูลรายวิชา ---------- */
const K = (section, index) =>
    S.cur + section + index;

const valOf = (section, index, defaultValue) => {
    const key = K(section, index);

    return key in S.val ?
        S.val[key] :
        defaultValue;
};

const codeOf = (section, item, index) => {
    if (Array.isArray(item)) {
        return item[S.alt[K(section, index)] || 0];
    }

    return item;
};

/* ---------- สร้างแถวรายวิชา ---------- */
function rowHtml(section, item, index, list, defaultValue) {
    const key = K(section, index);
    const value = valOf(section, index, defaultValue);
    const isGroup = Array.isArray(item);

    const courseSelector = isGroup ?
        `
         <select data-t="alt"
                 data-k="${key}"
                 aria-label="เลือกรายวิชา">
            ${item.map((code, choiceIndex) => `
               <option
                  value="${choiceIndex}"
                  ${(S.alt[key] || 0) === choiceIndex
                     ? 'selected'
                     : ''}>
                  ${code}
               </option>
            `).join('')}
         </select>
      `
      : '<small></small>';

   const valueSelector = `
      <select data-t="val"
              data-k="${key}"
              aria-label="${isGroup ? 'ผล' : item}">
         <option value="" ${value === '' ? 'selected' : ''}>กรุณาเลือก</option>
         ${list.map(option => `
            <option ${option === value ? 'selected' : ''}>
               ${option}
            </option>
         `).join('')}
      </select>
   `;

   return `
      <div class="row">
         <b>${isGroup ? 'เลือก 1' : item}</b>
         ${courseSelector}
         ${valueSelector}
      </div>
   `;
}

/*
 * ฟังก์ชันประเมินเดิม เก็บไว้สำหรับโค้ดหรือ Test เดิม
 * การกดตรวจสอบบนหน้าเว็บจะใช้ผลจาก API เท่านั้น
 */
function evaluate() {
   const curriculum = CUR[S.cur];
   const gpa = Number(S.form.gpax);
   const results = [];

   results.push({
      ok: 1,
      t: 'สาขาวิชา',
      d: 'นักศึกษาสาขาวิทยาการคอมพิวเตอร์ คณะวิทยาศาสตร์ฯ ม.ธรรมศาสตร์'
   });

   curriculum.core.forEach((item, index) => {
      const code = codeOf('g', item, index);
      const grade = valOf('g', index, '');

      if (!(grade in GRADE)) {
         results.push({
            ok: 0,
            t: code,
            d: 'ต้องเคยเรียนและได้เกรดไม่ต่ำกว่า 2.50',
            v: 'ยังไม่เคยเรียน'
         });
      } else if (GRADE[grade] < MIN_GRADE) {
         results.push({
            ok: 0,
            t: code,
            d: 'ต้องได้เกรดไม่ต่ำกว่า 2.50 (C+ ขึ้นไป)',
            v: 'เกรด ' + grade
         });
      } else {
         results.push({
            ok: 1,
            t: code,
            d: 'เกรด ' + grade + ' (ผ่านเกณฑ์ไม่ต่ำกว่า 2.50)'
         });
      }
   });

   const missingCourses = [];
   const courseDetails = [];

   curriculum.now.forEach((item, index) => {
      const code = codeOf('s', item, index);
      const status = valOf('s', index, '');

      courseDetails.push(code + ' (' + status + ')');

      if (status === 'ยังไม่เคยลง') {
         missingCourses.push(code);
      }
   });

   results.push({
      ok: missingCourses.length ? 0 : 1,
      t: 'กำลังศึกษา หรือเคยศึกษารายวิชา',
      d: courseDetails.join(' · '),
      v: missingCourses.length
         ? 'ยังไม่ลง ' + missingCourses.join(', ')
         : ''
   });

   const validGpa =
      Number.isFinite(gpa) && gpa >= 0 && gpa <= 4;

   const passedGpa = validGpa && gpa >= 2.75;

   results.push({
      ok: passedGpa ? 1 : 0,
      t: 'เกรดเฉลี่ยสะสม (GPA) ไม่ต่ำกว่า 2.75',
      d: validGpa
         ? 'ของคุณ ' + gpa.toFixed(2)
         : 'กรอก GPA ไม่ถูกต้อง',
      v: passedGpa
         ? ''
         : validGpa
            ? gpa.toFixed(2)
            : 'ไม่ถูกต้อง'
   });

   results.push({
      ok: 'w',
      t: 'ต้องสอบได้วิชา ' + curriculum.must,
      d: 'ก่อนออกไปปฏิบัติงานจริง ณ สถานประกอบการ'
   });

   return results;
}

/* ---------- สร้าง Request สำหรับ API ---------- */
function buildEligibilityRequest() {
   const curriculum = CUR[S.cur];

   if (!curriculum) {
      throw new Error('หลักสูตรต้องเป็น 61 หรือ 66');
   }

   const grades = {};
   const courseStatuses = {};

   curriculum.core.forEach((item, index) => {
      const course = codeOf('g', item, index);
      grades[course] = valOf('g', index, '');
   });

   curriculum.now.forEach((item, index) => {
      const course = codeOf('s', item, index);

      courseStatuses[course] =
         valOf('s', index, '');
   });

   return {
      studentId: String(S.form.id ?? '').trim(),
      curriculum: S.cur,
      gpax: String(S.form.gpax ?? '').trim(),
      grades,
      courseStatuses,
      acknowledgements: [...S.ack]
   };
}

/* ---------- ตรวจรูปแบบ Response ---------- */
function isEligibilityResponse(data) {
   if (!data || typeof data !== 'object') {
      return false;
   }

   const stringArray = value =>
      Array.isArray(value) &&
      value.every(item => typeof item === 'string');

   return (
      typeof data.valid === 'boolean' &&
      (
         data.valid
            ? typeof data.eligible === 'boolean'
            : data.eligible === null
      ) &&
      typeof data.message === 'string' &&
      stringArray(data.errors) &&
      stringArray(data.reasons) &&
      stringArray(data.warnings)
   );
}

/* ---------- แปลงผล API สำหรับแสดงบน UI ---------- */
function convertApiResponse(data) {
   const results = [];

   if (!data.valid) {
      const errors = data.errors.length
         ? data.errors
         : [data.message];

      errors.forEach(error => {
         results.push({
            ok: 0,
            t: 'ข้อมูลไม่ถูกต้อง',
            d: error
         });
      });

      return results;
   }

   if (data.eligible) {
      results.push({
         ok: 1,
         t: 'ผลการตรวจสอบ',
         d: data.message
      });
   } else {
      // แสดงเฉพาะเหตุผล เพื่อไม่นับรายการไม่ผ่านซ้ำ
      const reasons = data.reasons.length
         ? data.reasons
         : [data.message];

      reasons.forEach(reason => {
         results.push({
            ok: 0,
            t: 'ไม่ผ่านคุณสมบัติ',
            d: reason
         });
      });
   }

   data.warnings.forEach(warning => {
      results.push({
         ok: 'w',
         t: 'คำเตือน',
         d: warning
      });
   });

   return results;
}

/* ---------- ควบคุมหน้าตรวจสอบคุณสมบัติ ---------- */
function initCheck() {
   const views = [
      'vForm',
      'vLoad',
      'vResult',
      'vDone'
   ];

   const show = id => showView(views, id);

   let res = [];
   let ok = false;
   let busy = false;
   let resultMessage = '';
   let profileReady = false;
   let studentId = '';
   // Discard legacy sample data and data belonging to a different account.
   if (S.eligibilityVersion !== 2 || S.eligibilityOwner !== S.user) {
      S.form = fresh().form;
      S.val = {};
      S.alt = {};
      S.ack = [false, false, false];
      S.unlocked = 1;
      S.eligibilityVersion = 2;
      S.eligibilityOwner = S.user;
   }

   if (!Object.prototype.hasOwnProperty.call(CUR, S.cur)) {
      S.cur = '61';
   }

   $('#f_id').value = '';
   $('#f_id').readOnly = true;
   $('#f_gpax').value = S.form.gpax;
   $('#f_prog').value = S.cur;

   const sync = () => {
      $('#btnCheck').disabled =
         busy || !profileReady || !S.ack.every(value => value === true);
   };

   function draw() {
      const curriculum = CUR[S.cur];

      $('#coreRows').innerHTML = curriculum.core
         .map((item, index) =>
            rowHtml('g', item, index, GRADE_OPTS, '')
         )
         .join('');

      $('#nowRows').innerHTML = curriculum.now
         .map((item, index) =>
            rowHtml(
               's',
               item,
               index,
               STAT_OPTS,
               ''
            )
         )
         .join('');

      $('#mustTxt').textContent = curriculum.must;

      $$('.cf').forEach((checkbox, index) => {
         checkbox.checked = S.ack[index];
      });

      sync();
   }

   draw();

   async function loadProfile() {
      const errorBox = $('#formError');
      try {
         const auth = JSON.parse(sessionStorage.getItem('u1-auth') || 'null');
         if (!auth || auth.username !== S.user || auth.role !== 'STUDENT' || !auth.userId) {
            throw new Error('กรุณาเข้าสู่ระบบด้วยบัญชีนักศึกษาใหม่');
         }
         const response = await fetch('http://localhost:8080/api/students/me', {
            headers: { 'X-User-Id': String(auth.userId) }
         });
         if (!response.ok) throw new Error('ไม่สามารถโหลดข้อมูลนักศึกษาจากฐานข้อมูลได้');
         const profile = await response.json();
         if (!/^\d{10}$/.test(profile.studentId || '') || !profile.faculty) {
            throw new Error('ข้อมูลรหัสนักศึกษาหรือคณะในฐานข้อมูลไม่ครบ');
         }
         studentId = profile.studentId;
         S.form.id = studentId;
         S.form.fac = profile.faculty;
         S.form.major = profile.major || '';
         $('#f_id').value = studentId;
         $('#f_fac').value = S.form.fac;
         $('#f_major').value = S.form.major;
         profileReady = true;
         save();
      } catch (error) {
         errorBox.textContent = error.message;
         errorBox.hidden = false;
         $('#f_id').setAttribute('aria-invalid', 'true');
      }
      sync();
   }
   loadProfile();

   const form = $('#vForm');

   function clearFieldError(target) {
      if (target.matches('input, select')) {
         target.removeAttribute('aria-invalid');
         target.removeAttribute('aria-describedby');
      }
   }

   /* เก็บค่าจากช่องกรอกข้อมูล */
   form.addEventListener('input', event => {
      clearFieldError(event.target);
      if (event.target.id === 'f_id') {
         event.target.value = studentId;
      }

      if (event.target.id === 'f_gpax') {
         S.form.gpax = event.target.value;
      }

      save();
   });

   /* เก็บค่าจากตัวเลือกและ checkbox */
   form.addEventListener('change', event => {
      const target = event.target;
      clearFieldError(target);
      const data = target.dataset;

      if (data.t === 'cur') {
         S.cur = target.value;
         draw();
      } else if (data.t === 'alt') {
         S.alt[data.k] = Number(target.value);
      } else if (data.t === 'val') {
         S.val[data.k] = target.value;
      } else if (target.classList.contains('cf')) {
         S.ack[data.i] = target.checked;
         sync();
      }

      save();
   });

   /* ตรวจข้อมูลเบื้องต้นให้ตรงกับ Backend */
   function validateInput() {
      $$('#vForm [aria-invalid]').forEach(el => el.removeAttribute('aria-invalid'));
      const errors = [];
      const mark = (element, message) => {
         element.setAttribute('aria-invalid', 'true');
         element.setAttribute('aria-describedby', 'formError');
         errors.push(message);
      };
      if (!profileReady) mark($('#f_id'), 'กรุณาเข้าสู่ระบบและโหลดข้อมูลนักศึกษาให้สำเร็จ');
      const id = String(S.form.id ?? '').trim();
      const gpax = String(S.form.gpax ?? '').trim();

      if (!id || !/^\d{10}$/.test(id)) mark($('#f_id'), 'รหัสนักศึกษาต้องเป็นตัวเลข 10 หลัก');
      if (!gpax) mark($('#f_gpax'), 'กรุณากรอกข้อมูลให้ครบถ้วน: GPA');
      else if (!/^(?:[0-3](?:\.\d{1,2})?|4(?:\.0{1,2})?)$/.test(gpax)) {
         mark($('#f_gpax'), 'GPA ต้องเป็นตัวเลข 0.00–4.00 และมีทศนิยมไม่เกิน 2 ตำแหน่ง');
      }
      $$('#vForm select[data-t="val"]').forEach(select => {
         if (!select.value) mark(select, 'กรุณาเลือกเกรดหรือสถานะรายวิชาให้ครบถ้วน');
      });
      if (errors.length) {
         $('#vForm [aria-invalid="true"]').focus();
         return [...new Set(errors)].join(' / ');
      }

      if (!id || !gpax) {
         return 'กรุณากรอกข้อมูลให้ครบถ้วน';
      }

      if (!/^\d{10}$/.test(id)) {
         return 'รหัสนักศึกษาต้องเป็นตัวเลข 10 หลัก';
      }

      if (
         !/^(?:[0-3](?:\.\d{1,2})?|4(?:\.0{1,2})?)$/.test(gpax)
      ) {
         return 'GPA ต้องเป็นตัวเลข 0.00–4.00 และมีทศนิยมไม่เกิน 2 ตำแหน่ง';
      }

      if (
         !Object.prototype.hasOwnProperty.call(CUR, S.cur)
      ) {
         return 'หลักสูตรต้องเป็น 61 หรือ 66';
      }

      if (!S.ack.every(value => value === true)) {
         return 'กรุณายืนยันเงื่อนไขให้ครบทั้ง 3 ข้อ';
      }

      return '';
   }

   /* เรียก API จริง */
   async function run(event) {
      if (event) {
         event.preventDefault();
      }

      if (busy) {
         return;
      }

      // อ่านค่าปัจจุบันจากหน้าฟอร์ม
      S.form.id = studentId;
      $('#f_id').value = studentId;
      S.form.gpax = $('#f_gpax').value;
      S.cur = $('#f_prog').value;

      const error = validateInput();
      const errorBox = $('#formError');

      errorBox.textContent = error;
      errorBox.hidden = error === '';

      if (error !== '') {
         setStepper(1);
         show('vForm');
         return;
      }

      busy = true;
      ok = false;
      sync();
      save();

      setStepper(2);
      show('vLoad');

      const controller = new AbortController();
      const timeoutId = setTimeout(
         () => controller.abort(),
         15000
      );

      try {
         const auth = JSON.parse(
            sessionStorage.getItem('u1-auth') || 'null'
         );

         if (
            !auth ||
            auth.role !== 'STUDENT' ||
            auth.userId == null
         ) {
            throw new Error(
               'ไม่พบข้อมูลผู้ใช้ที่เข้าสู่ระบบ'
            );
         }

         const response = await fetch(
            ELIGIBILITY_API,
            {
               method: 'POST',

               headers: {
                  'Content-Type': 'application/json',
                  'X-User-Id': String(auth.userId)
               },

               body: JSON.stringify(
                  buildEligibilityRequest()
               ),

               signal: controller.signal
            }
         );

         if (
            response.status !== 200 &&
            response.status !== 400
         ) {
            throw new Error(
               'เซิร์ฟเวอร์ตอบกลับ HTTP ' +
               response.status +
               ' กรุณาลองใหม่หรือตรวจสอบ Backend'
            );
         }

         const data = await response.json();

         if (!isEligibilityResponse(data)) {
            throw new Error(
               'รูปแบบข้อมูลตอบกลับจาก API ไม่ถูกต้อง'
            );
         }

         if (
            (response.status === 200 && !data.valid) ||
            (response.status === 400 && data.valid)
         ) {
            throw new Error(
               'HTTP Status ไม่ตรงกับผลการตรวจข้อมูล'
            );
         }

         res = convertApiResponse(data);

         // API currently supplies messages, not structured field names.
         // Highlight matching fields; retain all messages even if unmapped.
         if (!data.valid) {
            const markApi = element => {
               element.setAttribute('aria-invalid', 'true');
               element.setAttribute('aria-describedby', 'formError');
            };
            data.errors.forEach(message => {
               if (message.includes('รหัสนักศึกษา')) markApi($('#f_id'));
               if (message.includes('GPA')) markApi($('#f_gpax'));
               if (message.includes('หลักสูตร')) markApi($('#f_prog'));
               const curriculum = CUR[S.cur];
               [['g', curriculum.core], ['s', curriculum.now]].forEach(([section, courses]) => {
                  courses.forEach((item, index) => {
                     if (message.includes(codeOf(section, item, index))) {
                        const field = document.querySelector(`select[data-t="val"][data-k="${K(section, index)}"]`);
                        if (field) markApi(field);
                     }
                  });
               });
            });
            errorBox.textContent = data.errors.join(' / ') || data.message;
            errorBox.hidden = false;
            show('vForm');
            $('#vForm [aria-invalid="true"]')?.focus();
            return;
         }

         ok =
            response.status === 200 &&
            data.valid === true &&
            data.eligible === true;

         resultMessage = data.message;
      } catch (error) {
         ok = false;
         resultMessage = 'ไม่สามารถตรวจสอบคุณสมบัติได้';

         let detail =
            'ไม่สามารถเชื่อมต่อ API ได้ กรุณาตรวจสอบ Backend ที่ localhost:8080';

         if (error.name === 'AbortError') {
            detail =
               'ระบบตอบกลับช้าเกิน 15 วินาที กรุณาลองใหม่';
         } else if (error instanceof SyntaxError) {
            detail =
               'API ไม่ได้ส่งข้อมูล JSON ที่ถูกต้อง';
         } else if (
            error instanceof Error &&
            !(error instanceof TypeError)
         ) {
            detail = error.message;
         }

         res = [
            {
               ok: 0,
               t: 'เกิดข้อผิดพลาดในการตรวจสอบ',
               d: detail
            }
         ];
      } finally {
         clearTimeout(timeoutId);
         busy = false;
         sync();
      }

      result();
   }

   /* แสดงผลโดยใช้ textContent กับข้อมูลจาก API */
   function result() {
      setStepper(3, ok ? 0 : 2);
      show('vResult');

      const alert = $('#rAlert');

      alert.className =
         'alert ' + (ok ? 'ok' : 'bad');

      alert.textContent = resultMessage;

      const list = $('#rList');
      list.replaceChildren();

      res.forEach(item => {
         const resultClass =
            item.ok === 1
               ? 'ok'
               : item.ok === 0
                  ? 'bad'
                  : 'warn';

         const symbol =
            item.ok === 1
               ? '✓'
               : item.ok === 0
                  ? '✕'
                  : '!';

         const row = document.createElement('div');
         row.className = 'res ' + resultClass;

         const dot = document.createElement('i');
         dot.className = 'dot';
         dot.textContent = symbol;

         const paragraph = document.createElement('p');

         const title = document.createElement('b');
         title.textContent = item.t;

         const detail = document.createElement('small');
         detail.textContent = item.d;

         paragraph.append(title, detail);
         row.append(dot, paragraph);

         if (item.v) {
            const value = document.createElement('em');
            value.textContent = item.v;
            row.append(value);
         }

         list.append(row);
      });

      $('#rNext').hidden = !ok;
      $('#rRetry').hidden = ok;
      $('#rEdit').hidden = ok;
   }

   const toForm = event => {
      if (event) {
         event.preventDefault();
      }

      setStepper(1);
      show('vForm');
   };

   $('#btnCheck').onclick = run;
   $('#rRetry').onclick = run;
   $('#rBack').onclick = toForm;
   $('#rEdit').onclick = toForm;

   $('#rNext').onclick = event => {
      event.preventDefault();

      if (!ok) {
         return;
      }

      setStepper(3, 0, true);
      show('vDone');
   };

   $('#dBack').onclick = event => {
      event.preventDefault();
      result();
   };

   $('#dNext').onclick = event => {
      if (!ok) {
         event.preventDefault();
         return;
      }

      S.unlocked = Math.max(S.unlocked, 2);
      save();
   };
}

/* =========================================================
   หน้ายื่นคำร้อง
   ========================================================= */
function initSubmit() {
   /* U3: ยื่นคำร้องผ่าน API จริง
      GET /api/forms/published · GET /api/requests/me · POST /api/requests
      PUT /api/requests/{id}/form · POST /api/requests/{id}/documents · POST /api/requests/{id}/submit */
   const API = window.U3_API_BASE || 'http://localhost:8080';
   const MAX_SIZE = 10 * 1024 * 1024;
   const MSG = {
      required: 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ',
      email: 'รูปแบบอีเมลไม่ถูกต้อง',
      tel: 'รูปแบบเบอร์โทรศัพท์ไม่ถูกต้อง',
      pdf: 'Transcript ต้องเป็นไฟล์ PDF เท่านั้น',
      size: 'ไฟล์ต้องมีขนาดไม่เกิน 10 MB',
      noFile: 'ไม่พบไฟล์หรือไฟล์อ่านไม่ได้',
      fail1: 'คำร้องไม่ผ่านการตรวจสอบ 1 รายการ กรุณาแก้ไขข้อมูล',
      duplicate: 'คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว',
      deadline: 'เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้',
      network: 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง',
      login: 'กรุณาเข้าสู่ระบบใหม่อีกครั้ง'
   };
   const STATUS_TH = {
      DRAFT: 'ฉบับร่าง',
      SUBMITTED: 'ยื่นแล้ว รอเจ้าหน้าที่ตรวจสอบ',
      UNDER_REVIEW: 'รออาจารย์พิจารณา',
      NEED_CORRECTION: 'ส่งกลับให้แก้ไข',
      APPROVED: 'อนุมัติแล้ว',
      REJECTED: 'ไม่อนุมัติ'
   };
   const INPUTS = { s_name: 'TEXT', s_tel: 'PHONE', s_mail: 'EMAIL', s_addr: 'TEXTAREA' };
   const views = ['vForm', 'vLoad', 'vResult'];
   const show = id => showView(views, id);

   let form = null;         // แบบฟอร์มที่เผยแพร่ (จาก API)
   let reqId = null;        // เลขที่คำร้องที่กำลังกรอก (DRAFT / NEED_CORRECTION)
   let busy = false;        // กันกดส่งซ้ำ
   let locked = false;      // ยื่นไม่ได้ (ยื่นซ้ำ / หมดเขต / ยื่นสำเร็จแล้ว)

   $('#s_id').value = S.form.id;
   $('#s_fac').value = S.form.fac;
   $('#s_major').value = S.form.major;
   $('#s_gpax').value = S.form.gpax;

   /* ร่างที่กรอกไว้ เก็บใน sessionStorage ปิดหน้าแล้วเปิดใหม่ข้อมูลยังอยู่ */
   const draft = S.draft || {};
   Object.keys(INPUTS).forEach(id => {
      if (draft[id] != null && !$('#' + id).value) {
         $('#' + id).value = draft[id];
      }
      $('#' + id).addEventListener('input', () => {
         S.draft = { ...(S.draft || {}), [id]: $('#' + id).value };
         save();
      });
   });

   function userId() {
      try {
         const u5 = JSON.parse(sessionStorage.getItem('u5-user') || 'null');
         if (u5 && u5.id != null) return String(u5.id);
         const u1 = JSON.parse(sessionStorage.getItem('u1-auth') || 'null');
         return u1 && u1.userId != null ? String(u1.userId) : '';
      } catch (e) {
         return '';
      }
   }

   /* เรียก API: คืน { status, data } · เชื่อมต่อไม่ได้ → throw */
   async function call(path, method = 'GET', body) {
      const headers = { 'X-User-Id': userId() };
      let payload;
      if (body instanceof FormData) {
         payload = body;
      } else if (body !== undefined) {
         headers['Content-Type'] = 'application/json';
         payload = JSON.stringify(body);
      }
      const res = await fetch(API + path, { method, headers, body: payload });
      let data = null;
      try {
         data = await res.json();
      } catch (e) {
         data = null;
      }
      return { status: res.status, data };
   }

   const fieldName = type => {
      const f = form && (form.fields || []).find(x => x.fieldType === type);
      return f ? f.fieldName : null;
   };

   const fmt = iso => {
      if (!iso) return '';
      const d = new Date(iso);
      return isNaN(d) ? iso : d.toLocaleString('th-TH', { dateStyle: 'medium', timeStyle: 'short' });
   };

   function note(text) {
      const n = $('#s_note');
      n.textContent = text;
      n.hidden = !text;
   }

   function lock(text) {
      locked = true;
      note(text);
      $('#btnSend').disabled = true;
   }

   function fillAnswers(req) {
      if (!form || !req || !req.answers) return;
      Object.entries(INPUTS).forEach(([id, type]) => {
         const name = fieldName(type);
         if (name && req.answers[name] != null) {
            $('#' + id).value = req.answers[name];
         }
      });
   }

   /* โหลดแบบฟอร์ม (พร้อมวันหมดเขตตามเวลา server) และคำร้องเดิมของนักศึกษา */
   async function loadState() {
      try {
         // ข้อมูลนักศึกษาจากฐานข้อมูล (ถ้าเรียกไม่ได้ใช้ค่าที่เก็บไว้ในหน้าเว็บ)
         const me = await call('/api/students/me');
         if (me.status === 200 && me.data) {
            $('#s_id').value = me.data.studentId || '';
            $('#s_fac').value = me.data.faculty || '';
            $('#s_major').value = me.data.major || '';
            $('#s_gpax').value = me.data.gpa == null ? '' : Number(me.data.gpa).toFixed(2);
            if (!$('#s_name').value && me.data.fullName) $('#s_name').value = me.data.fullName;
            if (!$('#s_mail').value && me.data.email) $('#s_mail').value = me.data.email;
         }
         const pub = await call('/api/forms/published');
         if (pub.status === 200 && pub.data) {
            form = pub.data.form;
            if (pub.data.deadlinePassed) {
               lock(MSG.deadline + (form.deadline ? ' (หมดเขต ' + fmt(form.deadline) + ')' : ''));
            }
         }
         const mine = await call('/api/requests/me');
         if (mine.status === 200 && Array.isArray(mine.data)) {
            // ร่างของแบบฟอร์มฉบับเก่า (ปิดไปแล้ว) ไม่นับ
            const open = mine.data.find(r => r.status !== 'REJECTED'
               && !(r.status === 'DRAFT' && form && r.formTemplateId !== form.id));
            if (open && (open.status === 'DRAFT' || open.status === 'NEED_CORRECTION')) {
               reqId = open.id;
               fillAnswers(open);
               if (open.status === 'NEED_CORRECTION' && !locked) {
                  note('เจ้าหน้าที่ส่งคำร้องเลขที่ ' + open.id + ' กลับให้แก้ไข'
                     + (open.correctionReason ? ': ' + open.correctionReason : ''));
               }
            } else if (open) {
               lock(MSG.duplicate + ' (เลขที่คำร้อง ' + open.id + ' · สถานะ: '
                  + (STATUS_TH[open.status] || open.status) + ')');
            }
         }
      } catch (e) {
         note(MSG.network);
      }
   }

   /* ตรวจข้อมูลฝั่งหน้าเว็บ คืนข้อความ error (ว่าง = ผ่าน) */
   function validate() {
      Object.keys(INPUTS).forEach(id => $('#' + id).removeAttribute('aria-invalid'));
      const empty = Object.keys(INPUTS).filter(id => !$('#' + id).value.trim());
      if (empty.length) {
         empty.forEach(id => $('#' + id).setAttribute('aria-invalid', 'true'));
         return MSG.required;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test($('#s_mail').value.trim())) {
         $('#s_mail').setAttribute('aria-invalid', 'true');
         return MSG.email;
      }
      if (!/^0\d{8,9}$/.test($('#s_tel').value.replace(/[\s-]/g, ''))) {
         $('#s_tel').setAttribute('aria-invalid', 'true');
         return MSG.tel;
      }
      return '';
   }

   /* หน้าผล: ok = ผ่าน · fileOk/fileText = แถว Transcript · buttons = ปุ่มที่แสดง */
   function result(ok, message, fileOk, fileText, buttons = {}) {
      setStepper(3, ok ? 0 : 2, ok);
      show('vResult');
      const alert = $('#sAlert');
      alert.className = 'alert ' + (ok ? 'ok' : 'bad');
      alert.textContent = message;
      const tr = $('#sTr');
      tr.className = 'res ' + (fileOk ? 'ok' : 'bad');
      $('.dot', tr).textContent = fileOk ? '✓' : '✕';
      $('small', tr).textContent = fileText;
      $('#sNext').hidden = !ok;
      $('#sRetry').hidden = !buttons.retry;
      $('#sEdit').hidden = !buttons.edit;
   }

   function backToForm(message) {
      setStepper(1);
      show('vForm');
      $('#s_err').textContent = message;
      $('#s_err').hidden = !message;
   }

   /* แปลง error จาก API เป็นสิ่งที่แสดงบนหน้าเว็บ */
   function fail(res) {
      const code = res.data && res.data.code;
      const msg = (res.data && res.data.message) || 'เกิดข้อผิดพลาด (' + res.status + ')';
      if (res.status === 401) {
         return backToForm(MSG.login);
      }
      if (code === 'DEADLINE_PASSED') {
         locked = true;
         return result(false, MSG.deadline, true, 'แนบไฟล์แล้ว');
      }
      if (code === 'DUPLICATE_REQUEST' || (res.status === 409 && code === 'INVALID_STATUS')) {
         locked = true;
         return result(false, MSG.duplicate, true, '-');
      }
      if (code === 'MISSING_DOCUMENT') {
         return result(false, MSG.fail1, false, MSG.noFile, { retry: true, edit: true });
      }
      if (code === 'INVALID_FILE' || code === 'FILE_TOO_LARGE') {
         return result(false, MSG.fail1, false, msg, { retry: true, edit: true });
      }
      if (res.status === 400) {
         const fields = (res.data && res.data.fields) || [];
         return backToForm(fields.length ? fields.map(f => f.message).join(' · ') : msg);
      }
      return result(false, msg, false, '-', { retry: true, edit: true });
   }

   function answers() {
      const out = {};
      Object.entries(INPUTS).forEach(([id, type]) => {
         const name = fieldName(type);
         if (name) out[name] = $('#' + id).value.trim();
      });
      return out;
   }

   /* ส่งคำร้อง: สร้าง (ถ้ายังไม่มี) → บันทึกคำตอบ → แนบไฟล์ (ถ้าเลือก) → ยื่น
      ไม่ได้แนบไฟล์ → ให้ API ตรวจและตอบ 400 MISSING_DOCUMENT */
   async function send() {
      if (busy || locked) return;
      const file = $('#s_tr').files[0];
      if (file && !/\.pdf$/i.test(file.name)) {
         return result(false, MSG.fail1, false, MSG.pdf, { edit: true });
      }
      if (file && file.size > MAX_SIZE) {
         return result(false, MSG.fail1, false, MSG.size, { edit: true });
      }
      busy = true;
      $('#btnSend').disabled = true;
      $('#sRetry').disabled = true;
      setStepper(2);
      show('vLoad');
      try {
         if (!form) {
            const pub = await call('/api/forms/published');
            if (pub.status !== 200) return fail(pub);
            form = pub.data.form;
         }
         if (reqId == null) {
            const created = await call('/api/requests', 'POST', { formTemplateId: form.id });
            if (created.status !== 201) return fail(created);
            reqId = created.data.id;
         }
         const saved = await call('/api/requests/' + reqId + '/form', 'PUT', { answers: answers() });
         if (saved.status !== 200) return fail(saved);
         if (file) {
            const fd = new FormData();
            fd.append('file', file, file.name);
            const up = await call('/api/requests/' + reqId + '/documents', 'POST', fd);
            if (up.status !== 201) return fail(up);
         }
         const done = await call('/api/requests/' + reqId + '/submit', 'POST');
         if (done.status !== 200) return fail(done);
         locked = true;
         S.draft = {};
         save();
         result(true, 'ยื่นคำร้องสำเร็จ เลขที่คำร้อง ' + done.data.id + ' · สถานะ: '
            + (STATUS_TH[done.data.status] || done.data.status), true, 'ไฟล์อ่านได้และข้อมูลตรงกับระบบ');
      } catch (e) {
         backToForm(MSG.network);
      } finally {
         busy = false;
         $('#btnSend').disabled = locked;
         $('#sRetry').disabled = false;
      }
   }

   $('#btnSend').onclick = () => {
      if (busy || locked) return;
      const error = validate();
      $('#s_err').textContent = error;
      $('#s_err').hidden = !error;
      if (error) return;
      if (!window.confirm('ยืนยันการส่งคำร้อง?')) return;
      send();
   };

   $('#sRetry').onclick = () => send();

   $('#sEdit').onclick = () => {
      setStepper(1);
      show('vForm');
   };

   $('#sNext').onclick = () => {
      S.unlocked = 3;
      save();
   };

   window.U3Submit = { ready: loadState() };
}

/* =========================================================
   หน้าจัดอันดับบริษัท
   ========================================================= */
function initRanking() {
   const selectors = $$('select[data-k]');

   selectors.forEach(select => {
      select.value = S.prio[select.dataset.k] || '';
   });

   function list() {
      const choices = Object.entries(S.prio)
         .filter(([, value]) => value)
         .sort((a, b) => a[1] - b[1]);

      $('#chosen').innerHTML = choices.length
         ? choices.map(([key, value]) =>
            `<div>${value}. บริษัท ${key}</div>`
         ).join('')
         : '<small class="mute">ยังไม่ได้เลือก</small>';
   }

   selectors.forEach(select => {
      select.onchange = () => {
         S.prio[select.dataset.k] = select.value;

         save();

         $('#msgOk').hidden = true;
         $('#msgBad').hidden = true;

         list();
      };
   });

   $('#btnConfirm').onclick = () => {
      const values =
         Object.values(S.prio).filter(Boolean);

      const error = !values.length
         ? 'กรุณาเลือกบริษัทอย่างน้อย 1 แห่ง'
         : new Set(values).size !== values.length
            ? 'มีลำดับซ้ำกันมากกว่า 1 บริษัท กรุณาเลือกลำดับใหม่'
            : '';

      $('#msgBad').hidden = !error;
      $('#msgOk').hidden = Boolean(error);

      if (error) {
         $('#msgBadTxt').textContent = error;
      }
   };

   list();
}

/* =========================================================
   หน้าบันทึกการทำงาน (US8)
   API (ดู .doc/UserStory/U8/us8-01-api-spec.png) · ทุก request ส่ง header X-User-Id
   GET  /api/work-logs/my-internship                หา internshipId ของนักศึกษา
   GET  /api/internships/{id}/work-logs              ประวัติบันทึก
   GET  /api/internships/{id}/work-logs/summary      สรุปชั่วโมง / วันที่ขาดบันทึก
   POST /api/internships/{id}/work-logs              สร้างบันทึกฉบับร่าง
   GET / PUT / DELETE /api/work-logs/{id}            รายละเอียด / แก้ไข / ลบ (เฉพาะ DRAFT)
   POST /api/work-logs/{id}/submit                   ส่งให้อาจารย์ตรวจ
   POST / GET / DELETE /api/work-logs/{id}/attachments[/{attachmentId}]
   (ดาวน์โหลดไฟล์แนบด้วย fetch + header แล้วเปิดเป็น blob ไม่ส่ง userId ทาง URL)
   ========================================================= */
const WL = {
   api: () => window.U8_API_BASE || 'http://localhost:8080',
   minHours: 0.5,
   maxHours: 12,
   maxText: 2000,
   maxFile: 10 * 1024 * 1024,
   maxFiles: 5,
   lateDays: 3,
   fileTypes: /\.(pdf|jpe?g|png)$/i
};

const WL_STATUS = {
   DRAFT: ['ฉบับร่าง', 'draft'],
   SUBMITTED: ['ส่งแล้ว', 'ok'],
   LATE: ['ส่งล่าช้า', 'bad'],
   NEED_REVISION: ['ขอให้แก้ไข', 'bad'],
   APPROVED: ['อนุมัติแล้ว', 'ok']
};

/** ชั่วโมงจากเวลา "HH:MM" ทศนิยม 2 ตำแหน่ง · เวลาไม่ครบหรือสิ้นสุดไม่หลังเวลาเริ่ม = null */
function wlHours(start, end) {
   if (!start || !end) return null;
   const toMin = t => {
      const [h, m] = t.split(':').map(Number);
      return h * 60 + m;
   };
   const minutes = toMin(end) - toMin(start);
   return minutes > 0 ? Math.round(minutes / 60 * 100) / 100 : null;
}

/** วันที่ท้องถิ่นรูปแบบ YYYY-MM-DD */
function wlIso(date) {
   const pad = n => String(n).padStart(2, '0');
   return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}

function wlThaiDate(iso) {
   if (!iso) return '-';
   return new Date(iso + 'T00:00:00').toLocaleDateString('th-TH', {
      day: 'numeric', month: 'short', year: 'numeric'
   });
}

function initWorklog() {
   const fields = {
      logDate: $('#wDate'),
      startTime: $('#wStart'),
      endTime: $('#wEnd'),
      activity: $('#wTask'),
      learning: $('#wSkill'),
      problem: $('#wProb')
   };
   const fileInput = $('#wFile');

   let internship = null;   // { id, startDate, endDate, status }
   let logs = [];           // ประวัติบันทึก (WorkLogResponse)
   let current = null;      // บันทึกที่เปิดอยู่ (WorkLogDetailResponse) · null = บันทึกใหม่
   let busy = false;        // กันกดซ้ำ

   /* ---------- เรียก API ---------- */
   function userId() {
      try {
         const u5 = JSON.parse(sessionStorage.getItem('u5-user') || 'null');
         if (u5 && u5.id != null) return String(u5.id);
         const u1 = JSON.parse(sessionStorage.getItem('u1-auth') || 'null');
         return u1 && u1.userId != null ? String(u1.userId) : '';
      } catch (e) {
         return '';
      }
   }

   async function call(path, method = 'GET', body) {
      const headers = { 'X-User-Id': userId() };
      let payload;
      if (body instanceof FormData) {
         payload = body;
      } else if (body !== undefined) {
         headers['Content-Type'] = 'application/json';
         payload = JSON.stringify(body);
      }
      const res = await fetch(WL.api() + path, { method, headers, body: payload });
      let data = null;
      try {
         data = await res.json();
      } catch (e) {
         data = null;
      }
      return { status: res.status, data };
   }

   const message = (res, fallback) =>
      (res.data && res.data.message) || fallback;

   /* ---------- ข้อความบนหน้า ---------- */
   function hideSide() {
      ['#wOk', '#wLate', '#wSaved'].forEach(s => { $(s).hidden = true; });
   }

   function showError(text) {
      $('#wErr').hidden = !text;
      $('#wErr').textContent = text || '';
   }

   function showState(text) {
      $('#wState').hidden = !text;
      $('#wState').textContent = text || '';
   }

   function setBusy(value) {
      busy = value;
      $('#wSend').textContent = value ? 'กำลังบันทึก...' : 'ส่งบันทึก';
      lockForm();
   }

   /* แก้ได้เฉพาะบันทึกใหม่ / DRAFT / NEED_REVISION ของการฝึกงานที่ IN_PROGRESS */
   function editable() {
      const open = internship && internship.status === 'IN_PROGRESS';
      return Boolean(open && (!current || ['DRAFT', 'NEED_REVISION'].includes(current.status)));
   }

   function lockForm() {
      const canEdit = editable() && !busy;
      Object.values(fields).forEach(el => { el.disabled = !canEdit; });
      // แก้บันทึกเดิมได้ แต่ห้ามเปลี่ยนวันที่ไปทับวันอื่น จึงล็อกวันที่ไว้
      fields.logDate.disabled = !canEdit || Boolean(current);
      fileInput.disabled = !canEdit;
      $('#wSend').disabled = !canEdit;
      $('#wDraft').disabled = !canEdit;
      $('#wDelete').hidden = !(current && current.status === 'DRAFT');
      $('#wDelete').disabled = busy;
      $('#wNew').disabled = busy || !internship;
   }

   /* ---------- กำหนดส่ง + ชั่วโมง ---------- */
   function updateDue() {
      const date = fields.logDate.value;
      if (!date) {
         $('#wDueTitle').textContent = 'กำหนดส่งบันทึก';
         return;
      }
      const due = new Date(date + 'T00:00:00');
      due.setDate(due.getDate() + WL.lateDays);
      $('#wDueTitle').textContent = 'กำหนดส่งบันทึกวันที่ ' + wlThaiDate(date) + ': '
         + wlThaiDate(wlIso(due)) + ' เวลา 23:59';
   }

   function updateHours() {
      const hours = wlHours(fields.startTime.value, fields.endTime.value);
      $('#wHours').value = hours == null ? '' : hours.toFixed(2).replace(/\.?0+$/, '') + ' ชั่วโมง';
   }

   /* ---------- ฟอร์ม ---------- */
   function fillForm(detail) {
      current = detail;
      const today = wlIso(new Date());
      fields.logDate.value = detail ? detail.logDate : (fields.logDate.value || today);
      fields.startTime.value = detail ? String(detail.startTime || '').slice(0, 5) : '';
      fields.endTime.value = detail ? String(detail.endTime || '').slice(0, 5) : '';
      fields.activity.value = detail ? detail.activity || '' : '';
      fields.learning.value = detail ? detail.learning || '' : '';
      fields.problem.value = detail ? detail.problem || '' : '';
      fileInput.value = '';

      $('#wFormTitle').textContent = detail
         ? 'บันทึกวันที่ ' + wlThaiDate(detail.logDate) + ' · ' + statusLabel(detail)[0]
         : 'บันทึกประจำวัน';

      const revise = detail && detail.status === 'NEED_REVISION';
      $('#wReview').hidden = !(detail && detail.reviewComment);
      $('#wReview b').textContent = revise ? 'อาจารย์ขอให้แก้ไข' : 'ความเห็นจากอาจารย์';
      $('#wReviewText').textContent = detail && detail.reviewComment
         ? detail.reviewComment + (detail.reviewerName ? ' (' + detail.reviewerName + ')' : '')
         : '';

      renderFiles();
      updateHours();
      updateDue();
      showError('');
      lockForm();
      renderHistory();
   }

   function renderFiles() {
      const list = $('#wFiles');
      list.textContent = '';
      const files = current && current.attachments ? current.attachments : [];
      files.forEach(file => {
         const li = document.createElement('li');
         const open = document.createElement('button');
         open.type = 'button';
         open.className = 'link';
         open.textContent = file.fileName;
         open.dataset.attachment = file.id;
         open.onclick = () => downloadFile(file);
         li.append(open);
         if (editable()) {
            const del = document.createElement('button');
            del.type = 'button';
            del.className = 'link del';
            del.textContent = 'ลบไฟล์';
            del.dataset.attachment = file.id;
            del.onclick = () => removeFile(file.id);
            li.append(del);
         }
         list.append(li);
      });
   }

   /**
    * เปิดไฟล์แนบ: ดาวน์โหลดด้วย fetch พร้อม header X-User-Id (เหมือน request อื่น) แล้วเปิดจาก blob
    * ไม่ใช้ window.open กับ URL ที่มี ?userId= เพราะเปลี่ยนเลขใน URL แล้วเปิดไฟล์ของคนอื่นได้
    */
   async function downloadFile(file) {
      try {
         const res = await fetch(WL.api() + '/api/work-logs/' + current.id + '/attachments/' + file.id, {
            headers: { 'X-User-Id': userId() }
         });
         if (res.status !== 200) {
            let data = null;
            try { data = await res.json(); } catch (e) { data = null; }
            return showError(message({ data }, 'เปิดไฟล์ ' + file.fileName + ' ไม่สำเร็จ'));
         }
         const url = URL.createObjectURL(await res.blob());
         const link = document.createElement('a');
         link.href = url;
         link.target = '_blank';
         link.rel = 'noopener';
         link.download = file.fileName;
         document.body.append(link);
         link.click();
         link.remove();
         setTimeout(() => URL.revokeObjectURL(url), 60000);
      } catch (e) {
         showError('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
      }
   }

   /** ตรวจข้อมูลก่อนเรียก API (ฝั่ง API ตรวจซ้ำอีกครั้ง) คืนข้อความ error แรกที่เจอ */
   function validate() {
      const v = key => fields[key].value.trim();
      const today = wlIso(new Date());
      if (!v('logDate')) return 'กรุณาเลือกวันที่';
      if (v('logDate') > today) return 'ไม่สามารถบันทึกวันที่ในอนาคตได้';
      if (internship && (v('logDate') < internship.startDate
         || (internship.endDate && v('logDate') > internship.endDate))) {
         return 'วันที่ต้องอยู่ในช่วงฝึกงาน';
      }
      if (!v('startTime') || !v('endTime')) return 'กรุณากรอกเวลาเริ่มและเวลาสิ้นสุด';
      const hours = wlHours(v('startTime'), v('endTime'));
      if (hours == null) return 'เวลาสิ้นสุดต้องหลังเวลาเริ่ม';
      if (hours < WL.minHours || hours > WL.maxHours) return 'ชั่วโมงทำงานต้องอยู่ระหว่าง 0.5 ถึง 12 ชั่วโมง';
      if (!v('activity')) return 'กรุณากรอกงานที่ทำ';
      if (!v('learning')) return 'กรุณากรอกสิ่งที่ได้เรียนรู้';
      if (['activity', 'learning', 'problem'].some(k => v(k).length > WL.maxText)) {
         return 'ข้อความแต่ละช่องต้องไม่เกิน ' + WL.maxText + ' ตัวอักษร';
      }
      if (!current && logs.some(l => l.logDate === v('logDate'))) return 'มีบันทึกของวันที่นี้แล้ว';

      const picked = [...(fileInput.files || [])];
      const existing = current && current.attachments ? current.attachments.length : 0;
      if (existing + picked.length > WL.maxFiles) return 'แนบไฟล์ได้ไม่เกิน ' + WL.maxFiles + ' ไฟล์ต่อบันทึก';
      if (picked.some(f => !WL.fileTypes.test(f.name))) return 'แนบได้เฉพาะไฟล์รูป (JPG, PNG) หรือ PDF';
      if (picked.some(f => f.size > WL.maxFile)) return 'ไฟล์ต้องมีขนาดไม่เกิน 10 MB';
      return '';
   }

   function request() {
      const v = key => fields[key].value.trim();
      return {
         logDate: v('logDate'),
         startTime: v('startTime'),
         endTime: v('endTime'),
         activity: v('activity'),
         learning: v('learning'),
         problem: v('problem') || null
      };
   }

   /* ---------- บันทึกร่าง / ส่ง ---------- */
   async function save(submit) {
      if (busy) return;
      hideSide();
      const error = validate();
      showError(error);
      if (error) return;

      setBusy(true);
      try {
         let res = current
            ? await call('/api/work-logs/' + current.id, 'PUT', request())
            : await call('/api/internships/' + internship.id + '/work-logs', 'POST', request());
         if (res.status !== 200 && res.status !== 201) {
            return showError(message(res, 'บันทึกไม่สำเร็จ กรุณาลองใหม่อีกครั้ง'));
         }
         const id = res.data.id;

         for (const file of [...(fileInput.files || [])]) {
            const form = new FormData();
            form.append('file', file);
            const up = await call('/api/work-logs/' + id + '/attachments', 'POST', form);
            if (up.status !== 201) {
               await reload(id);
               return showError(message(up, 'แนบไฟล์ ' + file.name + ' ไม่สำเร็จ'));
            }
         }

         if (submit) {
            res = await call('/api/work-logs/' + id + '/submit', 'POST');
            if (res.status !== 200) {
               await reload(id);
               return showError(message(res, 'ส่งบันทึกไม่สำเร็จ กรุณาลองใหม่อีกครั้ง'));
            }
            $(res.data.lateSubmitted ? '#wLate' : '#wOk').hidden = false;
         } else {
            $('#wSaved').hidden = false;
         }
         await reload(id);
      } catch (e) {
         showError('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
      } finally {
         setBusy(false);
      }
   }

   async function removeLog() {
      if (busy || !current || current.status !== 'DRAFT') return;
      if (!window.confirm('ลบบันทึกร่างวันที่ ' + wlThaiDate(current.logDate) + ' ?')) return;
      setBusy(true);
      try {
         const res = await call('/api/work-logs/' + current.id, 'DELETE');
         if (res.status !== 204) return showError(message(res, 'ลบบันทึกไม่สำเร็จ'));
         hideSide();
         fillForm(null);
         await refresh();
      } catch (e) {
         showError('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
      } finally {
         setBusy(false);
      }
   }

   async function removeFile(attachmentId) {
      if (busy || !current) return;
      setBusy(true);
      try {
         const res = await call('/api/work-logs/' + current.id + '/attachments/' + attachmentId, 'DELETE');
         if (res.status !== 204) return showError(message(res, 'ลบไฟล์ไม่สำเร็จ'));
         await reload(current.id);
      } catch (e) {
         showError('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง');
      } finally {
         setBusy(false);
      }
   }

   /* ---------- โหลดข้อมูล ---------- */
   async function open(id) {
      const res = await call('/api/work-logs/' + id);
      if (res.status !== 200) return showError(message(res, 'เปิดบันทึกไม่สำเร็จ'));
      fillForm(res.data);
   }

   async function reload(id) {
      await refresh();
      if (id != null) await open(id);
   }

   async function refresh() {
      const [list, sum] = await Promise.all([
         call('/api/internships/' + internship.id + '/work-logs'),
         call('/api/internships/' + internship.id + '/work-logs/summary')
      ]);
      logs = list.status === 200 && Array.isArray(list.data) ? list.data : [];
      renderHistory();
      renderSummary(sum.status === 200 ? sum.data : null);
   }

   function statusLabel(log) {
      if (log.status === 'SUBMITTED' && log.lateSubmitted) return WL_STATUS.LATE;
      return WL_STATUS[log.status] || [log.status, 'draft'];
   }

   function renderHistory() {
      const box = $('#wHist');
      box.textContent = '';
      if (!logs.length) {
         const empty = document.createElement('p');
         empty.className = 'mute';
         empty.textContent = 'ยังไม่มีบันทึก';
         box.append(empty);
         return;
      }
      [...logs].sort((a, b) => b.logDate.localeCompare(a.logDate)).forEach(log => {
         const [label, tone] = statusLabel(log);
         const row = document.createElement('button');
         const date = document.createElement('span');
         const pill = document.createElement('span');
         const hours = document.createElement('small');
         row.type = 'button';
         row.className = 'wl-hist' + (current && current.id === log.id ? ' cur' : '');
         row.dataset.id = log.id;
         date.textContent = wlThaiDate(log.logDate);
         pill.className = 'pill ' + tone;
         pill.textContent = label;
         hours.textContent = Number(log.hours) + ' ชม.';
         row.append(date, pill, hours);
         row.onclick = () => {
            hideSide();
            open(log.id).catch(() => showError('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง'));
         };
         box.append(row);
      });
   }

   function renderSummary(sum) {
      $('#sumHours').textContent = sum ? Number(sum.totalHours) + ' ชม.' : '-';
      $('#sumDays').textContent = sum ? sum.daysLogged + ' / ' + sum.workingDays : '-';
      const missing = sum && Array.isArray(sum.missingDates) ? sum.missingDates : [];
      $('#sumMissing').textContent = sum ? missing.length + ' วัน' : '-';
      $('#sumMissingList').hidden = !missing.length;
      $('#sumMissingList').textContent = missing.length
         ? 'ยังไม่บันทึก: ' + missing.slice(-5).map(wlThaiDate).join(', ') + (missing.length > 5 ? ' ...' : '')
         : '';
   }

   /* ---------- เริ่มต้น ---------- */
   fields.startTime.addEventListener('input', updateHours);
   fields.endTime.addEventListener('input', updateHours);
   fields.logDate.addEventListener('change', () => {
      updateDue();
      // วันที่มีบันทึกแล้ว → เปิดบันทึกนั้นแทนการสร้างซ้ำ
      const found = logs.find(l => l.logDate === fields.logDate.value);
      if (found && !current) {
         hideSide();
         open(found.id).catch(() => showError('ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง'));
      }
   });
   $('#wSend').onclick = () => save(true);
   $('#wDraft').onclick = () => save(false);
   $('#wDelete').onclick = removeLog;
   $('#wNew').onclick = () => {
      hideSide();
      fields.logDate.value = '';
      fillForm(null);
   };

   fillForm(null);

   window.U8Worklog = {
      ready: (async () => {
         try {
            const res = await call('/api/work-logs/my-internship');
            if (res.status !== 200) {
               showState(res.status === 404
                  ? 'ยังไม่มีข้อมูลการฝึกงาน จึงยังบันทึกการทำงานไม่ได้'
                  : message(res, 'โหลดข้อมูลการฝึกงานไม่สำเร็จ'));
               return lockForm();
            }
            internship = res.data;
            fields.logDate.min = internship.startDate;
            const today = wlIso(new Date());
            fields.logDate.max = internship.endDate && internship.endDate < today ? internship.endDate : today;
            if (internship.status !== 'IN_PROGRESS') {
               showState('บันทึกได้เฉพาะช่วงที่กำลังฝึกงาน (สถานะปัจจุบัน: ' + internship.status + ')');
            }
            fillForm(null);
            await refresh();
         } catch (e) {
            showState('ไม่สามารถเชื่อมต่อระบบได้ กรุณาตรวจสอบ Backend ที่ localhost:8080');
            lockForm();
         }
      })()
   };
}
