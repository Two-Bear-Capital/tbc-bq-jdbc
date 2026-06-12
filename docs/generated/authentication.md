<!--
  GENERATED FILE — DO NOT EDIT BY HAND.
  Produced by vc.tbc.bq.jdbc.docgen.DocGen from the driver source of truth.
  Regenerate with: ./mvnw test-compile exec:java -Pdocs
-->

# Authentication

Authentication is selected with the `authType` connection property. The accepted values below are read straight from the driver's advertised property choices.

## `authType` values

| Value | Description |
| --- | --- |
| `ADC` | Application Default Credentials (gcloud, GCE/GKE metadata, env var). |
| `SERVICE_ACCOUNT` | Service account JSON key file (set via `credentials`). |
| `USER_OAUTH` | User OAuth flow (client id/secret/refresh token). |
| `WORKFORCE` | Workforce identity federation (set via `credentialConfigFile`). |
| `WORKLOAD` | Workload identity federation (set via `credentialConfigFile`). |

## Implementations

`AuthType` is a sealed interface; the following implementations are permitted. This list is reflected from the sealed hierarchy, so it stays complete automatically.

| Implementation | Notes |
| --- | --- |
| `ApplicationDefaultAuth` |  |
| `EmulatorAuth` | Testing only — not selectable via `authType`. |
| `ServiceAccountAuth` |  |
| `UserOAuthAuth` |  |
| `WorkforceIdentityAuth` |  |
| `WorkloadIdentityAuth` |  |
