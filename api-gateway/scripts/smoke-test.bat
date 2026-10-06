@echo off
rem Quick check of the gateway rules. Needs: gateway running on 8090. Item 5 also needs ticket-hub (event service) running.
set GW=http://localhost:8090

echo 1) gateway health                         (expect 200)
curl -s -o NUL -w "   -> %%{http_code}\n" %GW%/actuator/health

echo 2) bookings without a token              (expect 401)
curl -s -o NUL -w "   -> %%{http_code}\n" %GW%/bookings

echo 3) admin area without a token            (expect 401)
curl -s -o NUL -w "   -> %%{http_code}\n" %GW%/api/admin/users

echo 4) payment-service internal API          (expect 404 - not exposed)
curl -s -o NUL -w "   -> %%{http_code}\n" %GW%/api/v1/payments/1

echo 5) list events (public)                  (expect 200 when ticket-hub is running, 503 if not)
curl -s -o NUL -w "   -> %%{http_code}\n" %GW%/api/events

echo 6) create an event without a token       (expect 401)
curl -s -o NUL -w "   -> %%{http_code}\n" -X POST %GW%/api/events -H "Content-Type: application/json" -d "{}"

echo 7) forged token                           (expect 401)
curl -s -o NUL -w "   -> %%{http_code}\n" %GW%/bookings -H "Authorization: Bearer abc.def.ghi"
