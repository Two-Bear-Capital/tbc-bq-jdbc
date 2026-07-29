# Logging Configuration

The BigQuery JDBC Driver uses [SLF4J](https://www.slf4j.org/) as its logging facade. This document explains the available JAR variants and how to configure logging.

## JAR Variants

The driver is distributed in three variants:

### 1. Standard JAR (`tbc-bq-jdbc-3.0.6.jar`)
- **Use case:** When included as a Maven/Gradle dependency
- **Logging:** Requires you to provide your own SLF4J implementation
- **Size:** Smallest (runtime dependencies not included)


### 2. Shaded JAR (`tbc-bq-jdbc-3.0.6-shaded.jar`)
- **Use case:** Standalone usage with all dependencies bundled
- **Logging:** Requires you to provide your own SLF4J implementation
- **Size:** ~41 MB (includes Google Cloud libraries)
- **Dependencies:** All dependencies relocated to avoid conflicts

### 3. Shaded JAR with Logging (`tbc-bq-jdbc-3.0.6-with-logging.jar`)
- **Use case:** IntelliJ IDEA, DBeaver, and other database tools/IDEs
- **Logging:** Includes Logback with sensible defaults
- **Size:** ~42 MB (includes everything + Logback)
- **Dependencies:** All dependencies including Logback relocated
- **Default behavior:** Logs to `~/.bigquery-jdbc/logs/bigquery-jdbc.log` with daily rotation

## Choosing the Right Variant

| Scenario | Recommended Variant |
|----------|-------------------|
| Maven/Gradle project | Standard JAR |
| IntelliJ IDEA Database Tool | **with-logging** variant |
| DBeaver, DataGrip, etc. | **with-logging** variant |
| Standalone application | **shaded** variant + your logging config |
| Docker container | **shaded** variant + your logging config |

## Configuration for IntelliJ IDEA

When using the `with-logging` variant in IntelliJ:

1. **Add the driver** to IntelliJ's database configuration:
   - File → Project Structure → Libraries
   - Or use the Database tool window driver configuration
   - Select the `tbc-bq-jdbc-3.0.6-with-logging.jar` file

2. **Default logging behavior:**
   - Driver logs are written to `~/.bigquery-jdbc/logs/bigquery-jdbc.log` (your home directory) — created automatically on first connection
   - Log level: DEBUG for driver code, WARN for the bundled Google Cloud libraries
   - Daily rotation with 30-day retention
   - Maximum total size: 500MB

3. **Customize logging** (optional):
   - Create a `logback.xml` file in your IntelliJ project's resources
   - Your configuration will override the driver's default

### Example Custom Configuration for IntelliJ

Place a `logback.xml` on IntelliJ's classpath to override the default.

> **Important:** In the `with-logging` variant, Logback is **relocated** to the
> `vc.tbc.bq.jdbc.shaded.logback` package, so the appender `class` names must use that prefix
> (as shown below). When you add your own (non-relocated) Logback to the standard JAR in a
> Maven/Gradle project, use the normal `ch.qos.logback.*` class names instead.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Custom file location (relocated classes for the with-logging variant) -->
    <appender name="FILE" class="vc.tbc.bq.jdbc.shaded.logback.core.rolling.RollingFileAppender">
        <file>/tmp/my-custom-location.log</file>
        <rollingPolicy class="vc.tbc.bq.jdbc.shaded.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/tmp/my-custom-location.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Less verbose: only INFO level -->
    <logger name="vc.tbc.bq.jdbc" level="INFO">
        <appender-ref ref="FILE"/>
    </logger>

    <root level="WARN">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

## Configuration for Maven/Gradle Projects

When using the standard JAR, add an SLF4J implementation:

### Maven
```xml
<dependencies>
    <!-- BigQuery JDBC Driver -->
    <dependency>
        <groupId>vc.tbc</groupId>
        <artifactId>tbc-bq-jdbc</artifactId>
        <version>3.0.6</version>
    </dependency>

    <!-- Add your preferred logging implementation -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.6.1</version>
    </dependency>
</dependencies>
```

### Gradle
```groovy
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:3.0.6'
    implementation 'ch.qos.logback:logback-classic:1.6.1'
}
```

Then create your own `logback.xml` in `src/main/resources/`.

## Log Levels and Messages

The driver logs at various levels:

### DEBUG Level
- Connection opened and closed
- Query job creation and SQL execution
- Metadata lookups and cache operations
- Result set closed

### INFO Level
- Driver registration
- Session lifecycle (created, closing, started on demand)
- Query cancellation
- Use of a custom BigQuery endpoint

### WARN Level
- Query timeouts and failed job cancellation
- Metadata cache issues and `INFORMATION_SCHEMA` query failures
- Storage Read API unavailable, falling back to the standard path
- Statement cleanup failures during connection close

### ERROR Level
- Driver registration failures
- Connection creation failures
- Driver version could not be loaded

Failed queries surface as `SQLException` to the caller; the driver does not log them.

## Disabling Logging

If you don't want any driver logs:

### Option 1: Use slf4j-nop
```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-nop</artifactId>
    <version>2.0.18</version>
</dependency>
```

### Option 2: Set log level to OFF
In `logback.xml`:
```xml
<logger name="vc.tbc.bq.jdbc" level="OFF"/>
```

## Suppressing IntelliJ Warnings

If you see warnings from IntelliJ's own BigQuery dialect (e.g., "Could not get connection"), these are from IntelliJ's code, not the driver.

To suppress them:
1. **Help → Diagnostic Tools → Debug Log Settings**
2. Add: `#com.intellij.database.remote.jdba.jdbc.dialects.BigQueryIntermediateFacade:error`
3. Restart IntelliJ

## Troubleshooting

### Logs not appearing?
1. Check the default location `~/.bigquery-jdbc/logs/bigquery-jdbc.log` (or your custom path)
2. Verify write permissions for the log file location
3. Check if another logging configuration is taking precedence

### Too much logging?
Change the log level from DEBUG to INFO or WARN:
```xml
<logger name="vc.tbc.bq.jdbc" level="INFO"/>
```

### Want to see Google Cloud API calls?
Increase the log level for Google Cloud libraries:
```xml
<logger name="com.google.cloud.bigquery" level="DEBUG"/>
```

This works with the **standard JAR**, where the Google libraries keep their own package
names. In the **shaded** and **with-logging** JARs `com.google` is relocated under
`vc.tbc.bq.jdbc.shaded.google`, so use that prefix instead:

```xml
<logger name="vc.tbc.bq.jdbc.shaded.google" level="DEBUG"/>
```

## See Also

- [IntelliJ IDEA Integration](INTELLIJ.md#logging-in-intellij) - Using the with-logging JAR in IntelliJ
- [SLF4J Documentation](https://www.slf4j.org/manual.html)
- [Logback Configuration](https://logback.qos.ch/manual/configuration.html)
- [IntelliJ Database Tools](https://www.jetbrains.com/help/idea/relational-databases.html)
