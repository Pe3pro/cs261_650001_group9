# U8:T5 Integration Test API-DAO (IT-AD-01..18) against the real docker compose stack.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File .doc\UserStory\U8\test-run\run-it-ad.ps1
# Result: .doc\UserStory\U8\test-run\it-ad-result.json  (the stack is reset again at the end for the UI tests)
$ErrorActionPreference = "Stop"
$env:APP_SEED_U8_DATA = "true"

function Wait-App {
    Write-Host "waiting for app (U8 seed) ..."
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 http://localhost:8080/api/users/6609650031
            if ($r.StatusCode -eq 200) { Write-Host "app ready"; return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "app did not start"
}

function Run-Py($phase) {
    docker run --rm -v "${PWD}:/w" --volumes-from cs261-app:ro -w /w python:3.12-slim `
        sh -c "pip install -q --root-user-action=ignore pymysql >/dev/null && python .doc/UserStory/U8/test-run/it_ad.py $phase"
    if ($LASTEXITCODE -ne 0) { throw "it_ad.py $phase failed" }
}

docker compose down -v
docker compose up -d --build
Wait-App
Run-Py phase1                # IT-AD-18: notifications.type -> ENUM (old schema)
docker compose restart app   # LegacySchemaFixer must convert it back to VARCHAR(50)
Wait-App
Run-Py phase2                # IT-AD-01 .. IT-AD-18

Write-Host "`nreset database for the UI / System tests ..."
docker compose down -v
docker compose up -d --build
Wait-App
Write-Host "done"
