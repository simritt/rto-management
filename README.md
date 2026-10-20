# RTO Management System

A full-stack **Regional Transport Office (RTO) Management System** designed to digitize and streamline core transport-office workflows such as applications, vehicle and licence management, appointments, payments, violations, appeals, compliance, and audit tracking.

The system is built around a layered **Spring Boot REST backend**, a relational **MySQL 8** database, and a modular domain design using **JPA/Hibernate**. Authentication and authorization are handled using **JWT, BCrypt, and role-based access control (RBAC)**.

---

## 📌 Overview

Traditional RTO workflows involve large amounts of paperwork, repeated verification, manual status tracking, and fragmented records.

This project provides a centralized system where RTO operations can be managed through structured digital workflows.

### Core goals

- Centralize RTO application and citizen/employee records
- Manage vehicles and driving-licence related information
- Track application and appointment lifecycles
- Manage payments and payment records
- Record and process traffic/transport violations
- Support appeals and compliance workflows
- Enforce role-based access to sensitive operations
- Maintain auditability and database integrity
- Provide a scalable REST API architecture for future frontend/mobile clients

---

## ✨ Features

### 🔐 Authentication & Authorization

- JWT-based authentication
- BCrypt password hashing
- Role-Based Access Control (RBAC)
- Protected API endpoints
- Role-aware access to operational modules
- Secure user/session handling

### 📝 Application Management

- Create and manage RTO applications
- Track application status and lifecycle
- Centralized application processing
- Validation and verification workflows
- Application history and audit support

### 🚗 Vehicle Management

- Vehicle registration records
- Vehicle information management
- Vehicle ownership-related records
- Vehicle status tracking
- Database-backed vehicle verification

### 🪪 Licence Management

- Licence record management
- Licence application workflows
- Licence status tracking
- Licence validity/renewal related records
- Verification support

### 📅 Appointment Management

- Appointment creation and tracking
- Appointment status handling
- Structured scheduling information
- Integration with application workflows

### 💳 Payments

- Payment record management
- Fee/payment tracking
- Payment status handling
- Relationship between applications and payments
- Transaction-safe database operations

### ⚠️ Violations

- Record traffic/transport violations
- Track violation details
- Link violations to relevant records
- Support violation status and resolution workflows

### ⚖️ Appeals

- Appeal submission and tracking
- Appeal status management
- Link appeals with relevant applications/violations
- Structured review workflow

### 🔔 Notifications & Audit

- Notification-oriented backend support
- Audit trail for important operations
- Traceability of system changes
- Support for future alert/reminder integrations

### 🛡️ Compliance & Data Integrity

- Database constraints
- Foreign-key relationships
- Validation rules
- Transaction management
- Verification logic
- Indexed columns for frequently accessed data

---

## 🏗️ System Architecture

The backend follows a layered architecture designed to keep business logic, security, persistence, and HTTP handling separated.

```text
Client / Frontend
       │
       ▼
┌─────────────────────────────┐
│          Web / API          │
│     REST Controllers        │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│           Service           │
│      Business Logic         │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│            Core             │
│ Queries / Transactions /    │
│ Locking / Pagination / Audit│
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│          Domain             │
│ JPA Entities & Relationships│
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│          MySQL 8            │
│ Relational Database         │
└─────────────────────────────┘
```

### Backend layers

| Layer | Responsibility |
|---|---|
| `domain` | JPA entities and database relationships |
| `core` | Database queries, transactions, locking, pagination and audit-related infrastructure |
| `security` | Authentication, JWT, BCrypt and RBAC |
| `service` | Application/business rules and transactions |
| `web` | REST controllers and API endpoints |
| `dto` | Request/response models |
| `cli` | Schema/bootstrap and command-line utilities |
| `config` | Application configuration and supporting setup |
| `test` | Integration/database testing |

---

## 🧩 Major Backend Modules

```text
Authentication & RBAC
        │
        ├── Applications
        ├── Vehicles
        ├── Licences
        ├── Appointments
        ├── Payments
        ├── Violations
        ├── Appeals
        ├── Notifications
        └── Audit
```

The modules are designed to work together rather than operate as isolated CRUD components.

For example:

```text
Application
    │
    ├── Appointment
    │
    ├── Licence / Vehicle information
    │
    ├── Payment
    │
    └── Verification / Audit
```

---

## 🗄️ Database Design

The project uses **MySQL 8** with **JPA/Hibernate** for object-relational mapping.

The database is organized around a relational schema with interconnected entities supporting the complete RTO workflow.

### Database design principles

- Normalized relational structure
- Explicit primary and foreign keys
- Referential integrity
- Validation constraints
- Transaction-safe updates
- Indexed query paths
- Auditability
- Modular schema organization

### Advanced database features

| Feature | Purpose |
|---|---|
| Constraints | Preserve data integrity |
| Indexes | Improve query performance |
| Transactions | Keep multi-step operations consistent |
| Triggers | Automate database-side actions where required |
| Procedures | Encapsulate reusable database logic |
| Queries | Provide optimized data access |
| Views | Simplify complex read operations |
| Verification | Validate records and maintain consistency |

