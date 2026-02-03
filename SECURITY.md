# Security Policy

## Supported Versions

We release patches for security vulnerabilities for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of the BigQuery JDBC Driver seriously. If you believe you have found a security vulnerability, please report it to us as described below.

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them via GitHub Security Advisories:

1. Go to https://github.com/Two-Bear-Capital/tbc-bq-jdbc/security/advisories
2. Click "Report a vulnerability"
3. Provide a detailed description of the vulnerability

Alternatively, you can email security concerns to the maintainers directly via GitHub.

### What to Include

Please include the following information in your report:

- Type of vulnerability
- Full paths of source file(s) related to the vulnerability
- Location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit it

### Response Timeline

- We will acknowledge your report within 48 hours
- We will provide a more detailed response within 5 business days
- We will work with you to understand and validate the issue
- Once validated, we will work on a fix and coordinate disclosure

## Security Update Policy

- Security updates will be released as patch versions (e.g., 1.0.1)
- Critical vulnerabilities will be prioritized and released as soon as possible
- Security advisories will be published on GitHub Security Advisories
- Release notes will clearly indicate security fixes

## Best Practices

When using the BigQuery JDBC Driver:

- Always use the latest version
- Keep your dependencies up to date (use Dependabot)
- Follow Google Cloud security best practices for authentication
- Never commit credentials or service account keys to version control
- Use environment variables or secure credential management systems
- Regularly review and rotate service account keys
- Apply principle of least privilege for BigQuery access

## Secure Configuration

### Authentication

- Use service account JSON key files stored securely
- Alternatively, use Application Default Credentials (ADC)
- Never hardcode credentials in connection strings
- Rotate service account keys regularly

### Connection Strings

Avoid embedding sensitive information in JDBC URLs:

```java
// Good: Use properties for credentials
Properties props = new Properties();
props.setProperty("credentialsFile", "/secure/path/to/credentials.json");
Connection conn = DriverManager.getConnection("jdbc:bigquery://...", props);

// Bad: Credentials in URL (visible in logs)
Connection conn = DriverManager.getConnection("jdbc:bigquery://...?credentials=...");
```

## Dependency Security

We use:

- **Dependabot** for automated dependency updates
- **GitHub Actions** for CI/CD security
- Regular security audits of dependencies

## Contact

For security-related questions or concerns, please use GitHub Security Advisories or contact the maintainers through GitHub.