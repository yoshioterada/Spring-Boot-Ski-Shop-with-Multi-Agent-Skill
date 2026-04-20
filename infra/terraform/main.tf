terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # Uncomment and configure for remote state
  # backend "azurerm" {
  #   resource_group_name  = "skishop-tfstate-rg"
  #   storage_account_name = "skishoptfstate"
  #   container_name       = "tfstate"
  #   key                  = "skishop.terraform.tfstate"
  # }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}

# =============================================================================
# Variables
# =============================================================================

variable "subscription_id" {
  description = "Azure Subscription ID"
  type        = string
}

variable "resource_group_name" {
  description = "Name of the Azure Resource Group"
  type        = string
  default     = "skishop-rg"
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "japaneast"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "db_admin_username" {
  description = "PostgreSQL administrator username"
  type        = string
  default     = "<DB_ADMIN_USER>"
  sensitive   = true
}

variable "db_admin_password" {
  description = "PostgreSQL administrator password"
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT signing secret"
  type        = string
  sensitive   = true
}

locals {
  prefix = "skishop"
  tags = {
    project     = "skishop"
    environment = var.environment
    managed_by  = "terraform"
  }

  # PostgreSQL databases
  postgres_services = {
    auth    = { db_name = "skishop_auth", sku = "B_Standard_B1ms" }
    user    = { db_name = "skishop_users", sku = "B_Standard_B1ms" }
    sales   = { db_name = "skishop_sales", sku = "B_Standard_B1ms" }
    payment = { db_name = "skishop_payment", sku = "B_Standard_B1ms" }
    point   = { db_name = "point_db", sku = "B_Standard_B1ms" }
    coupon  = { db_name = "coupon_db", sku = "B_Standard_B1ms" }
  }

  # Container Apps
  app_services = {
    "auth-svc"      = { image = "authentication-service", port = 8080, min_replicas = 1, max_replicas = 3 }
    "user-svc"      = { image = "user-management-service", port = 8081, min_replicas = 1, max_replicas = 3 }
    "inventory-svc" = { image = "inventory-management-service", port = 8082, min_replicas = 1, max_replicas = 3 }
    "sales-svc"     = { image = "sales-management-service", port = 8083, min_replicas = 1, max_replicas = 3 }
    "payment-svc"   = { image = "payment-cart-service", port = 8084, min_replicas = 1, max_replicas = 3 }
    "point-svc"     = { image = "point-service", port = 8085, min_replicas = 1, max_replicas = 3 }
    "coupon-svc"    = { image = "coupon-service", port = 8088, min_replicas = 1, max_replicas = 3 }
    "ai-svc"        = { image = "ai-support-service", port = 8087, min_replicas = 1, max_replicas = 2 }
    "gateway-svc"   = { image = "api-gateway-service", port = 8090, min_replicas = 2, max_replicas = 5 }
  }
}

# =============================================================================
# Resource Group
# =============================================================================

resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location
  tags     = local.tags
}

# =============================================================================
# Container Apps Environment
# =============================================================================

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${local.prefix}-logs"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "main" {
  name                       = "${local.prefix}-cae"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = local.tags
}

# =============================================================================
# Key Vault
# =============================================================================

data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "main" {
  name                       = "${local.prefix}-kv"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  purge_protection_enabled   = false

  tags = local.tags
}

