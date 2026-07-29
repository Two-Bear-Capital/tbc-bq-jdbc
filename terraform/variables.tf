variable "org_id" {
  description = "The GCP organization ID under which to create the project."
  type        = string
}

variable "billing_account" {
  description = "The GCP billing account ID to link to the project."
  type        = string
}

variable "github_repo" {
  description = "The GitHub repository in 'owner/repo' format (e.g. 'Two-Bear-Capital/tbc-bq-jdbc')."
  type        = string
  default     = "Two-Bear-Capital/tbc-bq-jdbc"
}

variable "project_id" {
  description = "The GCP project ID to create for integration tests."
  type        = string
  default     = "bigquery-jdbc-driver-test"
}

variable "region" {
  description = "The GCP region for the BigQuery dataset."
  type        = string
  default     = "US"
}

variable "dataset_id" {
  description = "The BigQuery dataset ID for integration tests."
  type        = string
  default     = "tbc_bq_jdbc_integration_tests"
}

variable "impersonation_source_principals" {
  description = <<-EOT
    Extra IAM principals allowed to impersonate the test service accounts, in full
    member form (e.g. "user:someone@example.com"). CI already has these grants; this
    is for running the impersonation tests locally under your own ADC. Empty by
    default so the grant is always named deliberately.
  EOT
  type        = list(string)
  default     = []
}
