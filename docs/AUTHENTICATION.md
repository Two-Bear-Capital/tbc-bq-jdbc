# Authentication Guide

Complete guide to all authentication methods supported by tbc-bq-jdbc.

## Overview

tbc-bq-jdbc supports all Google Cloud authentication methods:

| Method | Use Case | Requires Credentials File |
|--------|----------|---------------------------|
| **ADC** | Local development, GCE/GKE | No (uses environment) |
| **Service Account** | Production, automation | Yes (JSON key file) |
| **User OAuth** | End-user applications | Yes (client ID/secret) |
| **Workforce Identity** | Federated workforce access | Yes (config file) |
| **Workload Identity** | GKE workload federation | No (uses metadata) |

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
| `refreshToken` | No | Refresh token (if already obtained) |

### OAuth Flow

For initial authentication without refresh token:

```java
// 1. User authenticates via browser
// 2. Obtain authorization code
// 3. Exchange for refresh token
// 4. Store refresh token securely
// 5. Use refresh token for subsequent connections
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
             "credentials=/path/to/workforce-config.json";

try (Connection conn = DriverManager.getConnection(url)) {
    // Authenticated via workforce identity
}
```

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `authType` | Yes | Must be `WORKFORCE` |
| `credentials` | Yes | Path to workforce config JSON |

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
String url = "jdbc:bigquery:my-project/my_dataset?authType=WORKLOAD";

try (Connection conn = DriverManager.getConnection(url)) {
    // Authenticated via workload identity (uses metadata service)
}
```

### Configuration Properties

| Property | Required | Description |
|----------|----------|-------------|
| `authType` | Yes | Must be `WORKLOAD` |

No credentials file needed - uses GKE metadata service.

---

## Authentication Comparison

### Security

| Method | Key Storage | Auto-Rotation | Audit Trail |
|--------|-------------|---------------|-------------|
| ADC | Environment | ✅ | User/SA specific |
| Service Account | File/Secret | ❌ | Service account |
| User OAuth | Refresh token | ✅ | User specific |
| Workforce | Config file | ✅ | User specific |
| Workload | Metadata | ✅ | Workload specific |

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

---

## Testing Authentication

### Verify Credentials

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url)) {
    if (conn.isValid(5)) {
        System.out.println("✅ Authentication successful");

        // Get authenticated user/service account
        DatabaseMetaData meta = conn.getMetaData();
        System.out.println("Connected as: " + meta.getUserName());
    }
} catch (SQLException e) {
    System.err.println("❌ Authentication failed: " + e.getMessage());
}
```

### Common Authentication Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Could not load credentials" | Missing credentials file | Check file path |
| "Permission denied" | Insufficient IAM permissions | Grant BigQuery roles |
| "Invalid authentication" | Expired/invalid credentials | Refresh credentials |
| "Project not found" | Wrong project ID | Verify project ID |

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
// Use workload identity (no keys!)
String url = "jdbc:bigquery:my-project/my_dataset?authType=WORKLOAD";
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
- [Troubleshooting](TROUBLESHOOTING.md) - Common authentication issues
