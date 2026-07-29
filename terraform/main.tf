terraform {
  required_version = ">= 1.6"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.project_id
}

# ── GCP Project ─────────────────────────────────────────────────────────────

resource "google_project" "integration" {
  name            = "TBC BQ JDBC Integration Tests"
  project_id      = var.project_id
  org_id          = var.org_id
  billing_account = var.billing_account

  lifecycle {
    prevent_destroy = true
  }
}

# ── Enable Required APIs ────────────────────────────────────────────────────

resource "google_project_service" "bigquery" {
  project            = google_project.integration.project_id
  service            = "bigquery.googleapis.com"
  disable_on_destroy = false
}

# The Storage Read API is a distinct service from bigquery.googleapis.com, with
# its own permission (bigquery.readsessions.create). The driver uses it for the
# useStorageApi result path (#152), so the integration suite needs it enabled.
resource "google_project_service" "bigquery_storage" {
  project            = google_project.integration.project_id
  service            = "bigquerystorage.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "iam_credentials" {
  project            = google_project.integration.project_id
  service            = "iamcredentials.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "iam" {
  project            = google_project.integration.project_id
  service            = "iam.googleapis.com"
  disable_on_destroy = false
}

# ── BigQuery Dataset ─────────────────────────────────────────────────────────

resource "google_bigquery_dataset" "integration_tests" {
  project     = google_project.integration.project_id
  dataset_id  = var.dataset_id
  location    = var.region
  description = "Dataset for tbc-bq-jdbc integration tests"

  # Allow tests to create/drop tables freely
  delete_contents_on_destroy = true

  depends_on = [google_project_service.bigquery]
}

# ── CI Service Account ───────────────────────────────────────────────────────

resource "google_service_account" "ci" {
  project      = google_project.integration.project_id
  account_id   = "tbc-bq-jdbc-ci"
  display_name = "TBC BQ JDBC CI Service Account"
  description  = "Used by GitHub Actions to run real BigQuery integration tests via WIF"

  depends_on = [google_project_service.iam]
}

# ── IAM Bindings for CI Service Account ─────────────────────────────────────

resource "google_bigquery_dataset_iam_member" "ci_data_editor" {
  project    = google_project.integration.project_id
  dataset_id = google_bigquery_dataset.integration_tests.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${google_service_account.ci.email}"
}

resource "google_project_iam_member" "ci_job_user" {
  project = google_project.integration.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.ci.email}"
}

# bigquery.jobUser and dataEditor do not cover reading through the Storage Read
# API; that needs bigquery.readsessions.create. Without this,
# StorageApiParityTest fails with PERMISSION_DENIED and the driver quietly falls
# back to the REST path, leaving the Storage path untested in CI.
resource "google_project_iam_member" "ci_read_session_user" {
  project = google_project.integration.project_id
  role    = "roles/bigquery.readSessionUser"
  member  = "serviceAccount:${google_service_account.ci.email}"

  depends_on = [google_project_service.bigquery_storage]
}

# ── Service Account Impersonation Fixture ────────────────────────────────────
#
# Two service accounts, existing only so RealImpersonationTest can exercise both
# shapes the driver supports (#197):
#
#   direct  : caller ──────────────────────────────► impersonated
#   delegated: caller ── delegate ──────────────────► impersonated
#
# Only "impersonated" holds BigQuery roles. "delegate" deliberately holds none,
# so the delegated test proves the token really was minted for the target — a
# chain that silently resolved to the delegate could not run a query at all.

resource "google_service_account" "impersonated" {
  project      = google_project.integration.project_id
  account_id   = "tbc-bq-jdbc-impersonated"
  display_name = "TBC BQ JDBC Impersonation Target"
  description  = "Target identity for the driver's service account impersonation tests"

  depends_on = [google_project_service.iam]
}

resource "google_service_account" "impersonation_delegate" {
  project      = google_project.integration.project_id
  account_id   = "tbc-bq-jdbc-delegate"
  display_name = "TBC BQ JDBC Impersonation Delegate"
  description  = "Intermediate identity for the driver's delegated impersonation test; holds no BigQuery access"

  depends_on = [google_project_service.iam]
}

# The impersonated identity must be able to run the same queries the tests run
# as themselves, or a passing connection would prove nothing about the token.

resource "google_bigquery_dataset_iam_member" "impersonated_data_viewer" {
  project    = google_project.integration.project_id
  dataset_id = google_bigquery_dataset.integration_tests.dataset_id
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${google_service_account.impersonated.email}"
}

resource "google_project_iam_member" "impersonated_job_user" {
  project = google_project.integration.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.impersonated.email}"
}

# Direct hop: CI mints tokens for the target itself.
resource "google_service_account_iam_member" "ci_impersonates_target" {
  service_account_id = google_service_account.impersonated.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.ci.email}"

  depends_on = [google_project_service.iam_credentials]
}

# Delegated hop: CI mints for the delegate, and the delegate mints for the
# target. Both links are required — IAM checks the chain pairwise.
resource "google_service_account_iam_member" "ci_impersonates_delegate" {
  service_account_id = google_service_account.impersonation_delegate.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.ci.email}"

  depends_on = [google_project_service.iam_credentials]
}

resource "google_service_account_iam_member" "delegate_impersonates_target" {
  service_account_id = google_service_account.impersonated.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.impersonation_delegate.email}"

  depends_on = [google_project_service.iam_credentials]
}

# Local development: the same two grants for whoever runs the suite from a
# workstation under their own ADC. Empty by default, because a principal that
# can mint tokens for an identity with BigQuery access is a real grant and
# should be named deliberately rather than inherited from a default.
resource "google_service_account_iam_member" "local_impersonates_target" {
  for_each = toset(var.impersonation_source_principals)

  service_account_id = google_service_account.impersonated.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = each.value

  depends_on = [google_project_service.iam_credentials]
}

resource "google_service_account_iam_member" "local_impersonates_delegate" {
  for_each = toset(var.impersonation_source_principals)

  service_account_id = google_service_account.impersonation_delegate.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = each.value

  depends_on = [google_project_service.iam_credentials]
}

# ── Workload Identity Federation ─────────────────────────────────────────────

resource "google_iam_workload_identity_pool" "github_actions" {
  project                   = google_project.integration.project_id
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions"
  description               = "WIF pool for GitHub Actions OIDC tokens"

  depends_on = [google_project_service.iam_credentials]
}

resource "google_iam_workload_identity_pool_provider" "github_oidc" {
  project                            = google_project.integration.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github_actions.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-oidc"
  display_name                       = "GitHub OIDC Provider"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.actor"      = "assertion.actor"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # Restrict to the specific GitHub repository and owner
  attribute_condition = <<-EOT
    assertion.repository == "${var.github_repo}" &&
    assertion.repository_owner == "${split("/", var.github_repo)[0]}"
  EOT
}

# Allow GitHub Actions (main branch only) to impersonate the CI service account
resource "google_service_account_iam_member" "wif_impersonation" {
  service_account_id = google_service_account.ci.name
  role               = "roles/iam.workloadIdentityUser"

  # Restrict to pushes on the main branch only
  member = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github_actions.name}/attribute.repository/${var.github_repo}"
}
