# tbc-bq-jdbc

Modern JDBC driver for Google BigQuery (Java 21+, JDBC 4.3)

## Quick Start

### Maven

```xml
<dependency>
    <groupId>com.twobearcapital</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Code Example

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url);
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT name, count FROM my_table")) {

    while (rs.next()) {
        System.out.println(rs.getString("name") + ": " + rs.getLong("count"));
    }
}
```

## Features

- **Java 21+** with virtual thread support
- **JDBC 4.3** compliance
- **BigQuery Storage Read API** for large results
- **Multiple authentication types**: Service Account, ADC, OAuth
- **Sessions and multi-statement support**
- **Comprehensive type mapping** for all BigQuery types

## Documentation

- [Quick Start](docs/QUICKSTART.md) *(coming soon)*
- [Authentication Guide](docs/AUTHENTICATION.md) *(coming soon)*
- [Connection Properties](docs/CONNECTION_PROPERTIES.md) *(coming soon)*
- [Performance Tuning](docs/PERFORMANCE.md) *(coming soon)*

## Building from Source

```bash
mvn clean install
```

## Requirements

- Java 21 or later
- Maven 3.9+

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details

## Project Status

🚧 **Under Active Development** - Version 1.0.0 coming soon
