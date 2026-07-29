# Authentication

Complete guide to all authentication methods supported by tbc-bq-jdbc.

## Overview

tbc-bq-jdbc supports all Google Cloud authentication methods:

| Method | `authType` | Use Case | Required properties |
|--------|-----------|----------|---------------------|
| **ADC** | `ADC` | Local development, GCE/GKE | None — discovered from the environment |
| **Service Account** | `SERVICE_ACCOUNT` | Production, automation | `credentials` (path to a JSON key file) |
| **User OAuth** | `USER_OAUTH` | End-user applications | `clientId`, `clientSecret`, `refreshToken` |
| **Workforce Identity** | `WORKFORCE` | Federated workforce access | `credentialConfigFile` |
| **Workload Identity** | `WORKLOAD` | GKE / workload federation | `credentialConfigFile` |

`authType` defaults to `ADC` when omitted, and its value is case-insensitive. Omitting a
required property fails at connection time with a message naming the property.

[Service account impersonation](#service-account-impersonation) is not an `authType` value.
It layers on top of whichever method above you choose, through the
`impersonateServiceAccount` property.

The accepted `authType` values and their underlying implementations are generated from the driver —
see [the generated reference](generated/authentication.md). The sections below cover setup,
credentials, and examples for each method.

<!-- @include: generated/authentication.md -->

A `host` in the URL controls only which address the driver reaches BigQuery at. It has no
effect on how you authenticate.

## Application Default Credentials (ADC)

**Recommended for:** Local development, Google Cloud environments

ADC automatically discovers credentials from the environment:
1. `GOOGLE_APPLICATION_CREDENTIALS` environment variable
2. gcloud CLI credentials
3. GCE/GKE metadata server

### Setup

```bash
# Option 1: Use gcloud CLI
gcloud auth application-default login

# Option 2: Set environment variable
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json
```

### Usage

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url)) {
    // Credentials automatically discovered
}
```

### Configuration Properties

None required. ADC discovers credentials automatically.

### Advantages

✅ No hardcoded credentials
✅ Works seamlessly in Google Cloud
✅ Easy local development
✅ Automatic credential refresh

### When to Use

- Local development with gcloud CLI
- Running on Google Compute Engine
- Running on Google Kubernetes Engine
- Cloud Functions, Cloud Run, App Engine

---

## Service Account (JSON Key)

**Recommended for:** Production deployments, automation, CI/CD

Service accounts are robot accounts for machine-to-machine authentication.

The `credentials` property is a **filesystem path** to the JSON key file. Passing the key
material inline is not supported.

Credentials are built once and shared by every connection using the same authentication,
then rebuilt after an hour so a rotated key takes effect without restarting the JVM. The
window is set by the `tbc.bq.jdbc.credentials.ttl.seconds` system property; `0` disables
expiry.

### Setup

1. **Create Service Account:**
   ```bash
   gcloud iam service-accounts create my-bq-service-account \
       --display-name="BigQuery JDBC Service Account"
   ```

2. **Grant BigQuery Permissions:**
   ```bash
   gcloud projects add-iam-policy-binding my-project \
       --member="serviceAccount:my-bq-service-account@my-project.iam.gserviceaccount.com" \
       --role="roles/bigquery.user"

   gcloud projects add-iam-policy-binding my-project \
       --member="serviceAccount:my-bq-service-account@my-project.iam.gserviceaccount.com" \
       --role="roles/bigquery.dataViewer"
   ```

3. **Create and Download Key:**
   ```bash
   gcloud iam service-accounts keys create key.json \
       --iam-account=my-bq-service-account@my-project.iam.gserviceaccount.com
   ```

### Usage

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=SERVICE_ACCOUNT&" +
             "credentials=/path/to/service-account-key.json";

try (Connection conn = DriverManager.getConnection(url)) {
    // Authenticated as service account
}
```

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `authType` | Yes | Must be `SERVICE_ACCOUNT` |
| `credentials` | Yes | Path to JSON key file |

### Security Best Practices

🔒 **Never commit keys to version control**
🔒 **Use environment variables for key paths**
🔒 **Rotate keys regularly**
🔒 **Use least-privilege IAM roles**
🔒 **Enable key expiration**

### Example with Environment Variable

```java
String keyPath = System.getenv("BIGQUERY_SA_KEY_PATH");
String url = String.format(
    "jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=%s",
    keyPath
);
```

### Required IAM Roles

Minimum permissions for BigQuery:
- `roles/bigquery.user` - Run queries
- `roles/bigquery.dataViewer` - Read table data
- `roles/bigquery.jobUser` - Create jobs

For write operations:
- `roles/bigquery.dataEditor` - Insert/update/delete data

---

## User OAuth

**Recommended for:** Desktop applications, end-user authentication

Authenticates as an individual Google user account.

### Setup

1. **Create OAuth 2.0 Client:**
   - Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Create OAuth 2.0 Client ID
   - Application type: Desktop app or Web application
   - Download client secret JSON

2. **Enable BigQuery API:**
   ```bash
   gcloud services enable bigquery.googleapis.com
   ```

