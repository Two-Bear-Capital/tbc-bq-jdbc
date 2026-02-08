# IntelliJ IDEA Setup Guide

This guide explains how to use the BigQuery JDBC Driver with IntelliJ IDEA's Database Tools.

## Quick Start

1. **Download the right JAR:**
   - Use `tbc-bq-jdbc-1.0.31-with-logging.jar` from the releases
   - This variant includes built-in logging support

2. **Add to IntelliJ:**
   - Open **Database** tool window
   - Click **+** → **Data Source** → **BigQuery** (or **Other**)
   - Under **Drivers**, add a new driver or edit the BigQuery driver
   - Add the `with-logging.jar` file

3. **Configure connection:**
   ```
   URL: jdbc:bigquery:YOUR-PROJECT-ID?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json
   ```

## Why the "with-logging" Variant?

IntelliJ runs JDBC drivers in a separate process and needs a complete logging implementation. The `with-logging` variant includes:

- **Logback** logging implementation (relocated to avoid conflicts)
- **Default logging configuration** that works out-of-the-box
- **No additional setup required**

### What You Get

By default, driver logs are written to a predictable location in your home directory:

**macOS/Linux:**
```
~/.bigquery-jdbc/logs/bigquery-jdbc.log
```

**Windows:**
```
C:\Users\YourUsername\.bigquery-jdbc\logs\bigquery-jdbc.log
```

With the following behavior:
- **Log Level:** DEBUG for driver code, WARN for Google Cloud APIs
- **Rotation:** Daily rotation with 30-day retention
- **Size Limit:** 500MB total size cap
- **Auto-created:** The directory will be created automatically on first connection

## Customizing Logs

### Option 1: Change Log Location

To write logs to a different location, create a custom `logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <!-- Change this path -->
        <file>/Users/you/logs/bigquery.log</file>

        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/Users/you/logs/bigquery.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>

        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.tbc.bq.jdbc" level="DEBUG">
        <appender-ref ref="FILE"/>
    </logger>

    <root level="WARN">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

Place this file on IntelliJ's classpath to override the default configuration.

### Option 2: Change Log Level

To reduce verbosity, change the log level from DEBUG to INFO or WARN:

```xml
<!-- Less verbose -->
<logger name="com.tbc.bq.jdbc" level="INFO">
    <appender-ref ref="FILE"/>
</logger>
```

### Option 3: Add Console Logging

To also see logs in IntelliJ's console:

```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>

<logger name="com.tbc.bq.jdbc" level="DEBUG">
    <appender-ref ref="FILE"/>
    <appender-ref ref="CONSOLE"/>
</logger>
```

## Suppressing IntelliJ Warnings

You may see warnings from IntelliJ's built-in BigQuery dialect:

```
WARNING: Could not get connection
```

These are **harmless** and come from IntelliJ's code (not the driver). To suppress them:

1. **Help → Diagnostic Tools → Debug Log Settings**
2. Add this line:
   ```
   #com.intellij.database.remote.jdba.jdbc.dialects.BigQueryIntermediateFacade:error
   ```
3. Click **OK** and restart IntelliJ

## Troubleshooting

### Logs not appearing?

1. **Check the default location:**
   - On macOS: `~/.bigquery-jdbc/logs/bigquery-jdbc.log`
   - On Linux: `~/.bigquery-jdbc/logs/bigquery-jdbc.log`
   - On Windows: `C:\Users\YourUsername\.bigquery-jdbc\logs\bigquery-jdbc.log`

2. **Quick way to find them:**
   ```bash
   # macOS/Linux
   open ~/.bigquery-jdbc/logs/

   # Or view the log directly
   tail -f ~/.bigquery-jdbc/logs/bigquery-jdbc.log
   ```

3. **Still not appearing?** Try an absolute path in your custom `logback.xml`:
   ```xml
   <file>/tmp/bigquery-jdbc.log</file>
   ```

### Too much logging?

Change the log level to INFO or WARN (see Option 2 above).

### Connection issues?

Check the logs for detailed error messages:
- Connection failures will be logged at ERROR level
- Authentication issues will show credential problems
- Query execution details are at DEBUG level

## Which JAR Variant Should I Use?

| Use Case | Recommended JAR |
|----------|----------------|
| **IntelliJ Database Tools** | `with-logging.jar` |
| **Maven/Gradle Project** | Standard JAR (add your own logging) |
| **DBeaver, DataGrip, etc.** | `with-logging.jar` |
| **Standalone Application** | `shaded.jar` + your logging config |

## More Information

- [Full Logging Documentation](LOGGING.md)
- [Connection Properties](CONNECTION_PROPERTIES.md)
- [Authentication Guide](AUTHENTICATION.md)

## Support

If you encounter issues:
1. Check the logs at `logs/bigquery-jdbc.log`
2. Increase log level to DEBUG if needed
3. Open an issue on GitHub with relevant log excerpts
