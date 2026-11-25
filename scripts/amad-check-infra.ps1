# Phase 0 acceptance check: every FinLakeAmad infrastructure service is healthy and reachable.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$failed = 0
function Check([string]$name, [scriptblock]$probe) {
    try { & $probe | Out-Null; Write-Host "[OK]   $name" }
    catch { Write-Host "[FAIL] $name -> $($_.Exception.Message)"; $script:failed++ }
}

$expected = 'amad-mssql','amad-postgres','amad-minio','amad-metastore-db','amad-metastore','amad-trino'
foreach ($svc in $expected) {
    Check "$svc healthy" {
        $s = docker inspect -f '{{.State.Health.Status}}' $svc
        if ($s -ne 'healthy') { throw "status=$s" }
    }
}
Check 'amad-minio-init completed' {
    $code = docker inspect -f '{{.State.ExitCode}}' amad-minio-init
    if ($code -ne '0') { throw "exit=$code" }
}
Check 'Trino sees catalog amad_lake' {
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $out = (docker exec -e TERM=dumb amad-trino trino --execute 'SHOW CATALOGS' 2>&1) -join "`n"
    $code = $LASTEXITCODE; $ErrorActionPreference = $prev
    if ($code -ne 0 -or $out -notmatch 'amad_lake') { throw "code=$code catalogs: $out" }
}
Check 'Trino -> Metastore round trip (SHOW SCHEMAS)' {
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $out = (docker exec -e TERM=dumb amad-trino trino --execute 'SHOW SCHEMAS FROM amad_lake' 2>&1) -join "`n"
    $code = $LASTEXITCODE; $ErrorActionPreference = $prev
    if ($code -ne 0 -or $out -notmatch 'default') { throw "code=$code out: $out" }
}

if ($failed) { Write-Host "$failed check(s) failed"; exit 1 } else { Write-Host 'Phase 0 infrastructure OK' }
