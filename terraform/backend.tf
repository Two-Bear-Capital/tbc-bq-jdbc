terraform {
  backend "gcs" {
    bucket = "tbc-bq-jdbc-tfstate"
    prefix = "terraform/state"
  }
}
