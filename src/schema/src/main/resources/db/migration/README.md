# Migrations

Flyway migrations, applied by the `migrator` module. Naming: `V<n>__<snake_case>.sql`, one
logical change per file, never edited after it has been applied anywhere.

The service itself runs with `spring.flyway.enabled: false` and `ddl-auto: validate`, so a
missing migration shows up as a startup failure rather than as a silent schema drift.

Spring Modulith's event publication registry needs its own table once the first event is
published; its DDL ships inside `spring-modulith-events-jdbc`
(`org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql`) — copy it into a
migration instead of letting the library create the table.
