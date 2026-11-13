# U8:T5 System Test: DB part (ST-U8-03 COMPLETED case + DB dump). Run from the repo root after the browser steps.
$ErrorActionPreference = "Stop"
docker run --rm -v "${PWD}:/w" --volumes-from cs261-app:ro -w /w python:3.12-slim `
    sh -c "pip install -q --root-user-action=ignore pymysql >/dev/null && python .doc/UserStory/U8/test-run/st_db.py"
