# Type Mapping Reference

Complete mapping between BigQuery data types and JDBC types.

## Overview

This driver maps BigQuery types to standard JDBC types following the JDBC 4.3 specification.

## Type Mapping Table

| BigQuery Type | JDBC Type | Java Type | ResultSet Method | PreparedStatement Method |
|---------------|-----------|-----------|------------------|--------------------------|
| `STRING` | `VARCHAR` | `String` | `getString()` | `setString()` |
| `BYTES` | `BINARY` | `byte[]` | `getBytes()` | `setBytes()` |
| `INT64` | `BIGINT` | `long` | `getLong()` | `setLong()` |
| `FLOAT64` | `DOUBLE` | `double` | `getDouble()` | `setDouble()` |
| `NUMERIC` | `NUMERIC` | `BigDecimal` | `getBigDecimal()` | `setBigDecimal()` |
| `BIGNUMERIC` | `NUMERIC` | `BigDecimal` | `getBigDecimal()` | `setBigDecimal()` |
| `BOOL` | `BOOLEAN` | `boolean` | `getBoolean()` | `setBoolean()` |
| `TIMESTAMP` | `TIMESTAMP` | `Timestamp` | `getTimestamp()` | `setTimestamp()` |
| `DATE` | `DATE` | `Date` | `getDate()` | `setDate()` |
| `TIME` | `TIME` | `Time` | `getTime()` | `setTime()` |
| `DATETIME` | `TIMESTAMP` | `Timestamp` | `getTimestamp()` | `setTimestamp()` |
| `GEOGRAPHY` | `VARCHAR` | `String` | `getString()` | `setString()` |
| `JSON` | `VARCHAR` | `String` | `getString()` | `setString()` |
| `ARRAY` | `ARRAY` | `Array` | `getArray()` | `setArray()` * |
| `STRUCT` | `STRUCT` | `Object` | `getObject()` | `setObject()` * |
| `INTERVAL` | `VARCHAR` | `String` | `getString()` | `setString()` |

\* Limited support - see Complex Types section

## Primitive Types

### STRING

**BigQuery:** Variable-length character data (up to 2MB)

```sql
-- BigQuery
SELECT 'hello world' as message
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT 'hello world' as message");
while (rs.next()) {
    String message = rs.getString("message");
    // or: String message = rs.getString(1);
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as name");
pstmt.setString(1, "Alice");
```

---

### BYTES

**BigQuery:** Variable-length binary data (up to 10MB)

```sql
-- BigQuery
SELECT FROM_BASE64('SGVsbG8=') as binary_data
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT FROM_BASE64('SGVsbG8=') as binary_data");
while (rs.next()) {
    byte[] data = rs.getBytes("binary_data");
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as data");
pstmt.setBytes(1, new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F});
```

---

### INT64

**BigQuery:** 64-bit signed integer

**Range:** -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807

```sql
-- BigQuery
SELECT 42 as count, -100 as delta
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT 42 as count");
while (rs.next()) {
    long count = rs.getLong("count");
    int countAsInt = rs.getInt("count"); // Narrowing conversion
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as id");
pstmt.setLong(1, 123456789L);
// or: pstmt.setInt(1, 123);  // Widening conversion
```

**Note:** `getInt()` works but may overflow for large values. Use `getLong()` for safety.

---

### FLOAT64

**BigQuery:** 64-bit IEEE 754 floating point

```sql
-- BigQuery
SELECT 3.14159 as pi, 2.5e10 as large_number
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT 3.14159 as pi");
while (rs.next()) {
    double pi = rs.getDouble("pi");
    float piAsFloat = rs.getFloat("pi"); // Narrowing conversion
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as value");
pstmt.setDouble(1, 3.14159);
// or: pstmt.setFloat(1, 3.14f);  // Widening conversion
```

**Special Values:**
```java
// BigQuery supports IEEE 754 special values
double inf = rs.getDouble("infinity_col");     // Infinity
double negInf = rs.getDouble("neg_infinity");  // -Infinity
double nan = rs.getDouble("nan_col");          // NaN
```

---

### NUMERIC

**BigQuery:** Exact numeric with 38 digits of precision, 9 decimal digits

**Range:** -99999999999999999999999999999.999999999 to 99999999999999999999999999999.999999999

```sql
-- BigQuery
SELECT NUMERIC '123.456' as price
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT NUMERIC '123.456' as price");
while (rs.next()) {
    BigDecimal price = rs.getBigDecimal("price");
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as amount");
pstmt.setBigDecimal(1, new BigDecimal("123.456"));
```

**Use Cases:**
- Financial calculations (currency)
- Precise decimal arithmetic
- When rounding errors are unacceptable

---

### BIGNUMERIC

**BigQuery:** Exact numeric with 76.76 digits of precision (76 integer, 38 decimal)

**Range:** Much larger than NUMERIC

```sql
-- BigQuery
SELECT BIGNUMERIC '1234567890123456789012345678901234567890.12345678901234567890123456789012345678' as huge
```

