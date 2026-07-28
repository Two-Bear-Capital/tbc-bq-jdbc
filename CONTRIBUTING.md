# Contributing to tbc-bq-jdbc

Thank you for considering contributing to the BigQuery JDBC driver!

## Development Setup

### Prerequisites

- Java 21 or later
- Git
- No need to install Maven (project includes Maven Wrapper)

### Building

```bash
./mvnw clean install
```

### Running Tests

```bash
# Unit tests only
./mvnw test

# Integration tests (real BigQuery; needs ADC and BQ_TEST_PROJECT)
export BQ_TEST_PROJECT=my-gcp-project
./mvnw verify -Preal-integration-tests
```

`./mvnw verify` on its own runs the unit tests only — the integration tests need the
`real-integration-tests` profile, and skip silently when `BQ_TEST_PROJECT` is unset.

### Detailed contributor guides

Build, test, and release topics live under [`docs/contributing/`](docs/contributing/) (these are
intentionally kept out of the user-facing documentation site):

- [Integration Tests](docs/contributing/INTEGRATION_TESTS.md) — running and writing the real-BigQuery integration tests
- [Performance Instrumentation](docs/contributing/PERFORMANCE.md) — JFR recordings, thread-scaling benchmarks, and the opt-in scale and load tests
- [JAR Size Optimization](docs/contributing/JAR_SIZE_OPTIMIZATION.md) — the shading/size strategy for the distributed JARs
- [Publishing to Maven Central](docs/contributing/MAVEN_CENTRAL_PUBLISHING.md) — the release runbook

### Code Formatting

This project uses Google Java Format via Spotless:

```bash
# Check formatting
./mvnw spotless:check

# Apply formatting
./mvnw spotless:apply
```

**Important**: All code must pass `./mvnw spotless:check` before submission.

## Contribution Guidelines

### Code Style

- Follow Google Java Format (enforced by Spotless)
- Use Java 21 features where appropriate (records, sealed classes, pattern matching)
- Write clear, self-documenting code
- Add comments only where logic isn't self-evident

### Testing

- Write unit tests for all new functionality
- Add integration tests for user-facing features
- Ensure all tests pass before submitting
- Aim for >80% code coverage

### Commit Messages

**[Conventional Commits](https://www.conventionalcommits.org/) are required.** The same
`cliff.toml` parsers drive both the generated changelog and the released version number,
so the prefix you choose decides the next release.

| Prefix | Meaning | Version bump |
|---|---|---|
| `feat(scope):` | New feature | **minor** (1.0.x → 1.1.0) |
| `fix(scope):` | Bug fix | patch |
| `perf(scope):` | Performance | patch |
| `docs(scope):` | Documentation | patch |
| `test(scope):` | Tests | patch |
| `refactor(scope):` | Refactoring | patch |
| `chore(scope):` | Maintenance | patch (`chore(deps)` is skipped) |
| `feat(scope)!:` or a `BREAKING CHANGE:` footer | Breaking change | **major** (1.x → 2.0.0) |

Keep the first line under 70 characters and put detail in the body.

Example:
```
feat(types): support ARRAY columns in ResultSet

- Implement getArray()
- Add type mapping for BigQuery ARRAY
- Include integration tests
```

Labelling a feature as `fix` understates the release, so pick the prefix deliberately.

### Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run tests and formatting checks
5. Commit your changes
6. Push to your fork
7. Open a Pull Request

### PR Checklist

- [ ] Code follows project style (Spotless passes)
- [ ] All tests pass
- [ ] New tests added for new functionality
- [ ] Documentation updated (if needed)
- [ ] Commit messages are clear
- [ ] No unrelated changes included

## Questions?

Open an issue on GitHub for questions or discussions.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
