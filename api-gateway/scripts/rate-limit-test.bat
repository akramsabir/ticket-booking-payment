@echo off
rem Sends 12 login attempts in a row. Limit for /api/auth/** is 10 per minute per IP.
rem Expected: ten times the answer of user-service (400/401 for these fake credentials), then 429 429.
for /L %%i in (1,1,12) do (
  curl -s -o NUL -w "%%{http_code} " -X POST http://localhost:8090/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"
)
echo.
