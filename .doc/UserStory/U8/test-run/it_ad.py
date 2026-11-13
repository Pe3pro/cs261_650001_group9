"""U8:T5 ส่วนที่ 6 รัน Integration Test API–DAO (IT-AD-01 ถึง IT-AD-18) กับระบบจริง (docker compose)
เรียก API ที่ http://host.docker.internal:8080 และตรวจ MySQL ที่ host.docker.internal:3306
รันผ่าน run-it-ad.ps1 (phase1 = ALTER notifications.type เป็น ENUM, phase2 = หลัง restart app รันทุก test)
"""
import json, os, sys, uuid, datetime as dt, urllib.request, urllib.error
import pymysql

BASE = os.environ.get("BASE", "http://host.docker.internal:8080")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "it-ad-result.json")
UPLOADS = "/app/uploads"   # --volumes-from cs261-app

def db():
    return pymysql.connect(host=os.environ.get("DB_HOST", "host.docker.internal"), port=3306, user="root",
                           password="root1234", database="cs261db", autocommit=True, charset="utf8mb4")

def q(sql, *args):
    with db() as c, c.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchall() if cur.description else cur.rowcount

def one(sql, *args):
    r = q(sql, *args)
    return r[0][0] if r else None

def call(method, path, uid=None, body=None, file=None):
    headers = {}
    data = None
    if uid is not None:
        headers["X-User-Id"] = str(uid)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if file is not None:
        name, content, ctype = file
        b = uuid.uuid4().hex
        data = (f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
                f"Content-Type: {ctype}\r\n\r\n").encode() + content + f"\r\n--{b}--\r\n".encode()
        headers["Content-Type"] = f"multipart/form-data; boundary={b}"
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            raw, status, ctype = r.read(), r.status, r.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        raw, status, ctype = e.read(), e.code, e.headers.get("Content-Type", "")
    js = None
    if "json" in ctype and raw:
        js = json.loads(raw.decode("utf-8"))
    return status, js, raw, ctype

def code(js):
    return js.get("code") if isinstance(js, dict) else None

PDF = b"%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"

def log_body(day, start="09:00", end="12:00", activity="ประชุมทีมและวางแผนงาน"):
    return {"logDate": day.isoformat(), "startTime": start, "endTime": end, "activity": activity,
            "learning": "การแบ่งงานด้วย Scrum", "problem": None}

results = []

def record(tc, ok, actual):
    results.append({"tc": tc, "pass": bool(ok), "actual": actual})
    print(("PASS " if ok else "FAIL ") + tc + " | " + actual, flush=True)

def phase1():
    rows = [r[0] for r in q("SELECT DISTINCT type FROM notifications") if r[0]]
    vals = sorted(set(rows) - {"WORK_LOG_SUBMITTED", "WORK_LOG_REVIEWED"}) or ["APPLICATION_SUBMITTED"]
    enum = ",".join("'" + v + "'" for v in vals)
    q(f"ALTER TABLE notifications MODIFY type ENUM({enum}) NOT NULL")
    col = q("SHOW COLUMNS FROM notifications LIKE 'type'")[0][1]
    json.dump({"it18_before": col}, open(OUT + ".phase1", "w", encoding="utf-8"), ensure_ascii=False)
    print("phase1: notifications.type =", col)

