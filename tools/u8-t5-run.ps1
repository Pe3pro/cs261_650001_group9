# =====================================================================
# U8:T5 Run Test ทั้งชุด (Integration UI-API, Integration API-DAO, System Test, UAT dry-run) กับระบบจริง
# รันจากโฟลเดอร์หลักของโปรเจกต์:
#   powershell -ExecutionPolicy Bypass -File tools\u8-t5-run.ps1            (ทุกส่วน ~10-15 นาที)
#   powershell -ExecutionPolicy Bypass -File tools\u8-t5-run.ps1 ui st      (เลือกเฉพาะ: apidao ui st uat)
# - เครื่องทีมไม่ต้องมี node / python: สคริปต์รันใน container (Playwright, Python) บน docker network เดียวกับ app
# - ทุกส่วนเริ่มจากฐานข้อมูลใหม่ (docker compose down -v / up -d --build) และเปิดข้อมูลตัวอย่างของ U8
# - IT-UA-17 / ST-U8-18 หยุด backend จริงด้วย docker compose stop app แล้วเปิดคืนด้วย docker compose start app
# - ผล: .doc\UserStory\U8\evidence\T5-*-output.txt / *-result.json / T5-*.png
# =====================================================================
param([string[]]$Parts = @("apidao", "ui", "st", "uat"))
$ErrorActionPreference = "Stop"
$env:APP_SEED_U8_DATA = "true"
$PW_IMAGE = "mcr.microsoft.com/playwright:v1.56.1-noble"

function Wait-App {
    Write-Host "  waiting for app (U8 seed) ..."
    for ($i = 0; $i -lt 120; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 http://localhost:8080/api/users/6609650031
            if ($r.StatusCode -eq 200) { Write-Host "  app ready"; return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "app did not start"
}
function Reset-Db {
    Write-Host "`n== reset database (docker compose down -v / up -d --build) =="
    docker compose down -v
    docker compose up -d --build
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }
    Wait-App
}
function Net { (docker inspect cs261-app -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}').Trim() }
function Run-Py($phase) {
    docker run --rm --network (Net) --volumes-from cs261-app:ro -v "${PWD}:/w" -w /w -e REPO=/w python:3.12-slim `
        sh -c "pip install -q --root-user-action=ignore --disable-pip-version-check pymysql >/dev/null && python tools/u8-t5-api-dao.py $phase"
    if ($LASTEXITCODE -ne 0) { throw "u8-t5-api-dao.py $phase failed" }
}
function Run-Pw($script, $phase) {
    docker run --rm --network (Net) --volumes-from cs261-app:ro -v "${PWD}:/w" -v cs261-u8-pw:/deps -e REPO=/w -e TZ=Asia/Bangkok $PW_IMAGE `
        sh -c "cd /deps && ([ -d node_modules/mysql2 ] || npm i --silent --no-audit --no-fund playwright@1.56.1 mysql2@3 >/dev/null) && cp /w/tools/u8-*.mjs /deps/ && node /deps/$script $phase"
    if ($LASTEXITCODE -ne 0) { throw "$script $phase failed" }
}
function Server-Down($script) {
    Write-Host "  docker compose stop app"
    docker compose stop app
    Run-Pw $script down
    Write-Host "  docker compose start app"
    docker compose start app
    Wait-App
    Run-Pw $script up
}

if ($Parts -contains "apidao") {
    Reset-Db
    Write-Host "`n== Integration Test API-DAO (IT-AD-01..18) =="
    Run-Py phase1                 # IT-AD-18: notifications.type -> ENUM (ฐานข้อมูลเดิม)
    docker compose restart app    # LegacySchemaFixer ต้องแปลงกลับเป็น VARCHAR(50)
    Wait-App
    Run-Py phase2
}
if ($Parts -contains "ui") {
    Reset-Db
    Write-Host "`n== Integration Test UI-API (IT-UA-01..17) =="
    Run-Pw u8-t5-ui.mjs main
    Server-Down u8-t5-ui.mjs
}
if ($Parts -contains "st") {
    Reset-Db
    Write-Host "`n== System Test (ST-U8-01..18) =="
    Run-Pw u8-t5-st.mjs main
    Server-Down u8-t5-st.mjs
}
if ($Parts -contains "uat") {
    Reset-Db
    Write-Host "`n== UAT dry-run (UAT-U8-01..12) =="
    Run-Pw u8-t5-uat.mjs main
}
Write-Host "`ndone -> .doc\UserStory\U8\evidence\"
