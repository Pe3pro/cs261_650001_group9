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
        id: '6410000001',
        fac: 'วิทยาศาสตร์',
        major: 'วิทยาการคอมพิวเตอร์',
        gpax: '3.10'
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
      const grade = valOf('g', index, 'B');

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
      const status = valOf('s', index, 'กำลังศึกษา');

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
      grades[course] = valOf('g', index, 'B');
   });

   curriculum.now.forEach((item, index) => {
      const course = codeOf('s', item, index);

      courseStatuses[course] =
         valOf('s', index, 'กำลังศึกษา');
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

   if (!Object.prototype.hasOwnProperty.call(CUR, S.cur)) {
      S.cur = '61';
   }

   $('#f_id').value = S.form.id;
   $('#f_gpax').value = S.form.gpax;
   $('#f_prog').value = S.cur;

   const sync = () => {
      $('#btnCheck').disabled =
         busy || !S.ack.every(value => value === true);
   };

   function draw() {
      const curriculum = CUR[S.cur];

      $('#coreRows').innerHTML = curriculum.core
         .map((item, index) =>
            rowHtml('g', item, index, GRADE_OPTS, 'B')
         )
         .join('');

      $('#nowRows').innerHTML = curriculum.now
         .map((item, index) =>
            rowHtml(
               's',
               item,
               index,
               STAT_OPTS,
               'กำลังศึกษา'
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

   const form = $('#vForm');

   /* เก็บค่าจากช่องกรอกข้อมูล */
   form.addEventListener('input', event => {
      if (event.target.id === 'f_id') {
         S.form.id = event.target.value;
      }

      if (event.target.id === 'f_gpax') {
         S.form.gpax = event.target.value;
      }

      save();
   });

   /* เก็บค่าจากตัวเลือกและ checkbox */
   form.addEventListener('change', event => {
      const target = event.target;
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
      const id = String(S.form.id ?? '').trim();
      const gpax = String(S.form.gpax ?? '').trim();

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
      S.form.id = $('#f_id').value;
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
         const response = await fetch(
            ELIGIBILITY_API,
            {
               method: 'POST',

               headers: {
                  'Content-Type': 'application/json'
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

   /* U5: ช่องแนบไฟล์ที่เจ้าหน้าที่เพิ่มในแบบฟอร์ม (ช่องไฟล์ช่องแรกใช้ช่อง Transcript เดิม #s_tr) */
   const extraFileFields = () => ((form && form.fields) || []).filter(x => x.fieldType === 'FILE').slice(1);

   function renderExtraFiles() {
      document.querySelectorAll('.u5-extra-file').forEach(el => el.remove());
      const anchor = $('#s_err');
      if (!anchor) return;
      extraFileFields().forEach(f => {
         const box = document.createElement('div');
         box.className = 'fld u5-extra-file';
         const label = document.createElement('label');
         label.htmlFor = 's_file_' + f.id;
         label.textContent = f.label + (f.required ? ' *' : '');
         const input = document.createElement('input');
         input.type = 'file';
         input.id = 's_file_' + f.id;
         input.accept = '.pdf,application/pdf';
         input.dataset.fieldId = String(f.id);
         box.append(label, input);
         anchor.before(box);
      });
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
            renderExtraFiles();
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
         // U5: ขาดไฟล์ของช่องแนบไฟล์อื่น → แสดงข้อความจาก server ที่บอกชื่อช่อง
         const other = /กรุณาแนบไฟล์ (?!Transcript)/.test(msg);
         return result(false, MSG.fail1, false, other ? msg : MSG.noFile, { retry: true, edit: true });
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
      // U5: ไฟล์ของช่องแนบไฟล์อื่นในแบบฟอร์ม [{fieldId, file}]
      const extras = [...document.querySelectorAll('.u5-extra-file input[type=file]')]
         .filter(i => i.files && i.files[0]).map(i => ({ fieldId: i.dataset.fieldId, file: i.files[0] }));
      for (const f of [file, ...extras.map(x => x.file)]) {
         if (f && !/\.pdf$/i.test(f.name)) {
            return result(false, MSG.fail1, false, MSG.pdf, { edit: true });
         }
         if (f && f.size > MAX_SIZE) {
            return result(false, MSG.fail1, false, MSG.size, { edit: true });
         }
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
         for (const x of extras) {
            const fd = new FormData();
            fd.append('file', x.file, x.file.name);
            const up = await call('/api/requests/' + reqId + '/documents?fieldId=' + encodeURIComponent(x.fieldId), 'POST', fd);
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