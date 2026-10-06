# service-registry (Eureka server)

The phone book of the platform. Each service says "I am booking-service and I live at localhost:8083" when it starts;
the gateway asks "where is booking-service?" for every request. Services can move or start on another port and nobody needs reconfiguring.

* Run: `./mvnw spring-boot:run` (or run `ServiceRegistryApplication` in IntelliJ)
* Dashboard: http://localhost:8761  ·  health: http://localhost:8761/actuator/health
* Port: `SERVER_PORT` (default 8761 – booking-service already expects it there)

## Reading the dashboard
"Instances currently registered with Eureka" lists `APPLICATION | AMIs | Availability Zones | Status`.
`UP (1) - booking-service:8083` is a running service. A stopped service disappears after about 15-20 seconds.

You will see a red banner **"THE SELF PRESERVATION MODE IS TURNED OFF"**. That is intentional for development (see the comment in `application.yml`):
with it ON, Eureka keeps showing services that were stopped. For production set `EUREKA_SELF_PRESERVATION=true`.

## Notes
* Standalone: one registry that does not register with itself. A second registry for high availability needs peer settings (not needed now).
* The registry has no login. Anyone who can reach port 8761 can see and change registrations, so on a real network keep it private
  (firewall / internal network only) or add Spring Security with basic auth and put the credentials in each service's `defaultZone`.