def phase2():
    p1 = json.load(open(OUT + ".phase1", encoding="utf-8"))
    uid = lambda u: call("GET", f"/api/users/{u}")[1]["id"]
    A, B, C, ADV, STAFF = uid("6609650031"), uid("6609650032"), uid("6609650033"), uid("advisor01"), uid("staff01")
    iA = call("GET", "/api/work-logs/my-internship", A)[1]
    iB = call("GET", "/api/work-logs/my-internship", B)[1]
    IA, IB = iA["id"], iB["id"]
    start = dt.date.fromisoformat(iA["startDate"])
    today = dt.date.today()
    days = [start + dt.timedelta(d) for d in range((today - start).days)]
    wd = [d for d in days if d.weekday() < 5]          # วันทำงานก่อนวันนี้ เรียงเก่า → ใหม่
    d1, d2, d3, d4 = wd[-1], wd[-2], wd[-3], wd[-4]
    count = lambda: one("SELECT COUNT(*) FROM work_log")
    notif = lambda u, t: one("SELECT COUNT(*) FROM notifications WHERE user_id=%s AND type=%s", u, t)
    meta = {"A": A, "B": B, "C": C, "advisor": ADV, "staff": STAFF, "iA": IA, "iB": IB, "start": str(start),
            "today(UTC)": str(today), "d1": str(d1), "d2": str(d2), "d3": str(d3), "d4": str(d4)}

    # IT-AD-18 (ส่วนที่ตรวจหลัง restart) · ส่วน submit ยืนยันใน IT-AD-08
    col18 = q("SHOW COLUMNS FROM notifications LIKE 'type'")[0][1]

    # IT-AD-01
    n0 = count()
    s, js, *_ = call("POST", f"/api/internships/{IA}/work-logs", A, log_body(d1))
    L1 = js and js.get("id")
    row = q("SELECT status, hours, student_id, created_at IS NOT NULL, updated_at IS NOT NULL, submitted_at FROM work_log WHERE id=%s", L1)
    r = row[0] if row else None
    ok = s == 201 and js["status"] == "DRAFT" and float(js["hours"]) == 3 and count() == n0 + 1 and r \
        and r[0] == "DRAFT" and float(r[1]) == 3 and r[2] == A and r[3] and r[4] and r[5] is None
    record("IT-AD-01", ok, f"{s} {js and js.get('status')} hours {js and js.get('hours')} · work_log {n0}→{count()} แถว id {L1}: status {r and r[0]} hours {r and r[1]} student_id {r and r[2]} (A={A}) created/updated มีค่า {bool(r and r[3] and r[4])} submitted_at {r and r[5]}")

    # IT-AD-02
    n0 = count()
    s, js, *_ = call("POST", f"/api/internships/{IA}/work-logs", A, log_body(d1))
    record("IT-AD-02", s == 409 and code(js) == "DUPLICATE_LOG_DATE" and count() == n0, f"{s} {code(js)} · work_log {n0}→{count()}")

    # IT-AD-03
    n0 = count()
    cases = [("อนาคต", log_body(today + dt.timedelta(1))), ("ก่อนเริ่มฝึก", log_body(start - dt.timedelta(3))),
             ("09:00–09:15", log_body(d2, "09:00", "09:15")), ("ไม่มี activity", log_body(d2, activity=""))]
    got = []
    for name, b in cases:
        s, js, *_ = call("POST", f"/api/internships/{IA}/work-logs", A, b)
        got.append((name, s, code(js)))
    ok = all(s == 400 and c == "INVALID_WORK_LOG" for _, s, c in got) and count() == n0
    record("IT-AD-03", ok, " · ".join(f"{n} {s} {c}" for n, s, c in got) + f" · work_log {n0}→{count()}")

    # IT-AD-04
    n0 = count()
    s1, j1, *_ = call("POST", f"/api/internships/{IA}/work-logs", C, log_body(d2))
    q("UPDATE internships SET status='COMPLETED' WHERE id=%s", IB)
    s2, j2, *_ = call("POST", f"/api/internships/{IB}/work-logs", B, log_body(d2))
    q("UPDATE internships SET status='IN_PROGRESS' WHERE id=%s", IB)
    back = one("SELECT status FROM internships WHERE id=%s", IB)
    ok = s1 == 403 and code(j1) == "FORBIDDEN" and s2 == 409 and code(j2) == "INTERNSHIP_NOT_IN_PROGRESS" and count() == n0 and back == "IN_PROGRESS"
    record("IT-AD-04", ok, f"C→iA {s1} {code(j1)} · B (COMPLETED) {s2} {code(j2)} · work_log {n0}→{count()} · คืน status {back}")

    # IT-AD-05
    before = one("SELECT updated_at FROM work_log WHERE id=%s", L1)
    import time; time.sleep(1.1)
    s, js, *_ = call("PUT", f"/api/work-logs/{L1}", A, log_body(d1, end="13:00"))
    r = q("SELECT end_time, hours, updated_at FROM work_log WHERE id=%s", L1)[0]
    ok = s == 200 and float(js["hours"]) == 4 and str(r[0]) == "13:00:00" and float(r[1]) == 4 and r[2] != before
    record("IT-AD-05", ok, f"{s} hours {js and js.get('hours')} · end_time {r[0]} hours {r[1]} · updated_at {before}→{r[2]}")

    # IT-AD-06
    a0 = one("SELECT COUNT(*) FROM work_log_attachment WHERE work_log_id=%s", L1)
    s1, j1, *_ = call("POST", f"/api/work-logs/{L1}/attachments", A, file=("note.pdf", PDF, "application/pdf"))
    s2, j2, *_ = call("POST", f"/api/work-logs/{L1}/attachments", A, file=("note.txt", b"hello", "text/plain"))
    rows = q("SELECT content_type, file_path FROM work_log_attachment WHERE work_log_id=%s ORDER BY id", L1)
    ok = s1 == 201 and j1["contentType"] == "application/pdf" and s2 == 400 and code(j2) == "INVALID_ATTACHMENT" \
        and len(rows) == a0 + 1 and rows[-1][0] == "application/pdf" and f"work-logs/{L1}/" in rows[-1][1]
    record("IT-AD-06", ok, f"PDF {s1} {j1 and j1.get('contentType')} · .txt {s2} {code(j2)} · attachment {a0}→{len(rows)} แถว content_type {rows[-1][0] if rows else None} file_path {rows[-1][1] if rows else None}")

    # IT-AD-07
    for i in range(4):
        call("POST", f"/api/work-logs/{L1}/attachments", A, file=(f"p{i+2}.pdf", PDF, "application/pdf"))
    a5 = one("SELECT COUNT(*) FROM work_log_attachment WHERE work_log_id=%s", L1)
    s, js, *_ = call("POST", f"/api/work-logs/{L1}/attachments", A, file=("p6.pdf", PDF, "application/pdf"))
    a6 = one("SELECT COUNT(*) FROM work_log_attachment WHERE work_log_id=%s", L1)
    record("IT-AD-07", a5 == 5 and s == 400 and code(js) == "INVALID_ATTACHMENT" and a6 == 5, f"มีไฟล์ {a5} แล้วแนบไฟล์ที่ 6: {s} {code(js)} · attachment คงที่ {a6} แถว")

    # IT-AD-08 (+ IT-AD-18 ส่วน submit)
    nN = notif(ADV, "WORK_LOG_SUBMITTED")
    t0 = dt.datetime.utcnow().replace(microsecond=0)
    s, js, *_ = call("POST", f"/api/work-logs/{L1}/submit", A)
    r = q("SELECT status, submitted_at FROM work_log WHERE id=%s", L1)[0]
    nN2 = notif(ADV, "WORK_LOG_SUBMITTED")
    sub_ok = r[1] is not None and abs((r[1] - t0).total_seconds()) < 120
    record("IT-AD-08", s == 200 and js["status"] == "SUBMITTED" and r[0] == "SUBMITTED" and sub_ok and nN2 == nN + 1,
           f"{s} {js and js.get('status')} lateSubmitted {js and js.get('lateSubmitted')} · status {r[0]} submitted_at {r[1]} (ยิงตอน {t0} UTC) · notifications อาจารย์ WORK_LOG_SUBMITTED {nN}→{nN2}")
    it18_submit = (s, nN, nN2)

    # IT-AD-09
    snap = lambda: q("SELECT status, end_time, hours, updated_at, submitted_at FROM work_log WHERE id=%s", L1)[0]
    b = snap(); nN = notif(ADV, "WORK_LOG_SUBMITTED")
    r1 = call("PUT", f"/api/work-logs/{L1}", A, log_body(d1, end="14:00"))
    r2 = call("DELETE", f"/api/work-logs/{L1}", A)
    r3 = call("POST", f"/api/work-logs/{L1}/submit", A)
    got = [(x[0], code(x[1])) for x in (r1, r2, r3)]
    ok = all(g == (409, "INVALID_STATUS") for g in got) and snap() == b and notif(ADV, "WORK_LOG_SUBMITTED") == nN
    record("IT-AD-09", ok, f"PUT {got[0]} · DELETE {got[1]} · submit {got[2]} · แถวเหมือนเดิม {snap() == b} · notifications {nN}→{notif(ADV, 'WORK_LOG_SUBMITTED')}")

    # IT-AD-10
    s, js, *_ = call("POST", f"/api/internships/{IA}/work-logs", A, log_body(d2))
    L2 = js["id"]
    call("POST", f"/api/work-logs/{L2}/attachments", A, file=("del.pdf", PDF, "application/pdf"))
    path = one("SELECT file_path FROM work_log_attachment WHERE work_log_id=%s", L2) or ""
    local = os.path.join(UPLOADS, path[path.find("work-logs/"):]) if "work-logs/" in path else None
    existed = bool(local and os.path.exists(local))
    n0 = count()
    s, js, *_ = call("DELETE", f"/api/work-logs/{L2}", A)
    att = one("SELECT COUNT(*) FROM work_log_attachment WHERE work_log_id=%s", L2)
    gone = not (local and os.path.exists(local))
    record("IT-AD-10", s == 204 and count() == n0 - 1 and att == 0 and existed and gone,
           f"{s} · work_log {n0}→{count()} · attachment ของบันทึก {L2}: {att} แถว · ไฟล์ {path}: ก่อนลบมีใน volume {existed} หลังลบ {'หายแล้ว' if gone else 'ยังอยู่'}")

    # เตรียม L3 (SUBMITTED) และ L4 (DRAFT) ไว้ใช้ใน IT-AD-11, 13, 14
    L3 = call("POST", f"/api/internships/{IA}/work-logs", A, log_body(d3))[1]["id"]
    call("POST", f"/api/work-logs/{L3}/submit", A)
    L4 = call("POST", f"/api/internships/{IA}/work-logs", A, log_body(d4, "13:00", "17:00"))[1]["id"]

    # IT-AD-11
    s1, j1, *_ = call("GET", "/api/work-logs/pending-review", ADV)
    s2, j2, *_ = call("GET", "/api/work-logs/pending-review", A)
    sql = one("SELECT COUNT(*) FROM work_log w JOIN students s ON w.student_id = s.user_id WHERE s.advisor_id=%s AND w.status='SUBMITTED'", ADV)
    ids = [x["id"] for x in j1] if isinstance(j1, list) else []
    ok = s1 == 200 and all(x["status"] == "SUBMITTED" for x in j1) and len(ids) == sql and {L1, L3} <= set(ids) and s2 == 403
    record("IT-AD-11", ok, f"อาจารย์ {s1} {len(ids)} รายการ id {ids} ทุกตัว SUBMITTED · SQL COUNT = {sql} · นักศึกษา {s2} {code(j2)}")

    # IT-AD-12
    nR = notif(A, "WORK_LOG_REVIEWED")
    s1, j1, *_ = call("POST", f"/api/work-logs/{L1}/review", ADV, {"decision": "REVISE"})
    st1 = one("SELECT status FROM work_log WHERE id=%s", L1)
    s2, j2, *_ = call("POST", f"/api/work-logs/{L1}/review", ADV, {"decision": "REVISE", "comment": "เพิ่มรายละเอียด"})
    r = q("SELECT status, reviewed_by, reviewed_at, review_comment FROM work_log WHERE id=%s", L1)[0]
    nR2 = notif(A, "WORK_LOG_REVIEWED")
    ok = s1 == 400 and code(j1) == "COMMENT_REQUIRED" and st1 == "SUBMITTED" and s2 == 200 and j2["status"] == "NEED_REVISION" \
        and r[0] == "NEED_REVISION" and r[1] == ADV and r[2] is not None and r[3] == "เพิ่มรายละเอียด" and nR2 == nR + 1
    record("IT-AD-12", ok, f"ไม่มีความเห็น {s1} {code(j1)} (status ยัง {st1}) · มีความเห็น {s2} {j2 and j2.get('status')} · reviewed_by {r[1]} reviewed_at {r[2]} review_comment \"{r[3]}\" · notifications นักศึกษา WORK_LOG_REVIEWED {nR}→{nR2}")

    # IT-AD-13
    b1 = q("SELECT status, reviewed_by, reviewed_at, review_comment FROM work_log WHERE id IN (%s,%s) ORDER BY id", L1, L3)
    nR = notif(A, "WORK_LOG_REVIEWED")
    s1, j1, *_ = call("POST", f"/api/work-logs/{L1}/review", ADV, {"decision": "APPROVE"})
    q("UPDATE students SET advisor_id=NULL WHERE user_id=%s", A)
    s2, j2, *_ = call("POST", f"/api/work-logs/{L3}/review", ADV, {"decision": "APPROVE"})
    q("UPDATE students SET advisor_id=%s WHERE user_id=%s", ADV, A)
    back = one("SELECT advisor_id FROM students WHERE user_id=%s", A)
    ok = s1 == 409 and code(j1) == "INVALID_STATUS" and s2 == 403 and code(j2) == "FORBIDDEN" and back == ADV \
        and q("SELECT status, reviewed_by, reviewed_at, review_comment FROM work_log WHERE id IN (%s,%s) ORDER BY id", L1, L3) == b1 \
        and notif(A, "WORK_LOG_REVIEWED") == nR
    record("IT-AD-13", ok, f"review NEED_REVISION {s1} {code(j1)} · ไม่ใช่ที่ปรึกษา {s2} {code(j2)} · แถวไม่เปลี่ยน · notifications {nR}→{notif(A, 'WORK_LOG_REVIEWED')} · คืน advisor_id = {back}")

    # IT-AD-14
    nR = notif(A, "WORK_LOG_REVIEWED")
    s1, j1, *_ = call("POST", "/api/work-logs/review-bulk", ADV, {"workLogIds": [L3, L4, 9999], "comment": "เรียบร้อย"})
    s2, j2, *_ = call("POST", "/api/work-logs/review-bulk", ADV, {"workLogIds": []})
    st3 = one("SELECT status FROM work_log WHERE id=%s", L3); st4 = one("SELECT status FROM work_log WHERE id=%s", L4)
    sk = j1.get("skipped", []) if isinstance(j1, dict) else []
    ok = s1 == 200 and j1["approved"] == [L3] and len(sk) == 2 and all(x.get("reason") for x in sk) and s2 == 400 \
        and st3 == "APPROVED" and st4 == "DRAFT" and notif(A, "WORK_LOG_REVIEWED") == nR + 1
    record("IT-AD-14", ok, f"{s1} approved {j1 and j1.get('approved')} skipped {[(x['workLogId'], x['reason']) for x in sk]} · [] {s2} {code(j2)} · L{L3} {st3} · L{L4} {st4} · notifications {nR}→{notif(A, 'WORK_LOG_REVIEWED')}")

    # IT-AD-15
    s, js, *_ = call("GET", f"/api/internships/{IA}/work-logs/summary", A)
    total = one("SELECT COALESCE(SUM(hours),0) FROM work_log WHERE internship_id=%s AND status<>'DRAFT'", IA)
    grp = {k: v for k, v in q("SELECT status, COUNT(*) FROM work_log WHERE internship_id=%s GROUP BY status", IA)}
    logged = {r[0] for r in q("SELECT log_date FROM work_log WHERE internship_id=%s", IA)}
    upto = today
    exp_missing = [str(start + dt.timedelta(d)) for d in range((upto - start).days + 1)
                   if (start + dt.timedelta(d)).weekday() < 5 and (start + dt.timedelta(d)) not in logged]
    cbs = {k: v for k, v in (js.get("countByStatus") or {}).items() if v}
    ok = s == 200 and float(js["totalHours"]) == float(total) and cbs == grp and js["missingDates"] == exp_missing
    record("IT-AD-15", ok, f"{s} totalHours {js.get('totalHours')} (SQL {total}) · countByStatus {cbs} (SQL {grp}) · missingDates {len(js.get('missingDates', []))} วัน ตรงกับวัน จ.–ศ. ที่ไม่มีแถว {js.get('missingDates') == exp_missing} · daysLogged {js.get('daysLogged')} workingDays {js.get('workingDays')}")

    # IT-AD-16
    s1, _, raw, ct = call("GET", f"/api/internships/{IA}/work-logs/export", ADV)
    s2, j2, *_ = call("GET", f"/api/internships/{IA}/work-logs/export", A)
    nd = one("SELECT COUNT(*) FROM work_log WHERE internship_id=%s AND status<>'DRAFT'", IA)
    lines = [l for l in raw.decode("utf-8-sig", "replace").splitlines() if l.strip()] if s1 == 200 else []
    bom = raw.startswith(b"\xef\xbb\xbf")
    ok = s1 == 200 and ct.startswith("text/csv") and bom and len(lines) == nd + 1 and s2 == 403
    record("IT-AD-16", ok, f"อาจารย์ {s1} {ct} BOM {bom} {len(lines)} บรรทัด (หัวตาราง 1 + บันทึกที่ไม่ใช่ DRAFT {nd}) · นักศึกษา {s2} {code(j2)}")

    # IT-AD-17
    before = q("SELECT id, status, updated_at FROM work_log ORDER BY id")
    res = {}
    for who, u in (("A", A), ("advisor", ADV), ("staff", STAFF), ("C", C), ("no header", None)):
        s, js, *_ = call("GET", f"/api/internships/{IA}/work-logs", u)
        res[who] = (s, sorted({x["status"] for x in js}) if isinstance(js, list) else code(js))
    ok = res["A"][0] == 200 and "DRAFT" in res["A"][1] and res["advisor"][0] == 200 and "DRAFT" not in res["advisor"][1] \
        and res["staff"][0] == 200 and "DRAFT" not in res["staff"][1] and res["C"][0] == 403 and res["no header"][0] == 401 \
        and q("SELECT id, status, updated_at FROM work_log ORDER BY id") == before
    record("IT-AD-17", ok, " · ".join(f"{k} {v[0]} {v[1]}" for k, v in res.items()) + " · ข้อมูลไม่เปลี่ยน")

    # IT-AD-18
    s, nN, nN2 = it18_submit
    record("IT-AD-18", "enum" in p1["it18_before"].lower() and col18.lower() == "varchar(50)" and s == 200 and nN2 == nN + 1,
           f"ก่อน restart {p1['it18_before']} → หลัง restart {col18} · submit (IT-AD-08) {s} · notifications WORK_LOG_SUBMITTED {nN}→{nN2}")

    final = {
        "work_log": [list(map(str, r)) for r in q("SELECT id, internship_id, student_id, log_date, start_time, end_time, hours, status, submitted_at, reviewed_by, review_comment FROM work_log ORDER BY id")],
        "work_log_attachment": [list(map(str, r)) for r in q("SELECT work_log_id, file_name, content_type, size FROM work_log_attachment ORDER BY id")],
        "notifications": [list(map(str, r)) for r in q("SELECT user_id, type, title, is_read FROM notifications WHERE type LIKE 'WORK_LOG%%' ORDER BY id")],
    }
    json.dump({"meta": meta, "results": results, "db": final}, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"\n{sum(r['pass'] for r in results)} / {len(results)} ผ่าน · ผลอยู่ที่ {OUT}")

{"phase1": phase1, "phase2": phase2}[sys.argv[1]]()
