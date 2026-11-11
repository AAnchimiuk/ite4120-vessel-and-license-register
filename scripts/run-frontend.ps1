# Start the frontend dev server (Vite, port 18640).
# On a fresh clone the dependencies are installed first — without them the
# failure is the cryptic `vite: command not found`.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location "$Root/frontend"

if (-not (Test-Path "node_modules/.bin/vite")) {
    Write-Host "frontend dependencies are not installed yet - running npm ci (first time only)..."
    Write-Host "(a 401 here means your GitHub token is missing: README section 'GitHub token')"
    npm ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

npm run dev
