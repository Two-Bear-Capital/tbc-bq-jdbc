# Quick Start Guide

Get started with tbc-bq-jdbc in 5 minutes.

## Prerequisites

- Java 21 or later
- Google Cloud project with BigQuery enabled
- One of:
  - Application Default Credentials configured
  - Service account JSON key file
  - OAuth credentials

## Installation

### Download a JAR (GitHub Releases)

The shaded JAR includes every dependency, so it works as a standalone driver:

```bash
wget https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases/latest/download/tbc-bq-jdbc-2.4.3-shaded.jar
```

For IntelliJ IDEA, DBeaver or DataGrip, use the `-with-logging` variant instead — it adds
a preconfigured Logback. See [Logging](LOGGING.md#jar-variants).

The shaded JARs are ~41 MB, mostly platform-specific native libraries for gRPC SSL/TLS.

### Maven / Gradle

> **Not yet published to Maven Central.** These coordinates are reserved for the first
> Central release; until then use a GitHub Releases JAR, or run `./mvnw clean install`
> to publish to your local repository.

```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>2.4.3</version>
</dependency>
```

```groovy
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:2.4.3'
}
```

## Basic Usage

### 1. Application Default Credentials (Recommended)

```java
import java.sql.*;

public class QuickStart {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, count FROM my_table LIMIT 10")) {

            while (rs.next()) {
                String name = rs.getString("name");
                long count = rs.getLong("count");
                System.out.printf("%s: %d%n", name, count);
            }
        }
    }
}
```

### 2. Service Account (JSON Key)

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=SERVICE_ACCOUNT&" +
             "credentials=/path/to/service-account-key.json";

try (Connection conn = DriverManager.getConnection(url)) {
    // Execute queries...
}
```

### 3. Using PreparedStatement

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url);
     PreparedStatement pstmt = conn.prepareStatement(
         "SELECT * FROM users WHERE age > ? AND active = ?")) {

    pstmt.setInt(1, 18);
    pstmt.setBoolean(2, true);

    try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
            System.out.println(rs.getString("name"));
        }
    }
}
```

## URL Formats

### Traditional Format

```
jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2
```

**Required:**
- `project` - Google Cloud project ID

**Optional:**
- `authType` - Authentication method (`ADC`, `SERVICE_ACCOUNT`, `USER_OAUTH`,
  `WORKFORCE`, `WORKLOAD`). Defaults to `ADC`; the value is case-insensitive
- `dataset` - Default dataset (can be omitted if queries specify dataset)
- `credentials` - Path to service account JSON key (for SERVICE_ACCOUNT auth)
- `timeout` - Query timeout in seconds (default: 300)
- `location` - BigQuery location (e.g., US, EU)
- `enableSessions` - Enable BigQuery sessions for temp tables (default: false)

See [Connection Properties](CONNECTION_PROPERTIES.md) for full list.

### Simba Format (Migration Support)

tbc-bq-jdbc also supports Simba BigQuery JDBC driver URLs:

```
jdbc:bigquery://[Host]:[Port];ProjectId=[Project];OAuthType=[AuthValue];[Property1]=[Value1];...
```

**Examples:**

```java
// Application Default Credentials
String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3";

// Service Account
String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=0;OAuthPvtKeyPath=/path/to/key.json";

// With additional properties
String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3;Timeout=120;Location=EU";
```

**OAuthType Values:**
- `0` = Service Account (requires `OAuthPvtKeyPath`)
- `1` = User OAuth (requires `OAuthClientId`, `OAuthClientSecret`, `OAuthRefreshToken`)
- `3` = Application Default Credentials (recommended)
- `4` = External Account → Workload/Workforce Identity (set `credentialConfigFile`)

`OAuthType=2` (pre-generated access tokens) is not supported and is rejected with an error.

Migrating from Simba is usually a matter of swapping the JAR and setting the driver class
to `vc.tbc.bq.jdbc.BQDriver`. Property names Simba defines are translated; anything else
in the URL is accepted and ignored rather than rejected.

See [Connection Properties - Simba Format](CONNECTION_PROPERTIES.md#simba-bigquery-driver-format) for complete property mapping.

## Common Examples

### Execute DML (INSERT, UPDATE, DELETE)

```java
try (Connection conn = DriverManager.getConnection(url);
     Statement stmt = conn.createStatement()) {

    int rowsAffected = stmt.executeUpdate(
        "UPDATE my_table SET status = 'active' WHERE id = 123");

    System.out.println("Rows updated: " + rowsAffected);
}
```

### Get Table Metadata

```java
try (Connection conn = DriverManager.getConnection(url)) {
    DatabaseMetaData meta = conn.getMetaData();

    // List all tables in dataset
    try (ResultSet rs = meta.getTables(null, "my_dataset", "%", new String[]{"TABLE"})) {
        while (rs.next()) {
            System.out.println("Table: " + rs.getString("TABLE_NAME"));
        }
    }

    // List columns for a specific table
    try (ResultSet rs = meta.getColumns(null, "my_dataset", "my_table", "%")) {
        while (rs.next()) {
            String colName = rs.getString("COLUMN_NAME");
            String colType = rs.getString("TYPE_NAME");
            System.out.printf("%s (%s)%n", colName, colType);
        }
    }
}
```

### Using Sessions for Temp Tables

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=ADC&enableSessions=true";

try (Connection conn = DriverManager.getConnection(url);
     Statement stmt = conn.createStatement()) {

    // Create temporary table
    stmt.execute("CREATE TEMP TABLE temp_data AS SELECT 1 as id, 'test' as name");

    // Query temporary table
    try (ResultSet rs = stmt.executeQuery("SELECT * FROM temp_data")) {
        while (rs.next()) {
            System.out.println(rs.getString("name"));
        }
    }
}
```

### Using Transactions

`setAutoCommit(false)` starts a BigQuery session on demand, so no extra connection
property is needed (add `enableSessions=true` to create the session at connect time).

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url)) {
    conn.setAutoCommit(false); // Starts a session; transaction begins with the first statement

    try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("INSERT INTO accounts (id, balance) VALUES (1, 100)");
        stmt.executeUpdate("INSERT INTO accounts (id, balance) VALUES (2, 200)");

        conn.commit(); // Commit transaction
    } catch (SQLException e) {
        conn.rollback(); // Rollback on error
        throw e;
    }
}
```

## Connection Pooling

For production applications, use a connection pool such as HikariCP. See
[Compatibility → Connection pools](COMPATIBILITY.md#connection-pools) for a worked example and
recommended settings.

## Environment Variables

Set credentials via environment:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
```

Google's client library reads that variable itself, so the URL just selects ADC:

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
```

## Troubleshooting

### "No suitable driver found"

Make sure the JAR is on your classpath. The driver auto-registers via ServiceLoader.

### "Authentication failed"

Verify your credentials:
- For ADC: Run `gcloud auth application-default login`
- For service account: Check the JSON key file path

### "Query timeout exceeded"

Increase timeout in the URL:
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=600
```

See [Connection Properties](CONNECTION_PROPERTIES.md) for all configuration options.

## Next Steps

- [Authentication Guide](AUTHENTICATION.md) - All authentication methods
- [Connection Properties](CONNECTION_PROPERTIES.md) - Full configuration reference and performance tuning
- [Type Mapping](TYPE_MAPPING.md) - BigQuery to JDBC type conversions
- [Logging](LOGGING.md) - Driver logging setup and JAR variants
- [Compatibility](COMPATIBILITY.md) - JDBC features and limitations

## Example Projects

Runnable examples live in the driver's own test suite — see
[`src/test/java/vc/tbc/bq/jdbc/integration/real/`](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/tree/main/src/test/java/vc/tbc/bq/jdbc/integration/real)
for connections, queries, transactions and metadata usage against real BigQuery.
