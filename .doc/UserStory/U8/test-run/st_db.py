"""U8:T5 ส่วนที่ 7 (System Test) ส่วนที่ต้องใช้ MySQL: ST-U8-03 (การฝึกงาน COMPLETED → 409) และตรวจข้อมูลในฐานข้อมูล
ของ ST-U8-01, 04, 06, 08, 10, 13, 15 หลังรันขั้นตอนบนหน้าเว็บแล้ว · รันผ่าน run-st-db.ps1"""
import json, os, datetime as dt, urllib.request, urllib.error
import pymysql

BASE = "http://host.docker.internal:8080"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "st-db-result.json")

def q(sql, *a):
    with pymysql.connect(host="host.docker.internal", user="root", password="root1234", database="cs261db",
                         autocommit=True, charset="utf8mb4") as c, c.cursor() as cur:
        cur.execute(sql, a)
        return cur.fetchall() if cur.description else cur.rowcount

def call(m, p, uid, body=None):
    req = urllib.request.Request(BASE + p, method=m, data=json.dumps(body).encode() if body else None,
                                 headers={"X-User-Id": str(uid), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")

uid = lambda u: call("GET", f"/api/users/{u}", 1)[1]["id"]
A, B = uid("6609650031"), uid("6609650032")
iB = call("GET", "/api/work-logs/my-internship", B)[1]["id"]
iA = call("GET", "/api/work-logs/my-internship", A)[1]["id"]
out = {}

# ST-U8-03 ส่วนการฝึกงานไม่ใช่ IN_PROGRESS
n0 = q("SELECT COUNT(*) FROM work_log")[0][0]
q("UPDATE internships SET status='COMPLETED' WHERE id=%s", iB)
s, j = call("POST", f"/api/internships/{iB}/work-logs", B,
            {"logDate": str(dt.date.today() - dt.timedelta(days=1)), "startTime": "09:00", "endTime": "12:00",
             "activity": "x", "learning": "y", "problem": None})
q("UPDATE internships SET status='IN_PROGRESS' WHERE id=%s", iB)
out["ST-U8-03"] = {"status": s, "code": j and j.get("code"), "work_log": [n0, q("SELECT COUNT(*) FROM work_log")[0][0]],
                   "restored": q("SELECT status FROM internships WHERE id=%s", iB)[0][0]}

out["work_log"] = [[str(x) for x in r] for r in q(
    "SELECT id, internship_id, student_id, log_date, start_time, end_time, hours, status, submitted_at, reviewed_by, review_comment "
    "FROM work_log ORDER BY id")]
att = q("SELECT id, work_log_id, file_name, content_type, size, file_path FROM work_log_attachment ORDER BY id")
out["work_log_attachment"] = [[str(x) for x in r] + [os.path.exists(os.path.join("/app/uploads", r[5][r[5].find("work-logs/"):]))] for r in att]
out["notifications"] = [[str(x) for x in r] for r in q(
    "SELECT id, user_id, type, title, created_at FROM notifications WHERE type LIKE 'WORK_LOG%%' ORDER BY id")]
out["sum_non_draft"] = str(q("SELECT COALESCE(SUM(hours),0) FROM work_log WHERE internship_id=%s AND status<>'DRAFT'", iA)[0][0])
out["group_by_status"] = {k: v for k, v in q("SELECT status, COUNT(*) FROM work_log WHERE internship_id=%s GROUP BY status", iA)}
out["log_dates"] = [str(r[0]) for r in q("SELECT log_date FROM work_log WHERE internship_id=%s ORDER BY log_date", iA)]
out["summary_api"] = call("GET", f"/api/internships/{iA}/work-logs/summary", A)[1]
out["db_now"] = str(q("SELECT NOW()")[0][0])
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=str)
print(json.dumps(out["ST-U8-03"], ensure_ascii=False))
print("done ->", OUT)