### Usage

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=USER_OAUTH&" +
             "clientId=123456789.apps.googleusercontent.com&" +
             "clientSecret=GOCSPX-abc123&" +
             "refreshToken=1//abc123";

try (Connection conn = DriverManager.getConnection(url)) {
    // Authenticated as user
}
```

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `authType` | Yes | Must be `USER_OAUTH` |
| `clientId` | Yes | OAuth 2.0 client ID |
| `clientSecret` | Yes | OAuth 2.0 client secret |
| `refreshToken` | Yes | OAuth 2.0 refresh token |

> All three (`clientId`, `clientSecret`, `refreshToken`) are required — the driver does not run an
> interactive browser flow. Obtain a refresh token out of band first, then supply it here.

### Obtaining a refresh token

The driver expects a pre-obtained refresh token. To get one:

```text
1. User authenticates via browser (one time)
2. Obtain an authorization code
3. Exchange the code for a refresh token
4. Store the refresh token securely
5. Pass it as `refreshToken` on every connection
```

### When to Use

- Desktop applications where users sign in with Google
- Tools that need to act on behalf of a user
- Applications requiring user-level audit trails

---

## Workforce Identity Federation

**Recommended for:** Workforce (employee) access with external identity providers

Allows employees to use existing corporate credentials (Azure AD, Okta, etc.) to access BigQuery.

### Setup

1. **Configure Workforce Identity Pool:**
   ```bash
   gcloud iam workforce-pools create my-workforce-pool \
       --organization=123456789 \
       --location=global
   ```

2. **Configure OIDC Provider:**
   ```bash
   gcloud iam workforce-pools providers create-oidc my-provider \
       --workforce-pool=my-workforce-pool \
       --location=global \
       --issuer-uri=https://accounts.google.com \
       --client-id=my-client-id
   ```

3. **Create Configuration File:**
   ```json
   {
     "type": "external_account",
     "audience": "//iam.googleapis.com/locations/global/workforcePools/my-workforce-pool/providers/my-provider",
     "subject_token_type": "urn:ietf:params:oauth:token-type:id_token",
     "token_url": "https://sts.googleapis.com/v1/token",
     "credential_source": {
       "file": "/path/to/oidc-token.txt"
     }
   }
   ```

### Usage

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=WORKFORCE&" +
             "credentialConfigFile=/path/to/workforce-config.json";

try (Connection conn = DriverManager.getConnection(url)) {
    // Authenticated via workforce identity
}
```

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `authType` | Yes | Must be `WORKFORCE` |
| `credentialConfigFile` | Yes | Path to workforce config JSON |

---

## Workload Identity Federation

**Recommended for:** GKE workloads, external cloud providers

Allows workloads running outside Google Cloud to authenticate without service account keys.

### Setup (GKE Example)

1. **Create Workload Identity Pool:**
   ```bash
   gcloud iam workload-identity-pools create my-pool \
       --project=my-project \
       --location=global
   ```

2. **Configure Provider:**
   ```bash
   gcloud iam workload-identity-pools providers create-oidc my-provider \
       --workload-identity-pool=my-pool \
       --issuer-uri=https://container.googleapis.com/v1/projects/my-project/locations/us-central1/clusters/my-cluster \
       --attribute-mapping="google.subject=assertion.sub"
   ```

3. **Bind Service Account:**
   ```bash
   gcloud iam service-accounts add-iam-policy-binding my-sa@my-project.iam.gserviceaccount.com \
       --role=roles/iam.workloadIdentityUser \
       --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/my-pool/*"
   ```

### Usage

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=WORKLOAD&" +
             "credentialConfigFile=/path/to/workload-config.json";

try (Connection conn = DriverManager.getConnection(url)) {
    // Authenticated via workload identity federation
}
```

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `authType` | Yes | Must be `WORKLOAD` |
| `credentialConfigFile` | Yes | Path to workload identity config JSON |

> **Note:** The driver requires a `credentialConfigFile` for `WORKLOAD`. If you are running on
> GKE/GCE and want to use the metadata service with no config file, use `authType=ADC` instead —
> ADC automatically picks up the workload's attached service account.

---

## Service Account Impersonation

Impersonation lets you authenticate as yourself and run queries as a service account. The
driver exchanges your credentials for a short-lived token belonging to the target service
account, so nothing long-lived has to be distributed.

This is not an `authType` value. It wraps whichever authentication method the connection
already uses, named by the `impersonateServiceAccount` property.

### Setup

Grant the caller — your own user, or the service account the connection authenticates as —
`roles/iam.serviceAccountTokenCreator` on the target:

```bash
gcloud iam service-accounts add-iam-policy-binding \
  etl@my-project.iam.gserviceaccount.com \
  --member="user:me@example.com" \
  --role="roles/iam.serviceAccountTokenCreator"
