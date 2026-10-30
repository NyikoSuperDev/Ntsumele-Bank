$ErrorActionPreference = 'Stop'
foreach ($name in @('TEST_DATABASE_URL', 'TEST_DATABASE_USERNAME', 'TEST_DATABASE_PASSWORD')) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
    throw "Set $name for the dedicated PostgreSQL test database. See README.md."
  }
}
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
  Push-Location frontend
  try {
    npm ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
    npm test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
  } finally { Pop-Location }
  & ./mvnw.cmd -B -ntp -f backend/pom.xml -Ppostgres-integration verify
  if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
  Write-Output 'Set DB_URL, DB_USERNAME and DB_PASSWORD, then run: java -jar backend/target/ntsumele-bank-1.0.0.jar'
} finally { Pop-Location }
