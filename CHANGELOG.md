# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project scaffolding
- Maven build with Java 21
- JDBC 4.3 core driver implementation
- BigQuery Storage Read API integration
- Comprehensive authentication support (Service Account, ADC, OAuth, Workforce, Workload)
- Session and multi-statement support
- Complete type mapping for all BigQuery types
- Testcontainers integration tests
- JMH performance benchmarks
- Shaded JAR distribution

### Known Limitations
- No support for traditional JDBC transactions outside of BigQuery sessions (BigQuery limitation)
- UPDATE/DELETE require DML syntax (BigQuery limitation)
- Limited index support (BigQuery limitation)
- `jdbcCompliant()` returns false due to BigQuery's limitations

## Future Releases

### Planned for 1.0.0
- Complete JDBC 4.3 implementation
- Production-ready stability
- Maven Central distribution
- Comprehensive documentation

---

[Unreleased]: https://github.com/twobearcapital/tbc-bq-jdbc/compare/v1.0.0...HEAD
