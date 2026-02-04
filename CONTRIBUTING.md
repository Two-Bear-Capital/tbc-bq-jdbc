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

# All tests (including integration tests)
./mvnw verify
```

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

- Use clear, descriptive commit messages
- Start with a verb in imperative form (Add, Fix, Update, etc.)
- Keep first line under 70 characters
- Add details in the body if needed

Example:
```
Add support for ARRAY type in ResultSet

- Implement getArray() method
- Add type mapping for BigQuery ARRAY
- Include integration tests
```

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