---

## 🔄 Database Workflow

A simplified end-to-end workflow is:

```text
Schema
  │
  ▼
Identity
  │
  ▼
RBAC
  │
  ▼
Compliance
  │
  ├── Vehicles
  │      │
  │      ▼
  │   Licences
  │
  └── Applications
          │
          ▼
       Permits
          │
          ▼
      Violations
          │
          ▼
       Payments
```

The modules are connected through database relationships and service-layer workflows.

---

## 🛠️ Technology Stack

### Backend

- **Java 21**
- **Spring Boot 3**
- **Spring Web**
- **Spring Security**
- **Spring Data JPA**
- **Hibernate**
- **Maven**

### Database

- **MySQL 8**
- SQL
- Relational database design
- Database constraints, indexes, transactions and advanced SQL features

### Security

- JWT
- BCrypt
- RBAC
- Protected REST endpoints

### Development

- Git
- GitHub
- IntelliJ IDEA / VS Code
- Postman or any REST client

---

## 📁 Project Structure

```text
rto-management/
├── database/                    # MySQL 8 schema, constraints, views, triggers, procedures, and seed data
│   ├── 00_run_all.sql
│   ├── schema/                  # Core entity tables
│   ├── constraints/             # Foreign keys and check constraints
│   ├── indexes/                 # Performance indexes
│   ├── views/                   # Operational and reporting views
│   ├── triggers/                # Audit & status history triggers
│   ├── procedures/              # Stored procedures
│   ├── queries/                 # Advanced analytical queries
│   └── seed/                    # Realistic reference & demo seed data
├── backend-java/                # Spring Boot 3.3.5 REST API backend (Port 8000)
│   ├── pom.xml
│   └── src/main/java/com/rto/
│       ├── domain/              # JPA entities & relationships
│       ├── core/                # Database abstraction, locking, audit, pagination
│       ├── security/            # JWT authentication, BCrypt, RBAC
│       ├── service/             # Business rules & transactional workflows
│       ├── web/                 # REST controllers (OpenAPI / Swagger at /docs)
│       └── cli/                 # Bootstrap & database setup commands
└── frontend-java/               # Spring Boot 3.3.5 + Thymeleaf + Tailwind CSS portal (Port 8080)
    ├── pom.xml
    └── src/main/
        ├── java/com/rto/web/    # UI controllers & JSON REST client
        └── resources/templates/ # Server-rendered responsive HTML views
```

---

## ⚙️ Prerequisites

Install the following before running the project:

- **JDK 21** (Eclipse Adoptium Temurin or Oracle JDK)
- **Maven 3.9+**
- **MySQL 8.0+**
- Git
- A web browser (for the portal) or REST client (Postman/Curl)

Check your installations:

```bash
java -version
mvn -version
mysql --version
git --version
```

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/simritt/rto-management.git
cd rto-management
```

### 2. Setup the MySQL Database

Create the MySQL database and initialize the schema:

```sql
CREATE DATABASE rto_management;
```

Run the consolidated schema runner to set up all tables, constraints, views, triggers, procedures, and seed data:

```bash
mysql -u root -p rto_management < database/00_run_all.sql
```

### 3. Configure Backend Environment

Copy `.env.example` (or create `.env`) in `backend-java/`:

```properties
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=YOUR_DB_USER
DB_PASSWORD=YOUR_DB_PASSWORD
DB_NAME=rto_management
JWT_SECRET=your_32_character_minimum_random_secret_string_here
PORT=8000
```

Bootstrap initial admin credentials and default reference roles:

```bash
cd backend-java
mvn clean package -DskipTests
java -jar target/rto-management-backend-1.0.0.jar --rto.command=bootstrap
```

### 4. Start the Backend API (Port 8000)

```bash
cd backend-java
java -jar target/rto-management-backend-1.0.0.jar
```

- REST API Base URL: `http://localhost:8000/api/v1`
- Swagger / OpenAPI Interactive UI: `http://localhost:8000/docs`

### 5. Start the Frontend Portal (Port 8080)

In a separate terminal:

```bash
cd frontend-java
mvn clean package -DskipTests
java -jar target/rto-management-frontend-1.0.0.jar
```

- Web Portal URL: `http://localhost:8080`
- Log in with your admin credentials to access the executive dashboard, citizen services, vehicle registrations, licences, challans, payments, legal appeals, staff directory, and RBAC matrix.

---

## 🔑 Configuration

Keep environment-specific values outside source control.

Recommended configuration areas include:

```text
Database URL
Database username
Database password
JWT secret
JWT expiration
Server port
Logging configuration
```

A typical environment setup can be represented as:

```properties
DB_URL=jdbc:mysql://localhost:3306/rto_management
DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET=...
JWT_EXPIRATION=...
```

The exact property names should match the application's configuration files.

---

## 🔒 Security

Security is a key part of the system because RTO operations can contain sensitive personal, vehicle, licence, payment and compliance information.

The backend uses:

### Authentication

JWT-based authentication allows stateless API authentication.

```text
Login
  │
  ▼
Validate credentials
  │
  ▼
Verify BCrypt password
  │
  ▼
Issue JWT
  │
  ▼
Client sends JWT with requests
```

