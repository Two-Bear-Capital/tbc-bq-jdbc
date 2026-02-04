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

### Maven

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.twobearcapital</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.4</version>
</dependency>
```

### Gradle

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'com.twobearcapital:tbc-bq-jdbc:1.0.4'
}
```

### Fat JAR (Standalone)

Download the shaded JAR that includes all dependencies:

```bash
# Download from Maven Central or GitHub Releases
wget https://repo1.maven.org/maven2/com/twobearcapital/tbc-bq-jdbc/1.0.4/tbc-bq-jdbc-1.0.4.jar
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
- `authType` - Authentication method (ADC, SERVICE_ACCOUNT, USER_OAUTH, etc.)

**Optional:**
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

If you're migrating from Simba driver, simply replace the driver JAR - your existing connection strings will work without modification.

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

### Using Transactions (with Sessions)

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=ADC&enableSessions=true";

try (Connection conn = DriverManager.getConnection(url)) {
    conn.setAutoCommit(false); // Begin transaction

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

For production applications, use a connection pool:

### HikariCP Example

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>1.0.4</version>
</dependency>
```

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:bigquery:my-project/my_dataset?authType=ADC");
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setConnectionTimeout(30000);

HikariDataSource dataSource = new HikariDataSource(config);

// Use the pool
try (Connection conn = dataSource.getConnection()) {
    // Execute queries...
}
```

## Environment Variables

Set credentials via environment:

```bash
# Application Default Credentials
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json

# Then use ADC authentication
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
- [Connection Properties](CONNECTION_PROPERTIES.md) - Full configuration reference
- [Type Mapping](TYPE_MAPPING.md) - BigQuery to JDBC type conversions
- [Connection Properties](CONNECTION_PROPERTIES.md#performance-tuning) - Performance optimization
- [Compatibility Matrix](COMPATIBILITY.md) - JDBC features and limitations

## Example Projects

Complete example projects available at:
- https://github.com/Two-Bear-Capital/tbc-bq-jdbc-examples