```java
// JDBC
// Treated identically to NUMERIC
ResultSet rs = stmt.executeQuery("SELECT BIGNUMERIC '123.456' as value");
while (rs.next()) {
    BigDecimal value = rs.getBigDecimal("value");
}
```

**Note:** Java `BigDecimal` can represent BIGNUMERIC values exactly.

---

### BOOL

**BigQuery:** Boolean true/false

```sql
-- BigQuery
SELECT TRUE as active, FALSE as deleted
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT TRUE as active");
while (rs.next()) {
    boolean active = rs.getBoolean("active");
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as flag");
pstmt.setBoolean(1, true);
```

**NULL Handling:**
```java
boolean value = rs.getBoolean("nullable_bool");
if (rs.wasNull()) {
    // Column was NULL, getBoolean returned false
}
```

---

## Temporal Types

### TIMESTAMP

**BigQuery:** Absolute point in time with microsecond precision

**Range:** 0001-01-01 00:00:00 to 9999-12-31 23:59:59.999999 UTC

```sql
-- BigQuery
SELECT CURRENT_TIMESTAMP() as now
SELECT TIMESTAMP '2024-01-15 10:30:00 UTC' as specific_time
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT CURRENT_TIMESTAMP() as now");
while (rs.next()) {
    java.sql.Timestamp ts = rs.getTimestamp("now");
    // Timestamp in UTC
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as ts");
pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
```

**Time Zone Handling:**
```java
// BigQuery TIMESTAMP is always UTC
// JDBC Timestamp is timezone-agnostic
Timestamp ts = rs.getTimestamp("created_at");
Instant instant = ts.toInstant(); // Convert to Instant for timezone-aware operations
```

---

### DATE

**BigQuery:** Calendar date (no time)

**Range:** 0001-01-01 to 9999-12-31

```sql
-- BigQuery
SELECT CURRENT_DATE() as today
SELECT DATE '2024-01-15' as specific_date
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT CURRENT_DATE() as today");
while (rs.next()) {
    java.sql.Date date = rs.getDate("today");
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as birth_date");
pstmt.setDate(1, java.sql.Date.valueOf("1990-05-15"));
```

**Conversion:**
```java
// Convert to java.time.LocalDate
Date sqlDate = rs.getDate("date_col");
LocalDate localDate = sqlDate.toLocalDate();
```

---

### TIME

**BigQuery:** Time of day (no date), microsecond precision

**Range:** 00:00:00 to 23:59:59.999999

```sql
-- BigQuery
SELECT CURRENT_TIME() as now
SELECT TIME '14:30:00' as afternoon
```

```java
// JDBC
ResultSet rs = stmt.executeQuery("SELECT TIME '14:30:00' as time_col");
while (rs.next()) {
    java.sql.Time time = rs.getTime("time_col");
}
```

**PreparedStatement:**
```java
PreparedStatement pstmt = conn.prepareStatement("SELECT ? as opening_time");
pstmt.setTime(1, java.sql.Time.valueOf("09:00:00"));
```

---

### DATETIME

**BigQuery:** Date and time (no timezone), microsecond precision

**Range:** 0001-01-01 00:00:00 to 9999-12-31 23:59:59.999999

```sql
-- BigQuery
SELECT CURRENT_DATETIME() as now
SELECT DATETIME '2024-01-15 14:30:00' as specific
```

```java
// JDBC - Mapped to Timestamp
ResultSet rs = stmt.executeQuery("SELECT DATETIME '2024-01-15 14:30:00' as dt");
while (rs.next()) {
    Timestamp ts = rs.getTimestamp("dt");
}
```

**Difference from TIMESTAMP:**
- DATETIME has no timezone (represents "wall clock" time)
- TIMESTAMP always UTC

---

## Complex Types

### ARRAY

**BigQuery:** Ordered list of zero or more elements of the same type

```sql
-- BigQuery
SELECT [1, 2, 3] as numbers
SELECT ['a', 'b', 'c'] as letters
```

```java
// JDBC - Limited support
ResultSet rs = stmt.executeQuery("SELECT [1, 2, 3] as numbers");
while (rs.next()) {
    // Option 1: Get as Array (limited support in current version)
    Array array = rs.getArray("numbers");

    // Option 2: Get as String representation
    String arrayStr = rs.getString("numbers");
    // Returns: "[1, 2, 3]"
}
```

**Current Limitation:**
Full `java.sql.Array` support is limited. Arrays are currently best accessed as strings and parsed manually.

---

### STRUCT

**BigQuery:** Container of ordered fields each with a type

```sql
-- BigQuery
SELECT STRUCT(1 as id, 'Alice' as name) as person
```

```java
// JDBC - Limited support
ResultSet rs = stmt.executeQuery("SELECT STRUCT(1 as id, 'Alice' as name) as person");
while (rs.next()) {
    // Get as Object
    Object struct = rs.getObject("person");

    // Or get as String representation
    String structStr = rs.getString("person");
    // Returns: "{id=1, name=Alice}" (format may vary)
}
```

**Current Limitation:**
Full `java.sql.Struct` support is limited. Structs are best accessed by querying their fields directly:

```sql
SELECT person.id, person.name FROM table
```

