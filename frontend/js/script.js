/* =========================================================
   script.js
   ใช้ร่วมกัน: login, check, submit, ranking
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
        ranking: initRanking
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
   หน้าจัดอันดับบริษัท (U4)
   API: GET /api/companies · GET /api/students/me/company-ranking
        PUT /api/students/me/company-ranking  {"companyIds": [อันดับ1, ..., อันดับ5]}
   ========================================================= */
function initRanking() {
   const API = window.U4_API_BASE || 'http://localhost:8080';
   const MAX = 5;
   const MSG = {
      incomplete: n => `กรุณาเลือกบริษัทให้ครบ ${MAX} อันดับ (เลือกแล้ว ${n} อันดับ)`,
      network: 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่อีกครั้ง',
      login: 'กรุณาเข้าสู่ระบบใหม่อีกครั้ง',
      load: 'โหลดรายชื่อบริษัทไม่สำเร็จ',
      empty: 'ยังไม่มีบริษัทที่เปิดรับ',
      notFound: 'ไม่พบบริษัทที่ค้นหา'
   };

   let companies = [];                  // บริษัทที่เปิดรับ (จาก API)
   let picks = Array(MAX).fill(null);   // id บริษัทของอันดับ 1–5
   let open = -1;                       // อันดับที่กำลังค้นหา (-1 = ไม่มี)
   let query = '';
   let hl = 0;                          // รายการที่ไฮไลต์ในเมนู
   let locked = false;
   let busy = false;

   const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g,
      c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
   const byId = id => companies.find(c => c.id === id);
   const count = () => picks.filter(Boolean).length;

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
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      const res = await fetch(API + path, {
         method,
         headers,
         body: body === undefined ? undefined : JSON.stringify(body)
      });
      let data = null;
      try {
         data = await res.json();
      } catch (e) {
         data = null;
      }
      return { status: res.status, data };
   }

   const errText = (r, fallback) =>
      r.status === 401 ? MSG.login : (r.data && r.data.message) || fallback;

   /* ร่างที่เลือกไว้ (ยังไม่กดยืนยัน) เก็บใน sessionStorage ออกจากหน้าแล้วกลับมายังอยู่ */
   function keep() {
      S.rank = picks.slice();
      save();
   }

   function hideMsg() {
      $('#msgOk').hidden = true;
      $('#msgBad').hidden = true;
   }

   function bad(text) {
      $('#msgOk').hidden = true;
      $('#msgBad').hidden = false;
      $('#msgBadTxt').textContent = text;
   }

   function lock(text) {
      locked = true;
      open = -1;
      $('#rkLock').textContent = text;
      $('#rkLock').hidden = false;
   }

   /* บริษัทที่ค้นเจอ: ยังไม่ถูกเลือกในอันดับอื่น และชื่อ/ที่อยู่ตรงกับคำค้น */
   function matches(slot) {
      const q = query.trim().toLowerCase();
      return companies.filter(c =>
         !picks.some((id, i) => i !== slot && id === c.id) &&
         (!q || `${c.name} ${c.address || ''}`.toLowerCase().includes(q)));
   }

   const coHtml = c => `<i class="rk-logo"></i><span class="rk-co"><b>${esc(c.name)}</b>` +
      `<small>${esc(c.address || '')}</small></span>`;

   function menuHtml(slot) {
      const list = matches(slot);
      hl = Math.min(hl, Math.max(list.length - 1, 0));
      const taken = picks.filter((id, i) => id && i !== slot).map(id => byId(id)).filter(Boolean);
      const items = list.length
         ? list.map((c, i) => `<li class="rk-opt${i === hl ? ' hl' : ''}" role="option" data-id="${c.id}"` +
            ` aria-selected="${i === hl}">${coHtml(c)}</li>`).join('')
         : `<li class="rk-note">${companies.length ? MSG.notFound : MSG.empty}</li>`;
      const note = taken.length
         ? `<li class="rk-note">ไม่แสดงบริษัทที่เลือกในอันดับอื่นแล้ว (${taken.map(c => esc(c.name)).join(', ')})</li>`
         : '';
      return `<ul class="rk-menu" role="listbox" id="rkMenu">${items}${note}</ul>`;
   }

   function render() {
      $('#rkSlots').innerHTML = picks.map((id, i) => {
         const c = id && byId(id);
         const cls = c ? 'filled' : i === open ? 'active' : '';
         const field = c
            ? `<div class="rk-pick">${coHtml(c)}` +
              (locked ? '' : `<button class="rk-clear" type="button" data-clear="${i}"` +
                 ` aria-label="ล้างอันดับ ${i + 1}">ล้าง ✕</button>`) + '</div>'
            : `<input class="rk-search" data-slot="${i}" type="text" autocomplete="off"` +
              ` placeholder="ค้นหาบริษัท..." aria-label="ค้นหาบริษัทสำหรับอันดับ ${i + 1}"` +
              ` value="${i === open ? esc(query) : ''}"${locked ? ' disabled' : ''}>` +
              (i === open ? menuHtml(i) : '');
         return `<li class="rk-row ${cls}"><span class="rk-no">${i + 1}</span>` +
            `<span class="rk-lbl">อันดับ ${i + 1}</span><div class="rk-field">${field}</div></li>`;
      }).join('');

      $('#rkCount').textContent = `เลือกแล้ว ${count()} จาก ${MAX} อันดับ`;
      $('#btnConfirm').disabled = locked || busy;
      $('#btnClear').disabled = locked || busy || !count();

      if (open >= 0) {
         const input = $(`.rk-search[data-slot="${open}"]`);
         if (input) {
            input.focus();
            input.setSelectionRange(query.length, query.length);
         }
      }
   }

   function choose(slot, id) {
      picks[slot] = id;
      open = -1;
      query = '';
      keep();
      hideMsg();
      render();
   }

   /* ---------- เหตุการณ์ในช่องอันดับ ---------- */
   const slots = $('#rkSlots');

   slots.addEventListener('focusin', e => {
      const input = e.target.closest('.rk-search');
      if (!input || locked) return;
      const slot = Number(input.dataset.slot);
      if (slot !== open) {
         open = slot;
         query = '';
         hl = 0;
         render();
      }
   });

   slots.addEventListener('input', e => {
      if (!e.target.classList.contains('rk-search')) return;
      query = e.target.value;
      hl = 0;
      render();
   });

   slots.addEventListener('keydown', e => {
      if (!e.target.classList.contains('rk-search') || open < 0) return;
      const list = matches(open);
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
         e.preventDefault();
         if (list.length) {
            hl = (hl + (e.key === 'ArrowDown' ? 1 : -1) + list.length) % list.length;
            render();
         }
      } else if (e.key === 'Enter') {
         e.preventDefault();
         if (list[hl]) choose(open, list[hl].id);
      } else if (e.key === 'Escape') {
         open = -1;
         query = '';
         render();
      }
   });

   /* mousedown: เลือกก่อนช่องค้นหาเสียโฟกัส */
   slots.addEventListener('mousedown', e => {
      const opt = e.target.closest('.rk-opt');
      if (opt) {
         e.preventDefault();
         choose(open, Number(opt.dataset.id));
      }
   });

   slots.addEventListener('click', e => {
      const btn = e.target.closest('[data-clear]');
      if (!btn || locked) return;
      picks[Number(btn.dataset.clear)] = null;
      keep();
      hideMsg();
      render();
   });

   /* คลิกนอกช่องค้นหา ปิดเมนู */
   document.addEventListener('mousedown', e => {
      if (open >= 0 && !e.target.closest('.rk-field')) {
         open = -1;
         query = '';
         render();
      }
   });

   /* ---------- ปุ่มล่าง ---------- */
   $('#btnClear').onclick = () => {
      picks = Array(MAX).fill(null);
      open = -1;
      query = '';
      keep();
      hideMsg();
      render();
   };

   $('#btnConfirm').onclick = async () => {
      if (busy || locked) return;
      hideMsg();
      if (count() < MAX) {
         return bad(MSG.incomplete(count()));
      }

      busy = true;
      $('#btnConfirm').textContent = 'กำลังบันทึก...';
      render();
      try {
         const r = await call('/api/students/me/company-ranking', 'PUT', { companyIds: picks });
         if (r.status === 200 && Array.isArray(r.data)) {
            r.data.forEach(item => { picks[item.rank - 1] = item.companyId; });
            keep();
            $('#msgOk').hidden = false;
         } else if (r.status === 409) {
            lock(errText(r, MSG.network));
            bad(errText(r, MSG.network));
         } else {
            bad(errText(r, MSG.network));
         }
      } catch (e) {
         bad(MSG.network);
      } finally {
         busy = false;
         $('#btnConfirm').textContent = 'ยืนยันการเลือก';
         render();
      }
   };

   /* ---------- โหลดบริษัทและอันดับที่เคยบันทึก ---------- */
   async function load() {
      render();
      try {
         const [co, mine] = await Promise.all([
            call('/api/companies'),
            call('/api/students/me/company-ranking')
         ]);
         if (co.status !== 200 || !Array.isArray(co.data)) {
            throw Object.assign(new Error('load'), { text: errText(co, MSG.load) });
         }
         companies = co.data;

         const saved = mine.status === 200 && Array.isArray(mine.data) ? mine.data : [];
         if (saved.length) {
            saved.forEach(item => {
               if (item.rank >= 1 && item.rank <= MAX) picks[item.rank - 1] = item.companyId;
            });
         } else if (Array.isArray(S.rank)) {
            picks = S.rank.slice(0, MAX).map(id => (byId(id) ? id : null));
            while (picks.length < MAX) picks.push(null);
         }
         // บริษัทที่บันทึกไว้แต่ถูกปิดรับไปแล้ว ยังแสดงชื่อจาก API ลำดับ
         saved.forEach(item => {
            if (!byId(item.companyId)) {
               companies.push({ id: item.companyId, name: item.companyName, address: '' });
            }
         });
         $('#rkLoading').hidden = true;
      } catch (e) {
         $('#rkLoading').hidden = true;
         $('#rkLoadErr').textContent = e.text || MSG.network;
         $('#rkLoadErr').hidden = false;
         locked = true;
      }
      render();
   }

   window.U4Ranking = { ready: load() };
}