resource "azurerm_key_vault_secret" "jwt_secret" {
  name         = "jwt-secret"
  value        = var.jwt_secret
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "db_password" {
  name         = "db-admin-password"
  value        = var.db_admin_password
  key_vault_id = azurerm_key_vault.main.id
}

# =============================================================================
# Azure PostgreSQL Flexible Server × 6
# =============================================================================

resource "azurerm_postgresql_flexible_server" "db" {
  for_each = local.postgres_services

  name                          = "${local.prefix}-${each.key}-db"
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  version                       = "16"
  administrator_login           = var.db_admin_username
  administrator_password        = var.db_admin_password
  sku_name                      = each.value.sku
  storage_mb                    = 32768
  backup_retention_days         = 7
  geo_redundant_backup_enabled  = false
  public_network_access_enabled = true

  tags = local.tags
}

resource "azurerm_postgresql_flexible_server_database" "db" {
  for_each = local.postgres_services

  name      = each.value.db_name
  server_id = azurerm_postgresql_flexible_server.db[each.key].id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# Allow Azure services to connect
resource "azurerm_postgresql_flexible_server_firewall_rule" "allow_azure" {
  for_each = local.postgres_services

  name             = "AllowAzureServices"
  server_id        = azurerm_postgresql_flexible_server.db[each.key].id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

# =============================================================================
# Azure Cosmos DB for MongoDB vCore
# =============================================================================

resource "azurerm_cosmosdb_account" "mongo" {
  name                = "${local.prefix}-mongo"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  offer_type          = "Standard"
  kind                = "MongoDB"

  capabilities {
    name = "EnableMongo"
  }

  consistency_policy {
    consistency_level = "Session"
  }

  geo_location {
    location          = azurerm_resource_group.main.location
    failover_priority = 0
  }

  tags = local.tags
}

resource "azurerm_cosmosdb_mongo_database" "inventory" {
  name                = "skishop_inventory"
  resource_group_name = azurerm_resource_group.main.name
  account_name        = azurerm_cosmosdb_account.mongo.name
}

resource "azurerm_cosmosdb_mongo_database" "ai" {
  name                = "skishop_ai"
  resource_group_name = azurerm_resource_group.main.name
  account_name        = azurerm_cosmosdb_account.mongo.name
}

# =============================================================================
# Azure Event Hubs (Kafka protocol compatible)
# =============================================================================

resource "azurerm_eventhub_namespace" "main" {
  name                = "${local.prefix}-eventhub"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "Standard"
  capacity            = 1
  auto_inflate_enabled   = true
  maximum_throughput_units = 4

  tags = local.tags
}

# Event Hub topics (one per service)
resource "azurerm_eventhub" "service_events" {
  for_each = toset([
    "skishop-auth-events",
    "skishop-user-events",
    "skishop-sales-events",
    "skishop-payment-events",
    "skishop-inventory-events",
    "skishop-point-events",
    "skishop-coupon-events",
    "skishop-ai-events",
  ])

  name              = each.value
  namespace_id      = azurerm_eventhub_namespace.main.id
  partition_count   = 4
  message_retention = 7
}

# =============================================================================
# Azure Container Registry
# =============================================================================

resource "azurerm_container_registry" "main" {
  name                = "${local.prefix}cr"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Standard"
  admin_enabled       = false

  tags = local.tags
}

# =============================================================================
# Container Apps (placeholder — images set by deploy workflow)
# =============================================================================

resource "azurerm_container_app" "services" {
  for_each = local.app_services

  name                         = each.key
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"

  tags = local.tags

  template {
    min_replicas = each.value.min_replicas
    max_replicas = each.value.max_replicas

    container {
      name   = each.key
      image  = "mcr.microsoft.com/k8se/quickstart:latest" # placeholder
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "JWT_SECRET"
        value = var.jwt_secret
      }
    }
  }

  ingress {
    external_traffic_weight {
      latest_revision = true
      percentage      = 100
    }
    target_port     = each.value.port
    external_enabled = each.key == "gateway-svc" ? true : false
  }
}

# =============================================================================
# Outputs
# =============================================================================

output "container_apps_environment_id" {
  value = azurerm_container_app_environment.main.id
}

output "key_vault_uri" {
  value = azurerm_key_vault.main.vault_uri
}

output "postgres_servers" {
  value = {
    for k, v in azurerm_postgresql_flexible_server.db :
    k => v.fqdn
  }
}

output "cosmos_mongo_connection" {
  value     = azurerm_cosmosdb_account.mongo.primary_mongodb_connection_string
  sensitive = true
}

output "eventhub_namespace" {
  value = azurerm_eventhub_namespace.main.name
}

output "gateway_url" {
  value = azurerm_container_app.services["gateway-svc"].ingress[0].fqdn
}