```

The target service account needs whatever BigQuery roles the queries require
(`roles/bigquery.jobUser`, plus dataset access). The caller does not.

The `iamcredentials.googleapis.com` API must be enabled on the project holding the target.

### Usage

With Application Default Credentials as the source:

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com";

try (Connection conn = DriverManager.getConnection(url)) {
    // Queries run as etl@my-project.iam.gserviceaccount.com
}
```

With a service account key file as the source:

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=SERVICE_ACCOUNT&" +
             "credentials=/keys/bootstrap.json&" +
             "impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com";
```

### Delegation chains

When the caller cannot mint a token for the target directly but can reach it through
intermediates, list them in `impersonateDelegates`, source-first:

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com&" +
             "impersonateDelegates=mid1@my-project.iam.gserviceaccount.com," +
             "mid2@my-project.iam.gserviceaccount.com";
```

Each link needs its own grant: the caller must hold `serviceAccountTokenCreator` on `mid1`,
`mid1` on `mid2`, and `mid2` on the target. Most deployments need no delegates at all.

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `impersonateServiceAccount` | No | Email of the service account to impersonate. Setting it enables impersonation |
| `impersonateDelegates` | No | Comma-separated intermediate service account emails, source-first |

`impersonateDelegates` without `impersonateServiceAccount` is rejected at connection time.

### Verifying

`SESSION_USER()` reports the identity BigQuery sees, which is the target when impersonation
is in effect:

```java
try (Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT SESSION_USER()")) {
    rs.next();
    System.out.println(rs.getString(1));
}
```

---

## Authentication Comparison

### Security

| Method | Key Storage | Auto-Rotation | Audit Trail |
|--------|-------------|---------------|-------------|
| ADC | Environment | ✅ | User/SA specific |
| Service Account | File/Secret | ❌ | Service account |
| User OAuth | Refresh token | ✅ | User specific |
| Workforce | Config file | ✅ | User specific |
| Workload | Config file | ✅ | Workload specific |
| Impersonation | None — layered over one of the above | ✅ | Target service account |

### Use Case Matrix

| Scenario | Recommended Method |
|----------|-------------------|
| Local development | ADC |
| CI/CD pipeline | Service Account |
| Production server | Service Account or Workload Identity |
| GKE deployment | Workload Identity |
| Desktop app | User OAuth |
| Enterprise SSO | Workforce Identity |
| Cloud Function | ADC (automatic) |
| Lambda/external cloud | Workload Identity |
| Running as a service account without distributing its key | ADC + `impersonateServiceAccount` |

---

## Testing Authentication

### Verify Credentials

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url)) {
    if (conn.isValid(5)) {
        System.out.println("✅ Authentication successful");
    }
} catch (SQLException e) {
    System.err.println("❌ Authentication failed: " + e.getMessage());
}
```

### Common Authentication Errors

The driver validates required properties before contacting BigQuery, so a missing one
fails fast with a message naming it:

| Error | Cause | Solution |
|-------|-------|----------|
| `credentials property required for SERVICE_ACCOUNT authentication` | `authType=SERVICE_ACCOUNT` without `credentials` | Add `credentials=/path/to/key.json` |
| `clientId, clientSecret, and refreshToken required for USER_OAUTH authentication` | `authType=USER_OAUTH` missing one of the three | Supply all three properties |
| `credentialConfigFile required for WORKFORCE authentication` | `authType=WORKFORCE` without the config file | Add `credentialConfigFile=/path/to/config.json` |
| `credentialConfigFile required for WORKLOAD authentication` | `authType=WORKLOAD` without the config file | Add `credentialConfigFile=/path/to/config.json` |
| `Unsupported authentication type` | `authType` is not one of the five values | Use `ADC`, `SERVICE_ACCOUNT`, `USER_OAUTH`, `WORKFORCE` or `WORKLOAD` |
| `impersonateDelegates requires impersonateServiceAccount` | A delegation chain with no target | Add `impersonateServiceAccount=sa@project.iam.gserviceaccount.com` |

Errors raised by Google Cloud rather than the driver — expired credentials, insufficient
IAM permissions, a project that does not exist — surface with the service's own message.

Impersonation is validated by Google Cloud, not the driver, and the token is minted on the
first query rather than at connection time. A missing
`roles/iam.serviceAccountTokenCreator` grant therefore fails on that first statement with
`Error requesting access token`; the `PERMISSION_DENIED` naming
`iam.serviceAccounts.getAccessToken` is in the exception's cause chain.

---

## Environment-Specific Recommendations

### Development
```java
// Use ADC with gcloud CLI
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
```

### Staging
```java
// Use service account
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=SERVICE_ACCOUNT&" +
             "credentials=/etc/secrets/bigquery-key.json";
```

### Production (GKE)
```java
// On GKE/GCE, ADC uses the workload's attached service account — no keys, no config file
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
```

### Production (Non-GCP)
```java
// Use service account with key rotation
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=SERVICE_ACCOUNT&" +
             "credentials=/vault/bigquery-rotating-key.json";
```

---

## See Also

- [Connection Properties](CONNECTION_PROPERTIES.md) - All configuration options
- [Quick Start](QUICKSTART.md) - Get started in 5 minutes
