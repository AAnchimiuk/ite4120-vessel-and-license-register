# Generate an interactive SchemaSpy HTML visualization for the `vessels` schema only.
# Requires the local PostgreSQL container (`ite4120-postgres-1`, with Liquibase migrations applied).
# Output: docs\schemaspy\index.html
# Usage: .\scripts\run-schemaspy.ps1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $Root 'docs\schemaspy'

$running = docker inspect -f '{{.State.Running}}' ite4120-postgres-1 2>$null
if ($running -ne 'true') {
    Write-Error "PostgreSQL container (ite4120-postgres-1) is not running. Run .\scripts\run-backend.ps1 once first."
    exit 1
}

if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Path $OutDir | Out-Null

$dbUser = if ($env:ANIMALS_DB_USER) { $env:ANIMALS_DB_USER } else { 'animals' }
$dbPass = if ($env:ANIMALS_DB_PASSWORD) { $env:ANIMALS_DB_PASSWORD } else { 'animals' }

Write-Host "Running SchemaSpy for schema 'vessels'..."
docker run --rm `
  --network container:ite4120-postgres-1 `
  -v "${OutDir}:/output" `
  schemaspy/schemaspy:latest `
  -t pgsql11 `
  -host localhost `
  -port 5432 `
  -db animals `
  -u $dbUser `
  -p $dbPass `
  -s vessels

$indexHtml = Join-Path $OutDir 'index.html'
Write-Host "SchemaSpy report generated at: $indexHtml"
Start-Process $indexHtml