### Authorization

RBAC determines which authenticated users can access specific operations.

```text
User
 │
 ├── Authentication
 │
 └── Role
       │
       ├── Allowed APIs
       └── Restricted APIs
```

### Security practices

- Hash passwords using BCrypt
- Validate and authenticate API requests
- Restrict protected endpoints
- Use role-based authorization
- Keep secrets outside version control
- Validate input at API boundaries
- Use parameterized/JPA-based queries
- Prefer transaction boundaries for sensitive multi-step operations
- Maintain audit information for important changes

---

## 🧪 Testing

The project includes backend/database testing support.

Run the test suite with:

```bash
mvn test
```

For a full verification cycle:

```bash
mvn clean test
```

Testing should cover areas such as:

- Authentication
- Authorization
- Application workflows
- Entity persistence
- Database relationships
- Validation
- Transactional operations
- API integration

---

## 🔌 API

The backend exposes functionality through REST APIs.

Typical API groups include:

```text
/auth
/applications
/vehicles
/licences
/appointments
/payments
/violations
/appeals
/notifications
/audit
```

The exact endpoint paths, request models and response models are defined by the controller classes and DTOs in the project.

A typical REST flow looks like:

```text
Client
  │
  ▼
HTTP Request
  │
  ▼
Controller
  │
  ▼
DTO Validation
  │
  ▼
Service
  │
  ▼
Repository / Query
  │
  ▼
MySQL
  │
  ▼
Response DTO
  │
  ▼
HTTP Response
```

---

## 📊 Data Integrity & Performance

The database layer is designed to support both correctness and performance.

### Integrity

- Foreign-key relationships
- Validation constraints
- Transaction boundaries
- Verification logic
- Consistent status transitions

### Performance

- Indexes on frequently queried fields
- Pagination for large result sets
- Focused database queries
- Efficient entity relationships
- Separation of query and service concerns

---

## 🧭 Development Workflow

A typical contribution flow is:

```text
Create branch
    │
    ▼
Implement feature
    │
    ▼
Run tests
    │
    ▼
Run build
    │
    ▼
Review changes
    │
    ▼
Push branch
    │
    ▼
Create Pull Request
```

Suggested branch naming:

```text
feat/<feature-name>
fix/<bug-name>
refactor/<area-name>
```

Example:

```bash
git checkout -b feat/payment-api
```

---

## 🧑‍💻 Development Commands

### Compile

```bash
mvn compile
```

### Run

```bash
mvn spring-boot:run
```

### Test

```bash
mvn test
```

### Package

```bash
mvn package
```

### Clean build

```bash
mvn clean install
```

---

## 🐞 Troubleshooting

### MySQL connection fails

Check:

```text
1. MySQL service is running
2. Database name is correct
3. Username/password are correct
4. Port is correct
5. JDBC URL is correct
```

### Port already in use

Change the configured server port or stop the process using the current port.

On Windows:

```powershell
netstat -ano | findstr :8080
```

### Authentication returns unauthorized

Check:

```text
1. User exists
2. Password is correct
3. JWT is being generated
4. JWT is being sent in Authorization header
5. Role has permission for the endpoint
```

Typical header:

```text
Authorization: Bearer <JWT_TOKEN>
```

### Database schema is inconsistent

Verify the current schema/migration scripts and database state before changing entity mappings.

---

## 📈 Future Enhancements

Potential extensions include:

- Citizen-facing web portal
- Employee/admin dashboards
- Online document upload and verification
- Automated notifications and reminders
- Email/SMS integration
- Payment-gateway integration
- Advanced reporting and analytics
- Document generation
- Digital approval workflows
- Fine-grained permission management
- Rate limiting and API security hardening
- Centralized application monitoring
- Docker-based deployment
- CI/CD pipeline
- API documentation with OpenAPI/Swagger

---

## 🎯 Project Outcomes

The system is designed to provide:

- **Centralized RTO operations**
- **Better data consistency**
- **Secure access control**
- **Reduced manual processing**
- **Traceable workflows**
- **Structured database management**
- **Scalable REST APIs**
- **Maintainable modular architecture**

---

## 📚 Learning & Technical Focus

This project demonstrates practical implementation of:

- Object-Oriented Programming
- Java and Spring Boot
- REST API development
- Spring Security
- JWT authentication
- BCrypt password hashing
- Role-Based Access Control
- JPA/Hibernate
- MySQL database design
- Relational mapping
- SQL queries
- Transactions
- Indexing
- Database constraints
- Backend validation
- Integration testing
- Git/GitHub collaboration

---

## 👥 Contributors

**Simrit**

Repository:  
https://github.com/simritt/rto-management

---

## 📄 License

This project is intended primarily for academic/educational use unless a separate license is provided in the repository.

If you intend to distribute or reuse the project publicly, add an explicit open-source license such as MIT, Apache-2.0, or GPL-3.0.

---

## ⭐ Acknowledgement

Built as an academic software-engineering project to model and digitize core RTO workflows using a secure, modular Java backend and a relational database architecture.
