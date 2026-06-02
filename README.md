# Verhåårm Backend

Backend for **Verhåårm**, a custom administration app built for one specific club.

## Project Context

This repository is mainly meant as a portfolio project. Verhåårm was developed as a custom internal tool, so the business logic is intentionally specific. 

## What I Built

This backend provides the API for managing users, roles, periods, fines,
attendance, tasks, suggestions, live events, file uploads, exports, and push
notifications.

The focus of the project was building a real-world backend with authentication,
authorization, auditability, and mobile/web app integration.

## Technical Highlights

- **Java / Spring Boot REST API**
- **PostgreSQL** database with **Flyway** migrations
- **Spring Security** with JWT-based authentication
- Access and refresh token flow with server-side session tracking
- Role-based access control for different internal responsibilities
- Structured service/controller/repository architecture
- OpenAPI / Swagger documentation via springdoc-openapi
- Push notifications for Android and web/PWA clients
  - Firebase Cloud Messaging
  - Web Push / VAPID
- File upload handling for fine photos
- CSV export functionality
- Audit logging for important actions
- Deployment on a VPS behind nginx with systemd

## License

This project is licensed under the **GNU Affero General Public License v3.0
only**.

Copyright (c) 2026 Valentin Schecklein.

See the `LICENSE` file for the full license text.
