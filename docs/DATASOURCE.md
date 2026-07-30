# DataSource

`vc.tbc.bq.jdbc.BQDataSource` is a `javax.sql.DataSource` for BigQuery, configured by JavaBean
setters instead of by a connection URL. Use it wherever a framework wants to be handed a
`DataSource` rather than a URL string: Spring, JPA/Hibernate, JNDI in an application server, or a
connection pool.

```java
import vc.tbc.bq.jdbc.BQDataSource;

BQDataSource ds = new BQDataSource();
ds.setProjectId("my-project");
ds.setDatasetId("my_dataset");
ds.setAuthType("ADC");

try (Connection conn = ds.getConnection();
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT 1")) {
    rs.next();
}
```

## Configuration

Every property in the [connection property reference](CONNECTION_PROPERTIES.md) has a setter of
the same name — `metadataCacheTtl` is `setMetadataCacheTtl`, `useStorageApi` is
`setUseStorageApi`. A URL and the setters configure the same things and mean the same things.

Setters take boxed types, and **`null` means "unset, use the driver's default"**:

```java
ds.setTimeout(600);          // 600-second query timeout
ds.setTimeout(null);         // back to the default
ds.setMetadataCacheEnabled(false);
ds.setQueryPricePerTiB(new BigDecimal("6.25"));
```

`projectId` is required. A property with no typed setter can be set by name:

```java
ds.setProperty("labels", "env=prod,team=data");
```

### Using a URL

A URL can be supplied instead of, or alongside, the setters. Any property set on the bean
overrides the same property in the URL:

```java
BQDataSource ds = new BQDataSource("jdbc:bigquery:my-project/my_dataset?authType=ADC");
ds.setTimeout(600);   // overrides a timeout in the URL, if any
```

Both URL formats are accepted — see [Connection Properties](CONNECTION_PROPERTIES.md#url-formats).

### Validation

Setters never throw. Configuration is validated when `getConnection()` is called, so a
half-configured bean is fine while a container is still populating it. `resolveProperties()`
validates without opening a connection, which is useful for a startup check:

```java
ds.resolveProperties();   // throws SQLException if the configuration is incomplete or invalid
```

## Authentication

Set `authType` and its credential property, exactly as in a URL — see the
[authentication guide](AUTHENTICATION.md).

```java
ds.setAuthType("SERVICE_ACCOUNT");
ds.setCredentials("/path/to/key.json");
```

`getConnection(user, password)` **does not authenticate**. BigQuery authenticates by credential,
not by a user name and password pair, so supplying either argument throws
`SQLFeatureNotSupportedException` (SQLState `0A000`) rather than reinterpreting it as some
credential. Calling it with `null` or blank arguments — which pools and application servers do
routinely when no credentials are configured — is the same as calling `getConnection()`.

`setLoginTimeout(seconds)` is the same setting as `setConnectionTimeout(seconds)`, the timeout for
establishing the HTTP connection. `setLoginTimeout(0)` clears it, so the driver's default applies.

## Spring

Declare the data source as a bean and bind properties to it. Spring's relaxed binding maps
`project-id` to `setProjectId`:

```java
@Bean
@ConfigurationProperties("bigquery")
public BQDataSource bigQueryDataSource() {
    return new BQDataSource();
}
```

```yaml
bigquery:
  project-id: my-project
  dataset-id: my_dataset
  auth-type: ADC
  metadata-cache-ttl: 600
```

## Connection pooling

`BQDataSource` opens a new connection per `getConnection()` call; it is not a pool. There is no
`ConnectionPoolDataSource` or `PooledConnection` — pool with HikariCP, Tomcat JDBC, DBCP or C3P0,
all of which pool `java.sql.Connection` directly.

```java
HikariConfig config = new HikariConfig();
config.setDataSource(bigQueryDataSource);
config.setMaximumPoolSize(10);

HikariDataSource pooled = new HikariDataSource(config);
```

The driver defers `BEGIN TRANSACTION` to the first statement, so a pool toggling auto-commit
between checkouts costs no BigQuery jobs. See
[Connection pools](COMPATIBILITY.md#connection-pools) for sizing and validation guidance.

## JNDI

`BQDataSource` is `Serializable` and `Referenceable`, so an application server can bind one into
its naming directory. `vc.tbc.bq.jdbc.BQDataSourceFactory` rebuilds it on lookup.

A container that declares the resource rather than binding a live bean names the factory
directly, and passes connection properties as attributes. In Tomcat's `context.xml`:

```xml
<Resource name="jdbc/bigquery"
          auth="Container"
          type="javax.sql.DataSource"
          factory="vc.tbc.bq.jdbc.BQDataSourceFactory"
          projectId="my-project"
          datasetId="my_dataset"
          authType="ADC"
          metadataCacheTtl="600"/>
```

```java
DataSource ds = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/bigquery");
```

A bound reference carries every property as a string, credentials included. Bind a data source
only into a directory as protected as the credentials themselves; `toString()` redacts them, a
JNDI reference does not.

## Logging

The driver logs through SLF4J. `setLogWriter(PrintWriter)` stores the writer and reads it back,
but the driver never writes to it — see the [logging guide](LOGGING.md) for how to route driver
output.

## See also

- [Connection Properties](CONNECTION_PROPERTIES.md) — every property a setter can set
- [Authentication](AUTHENTICATION.md) — the auth types and their credential properties
- [Compatibility](COMPATIBILITY.md) — connection pools, BI tools, JDBC feature support
