param(
    [string]$Schema = "identity_service",
    [string]$PostgresHostName = "postgres",
    [int]$PostgresPort = 5432,
    [string]$PostgresDb = "cybersim_it",
    [string]$PostgresAdminUser = "cybersim",
    [string]$PostgresUser = "cybersim_it",
    [string]$PostgresPassword = "cybersim_it_password",
    [string]$DockerNetwork = "cyber-defence-sim_default"
)

$ErrorActionPreference = "Stop"
if ($Schema -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$') {
    throw "Schema must be a simple PostgreSQL identifier."
}
if ($PostgresAdminUser -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$') {
    throw "PostgresAdminUser must be a simple PostgreSQL identifier."
}
if ($PostgresUser -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$') {
    throw "PostgresUser must be a simple PostgreSQL identifier."
}
if ($PostgresDb -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$') {
    throw "PostgresDb must be a simple PostgreSQL identifier."
}

$previousHost = $env:POSTGRES_HOST
$previousPort = $env:POSTGRES_PORT
$previousDb = $env:POSTGRES_DB
$previousUser = $env:POSTGRES_USER
$previousPassword = $env:POSTGRES_PASSWORD
$previousSchema = $env:POSTGRES_SCHEMA
Push-Location (Join-Path $PSScriptRoot "..")
try {
    docker compose up -d postgres

    $deadline = (Get-Date).AddSeconds(90)
    do {
        $status = docker compose ps postgres --format json | ConvertFrom-Json
        if ($status.Health -eq "healthy") {
            break
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    $status = docker compose ps postgres --format json | ConvertFrom-Json
    if ($status.Health -ne "healthy") {
        docker compose logs --tail=120 postgres
        throw "PostgreSQL did not become healthy before timeout."
    }

    $escapedRole = $PostgresUser.Replace("'", "''")
    $escapedPassword = $PostgresPassword.Replace("'", "''")
    $escapedDb = $PostgresDb.Replace("'", "''")
    $roleSql = "DO " + '$$' + " BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$escapedRole') THEN CREATE ROLE $PostgresUser LOGIN PASSWORD '$escapedPassword'; ELSE ALTER ROLE $PostgresUser WITH LOGIN PASSWORD '$escapedPassword'; END IF; END " + '$$' + ";"
    docker compose exec -T postgres psql -U $PostgresAdminUser -d postgres -v ON_ERROR_STOP=1 -c $roleSql

    docker compose exec -T postgres psql -U $PostgresAdminUser -d postgres -v ON_ERROR_STOP=1 `
        -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$escapedDb';"
    docker compose exec -T postgres psql -U $PostgresAdminUser -d postgres -v ON_ERROR_STOP=1 `
        -c "DROP DATABASE IF EXISTS $PostgresDb;"
    docker compose exec -T postgres psql -U $PostgresAdminUser -d postgres -v ON_ERROR_STOP=1 `
        -c "CREATE DATABASE $PostgresDb OWNER $PostgresUser;"
    docker compose exec -T postgres psql -U $PostgresAdminUser -d postgres -v ON_ERROR_STOP=1 `
        -c "GRANT ALL PRIVILEGES ON DATABASE $PostgresDb TO $PostgresUser;"

    $env:POSTGRES_HOST = $PostgresHostName
    $env:POSTGRES_PORT = "$PostgresPort"
    $env:POSTGRES_DB = $PostgresDb
    $env:POSTGRES_USER = $PostgresUser
    $env:POSTGRES_PASSWORD = $PostgresPassword
    $env:POSTGRES_SCHEMA = $Schema

    $workspace = (Get-Location).Path
    $mavenCache = Join-Path $env:USERPROFILE ".m2"
    docker run --rm `
        --network $DockerNetwork `
        -v "${workspace}:/workspace" `
        -v "${mavenCache}:/root/.m2" `
        -w /workspace `
        maven:3.9.9-eclipse-temurin-21 `
        mvn -pl services/identity-service -am `
        "-Dtest=AuthControllerPostgresIT" `
        "-Dsurefire.failIfNoSpecifiedTests=false" `
        "-Dcybersim.postgres.it=true" `
        "-Dcybersim.postgres.host=$PostgresHostName" `
        "-Dcybersim.postgres.port=$PostgresPort" `
        "-Dcybersim.postgres.db=$PostgresDb" `
        "-Dcybersim.postgres.user=$PostgresUser" `
        "-Dcybersim.postgres.password=$PostgresPassword" `
        "-Dcybersim.postgres.schema=$Schema" `
        test
    if ($LASTEXITCODE -ne 0) {
        throw "Maven Postgres integration tests failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:POSTGRES_HOST = $previousHost
    $env:POSTGRES_PORT = $previousPort
    $env:POSTGRES_DB = $previousDb
    $env:POSTGRES_USER = $previousUser
    $env:POSTGRES_PASSWORD = $previousPassword
    $env:POSTGRES_SCHEMA = $previousSchema
    Pop-Location
}