---

## Special Types

### GEOGRAPHY

**BigQuery:** Geographic data (points, lines, polygons) in WGS84

```sql
-- BigQuery
SELECT ST_GEOGPOINT(-122.35, 47.62) as location
```

```java
// JDBC - Mapped to String (WKT format)
ResultSet rs = stmt.executeQuery("SELECT ST_GEOGPOINT(-122.35, 47.62) as location");
while (rs.next()) {
    String wkt = rs.getString("location");
    // Returns: "POINT(-122.35 47.62)"
}
```

**Format:** Well-Known Text (WKT)

---

### JSON

**BigQuery:** JSON-encoded data

```sql
-- BigQuery
SELECT JSON '{"name": "Alice", "age": 30}' as data
```

```java
// JDBC - Mapped to String
ResultSet rs = stmt.executeQuery("SELECT JSON '{\"name\": \"Alice\"}' as data");
while (rs.next()) {
    String json = rs.getString("data");
    // Returns: "{\"name\": \"Alice\"}"

    // Parse with your favorite JSON library
    // ObjectMapper mapper = new ObjectMapper();
    // JsonNode node = mapper.readTree(json);
}
```

---

### INTERVAL

**BigQuery:** Duration between two timestamps

```sql
-- BigQuery
SELECT INTERVAL 5 DAY as duration
```

```java
// JDBC - Mapped to String
ResultSet rs = stmt.executeQuery("SELECT INTERVAL 5 DAY as duration");
while (rs.next()) {
    String interval = rs.getString("duration");
    // Returns: "0-0 5 0:0:0" (format: YEAR-MONTH DAY HOUR:MINUTE:SECOND)
}
```

---

## NULL Handling

All BigQuery types are nullable. Always check for NULL:

```java
ResultSet rs = stmt.executeQuery("SELECT nullable_column FROM table");
while (rs.next()) {
    long value = rs.getLong("nullable_column");

    if (rs.wasNull()) {
        // Column was NULL
        // Primitive getters return 0/false for NULL
    } else {
        // Use value
    }
}
```

**Primitive vs Object Types:**

| Primitive Getter | NULL Returns | Object Getter | NULL Returns |
|------------------|--------------|---------------|--------------|
| `getLong()` | `0` | `getBigDecimal()` | `null` |
| `getInt()` | `0` | `getString()` | `null` |
| `getDouble()` | `0.0` | `getTimestamp()` | `null` |
| `getBoolean()` | `false` | `getDate()` | `null` |

**Best Practice:**
```java
// For primitives - check wasNull()
long id = rs.getLong("id");
if (!rs.wasNull()) {
    // Use id
}

// For objects - check for null directly
String name = rs.getString("name");
if (name != null) {
    // Use name
}
```

---

## Type Conversions

### Automatic Conversions

The driver supports standard JDBC type conversions:

```java
// INT64 → various Java types
long l = rs.getLong("int64_col");      // Natural
int i = rs.getInt("int64_col");        // Narrowing (may overflow)
String s = rs.getString("int64_col");  // String conversion
BigDecimal bd = rs.getBigDecimal("int64_col"); // Widening

// FLOAT64 → various Java types
double d = rs.getDouble("float64_col");
float f = rs.getFloat("float64_col");  // Narrowing
String s = rs.getString("float64_col");

// STRING → various Java types
String s = rs.getString("string_col");
// Numeric strings can be parsed:
// BigDecimal bd = new BigDecimal(rs.getString("numeric_string"));
```

### Unsupported Conversions

❌ These will throw `SQLException`:

```java
// Cannot convert STRING to binary types
byte[] b = rs.getBytes("string_col"); // SQLException

// Cannot convert non-numeric STRING to number
int i = rs.getInt("non_numeric_string"); // SQLException
```

---

## ResultSetMetaData

Get type information at runtime:

```java
ResultSet rs = stmt.executeQuery("SELECT * FROM table");
ResultSetMetaData meta = rs.getMetaData();

for (int i = 1; i <= meta.getColumnCount(); i++) {
    String columnName = meta.getColumnName(i);
    String typeName = meta.getColumnTypeName(i);  // BigQuery type name
    int jdbcType = meta.getColumnType(i);         // java.sql.Types constant
    String javaClass = meta.getColumnClassName(i); // Java class name

    System.out.printf("Column %s: %s (JDBC: %d, Java: %s)%n",
        columnName, typeName, jdbcType, javaClass);
}
```

**Example Output:**
```
Column id: INT64 (JDBC: -5, Java: java.lang.Long)
Column name: STRING (JDBC: 12, Java: java.lang.String)
Column price: NUMERIC (JDBC: 2, Java: java.math.BigDecimal)
Column created: TIMESTAMP (JDBC: 93, Java: java.sql.Timestamp)
```

---

## See Also

- [Quick Start](QUICKSTART.md) - Basic query examples
- [Connection Properties](CONNECTION_PROPERTIES.md) - Configuration reference
- [Compatibility Matrix](COMPATIBILITY.md) - JDBC feature support
- [BigQuery Data Types](https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types) - Official BigQuery documentation
