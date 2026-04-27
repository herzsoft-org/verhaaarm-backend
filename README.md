# Verhåårm Backend

Spring Boot REST API for Verhåårm (internal administration: fines, convent periods, events, attendance, exports).

## Tech
- Java (Spring Boot)
- PostgreSQL
- JWT auth (access + refresh)
- OpenAPI / Swagger UI (springdoc-openapi)
- Migrations: Flyway

## Local Development

### Prerequisites
- Java (matching the project’s toolchain)
- PostgreSQL
- (Optional) Docker for local Postgres, if you use it locally

### Configure
Create `application-local.yml` (not committed) or use env vars.

Example env vars:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/verhaaarm`
- `SPRING_DATASOURCE_USERNAME=...`
- `SPRING_DATASOURCE_PASSWORD=...`

### Run
- Run from IDE, or:
- `./mvnw spring-boot:run` (Maven)  
  or `./gradlew bootRun` (Gradle)

### Swagger / OpenAPI
After starting:
- Swagger UI: `/swagger-ui.html` (or the springdoc path you configure)

## Deployment (VPS)
See `verhaaarm-infra` repo:
- systemd service unit
- nginx reverse proxy config
- backup scripts

## Project Status
MVP target: Users/Roles → Periods → Fines → CSV exports → Events/Attendance → Suggestions/Live events.

## License

This project is proprietary and closed source.

Copyright (c) 2026 Valentin Schecklein. All rights reserved.

No permission is granted to use, copy, modify, distribute, host, deploy, or
otherwise exploit this software without prior written permission.
