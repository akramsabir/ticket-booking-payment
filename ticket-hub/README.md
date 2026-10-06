# TicketHub

TicketHub is an event and ticket booking platform designed to manage events, venues, halls, seats, and ticket bookings.

The platform is designed to support different types of events such as movies, concerts, sports, and other ticketed events.

## Tech Stack

* Java 17
* Spring Boot 3.3
* PostgreSQL
* Spring Data JPA
* Flyway

## Core Modules

* Event Management
* Venue Management
* Hall Management
* Seat Management
* Ticket Booking
* User Management
* Payment Management

## Architecture

TicketHub follows a modular microservices-based architecture where different business capabilities can be developed and maintained as independent services.

## Event Management

The Event Service manages general event information such as:

* Event name
* Description
* Event type
* Language
* Duration
* Start and end dates
* Status
* Event metadata

## Venue Management

Venues contain the location information required for hosting events.

A venue can contain multiple halls, and each hall can have its own seating configuration.

## Hall & Seat Management

Each hall can have:

* Total seats
* Seat layout
* Seat types
* Seat positions
* Seat availability

Seat types can include categories such as:

* Regular
* Premium
* VIP

## Booking

The booking module manages the process of selecting available seats and creating ticket bookings for events.

## Database

PostgreSQL is used as the primary database.

Flyway is used to manage database schema changes and migrations.

Never modify an already-applied migration. Create a new migration file for every schema change.

Example:

```text
V2__add_event_metadata.sql
```

## Running the Project

Start the required services:

```bash
docker compose up -d
```

Run the application:

```bash
mvn spring-boot:run
```

Run tests:

```bash
mvn test
```

## Configuration

Database configuration can be provided through environment variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

Default configuration is intended for local development.

## Project Goal

The goal of TicketHub is to provide a scalable platform for managing different types of events and their complete ticket-booking lifecycle.
