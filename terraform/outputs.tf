output "project_id" {
  description = "The GCP project ID used for integration tests."
  value       = google_project.integration.project_id
}

output "dataset_id" {
  description = "The BigQuery dataset ID for integration tests."
  value       = google_bigquery_dataset.integration_tests.dataset_id
}

output "ci_service_account_email" {
  description = "Email of the CI service account. Set as WIF_SERVICE_ACCOUNT GitHub secret."
  value       = google_service_account.ci.email
}

output "wif_provider" {
  description = "Full WIF provider resource name. Set as WIF_PROVIDER GitHub secret."
  value       = google_iam_workload_identity_pool_provider.github_oidc.name
}

output "impersonation_target_service_account" {
  description = "Email of the impersonation target. Set as the BQ_TEST_IMPERSONATE_SA environment variable or GitHub secret."
  value       = google_service_account.impersonated.email
}

output "impersonation_delegate_service_account" {
  description = "Email of the impersonation delegate. Set as the BQ_TEST_IMPERSONATE_DELEGATE environment variable or GitHub secret."
  value       = google_service_account.impersonation_delegate.email
}

output "wif_pool" {
  description = "WIF pool resource name (for reference)."
  value       = google_iam_workload_identity_pool.github_actions.name
}
