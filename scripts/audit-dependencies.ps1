param(
    [string]$DependencyTree = 'target/dependency-tree.json',
    [string]$Report = 'target/dependency-audit.json'
)

# Input: mvnw dependency:tree -DoutputType=json -DoutputFile=target/dependency-tree.json
# Sends only public package coordinates and versions to OSV, never project source/configuration.
$ErrorActionPreference = 'Stop'
$packages = @{}
function Add-Package($ecosystem, $name, $version) {
    $packages["${ecosystem}:${name}@${version}"] = @{
        package = @{ ecosystem = $ecosystem; name = $name }; version = $version
    }
}
function Visit-Dependency($node) {
    Add-Package 'Maven' "$($node.groupId):$($node.artifactId)" $node.version
    # WebJar wrappers also need lookup under the upstream npm coordinates.
    if ($node.groupId -eq 'org.webjars.npm') {
        $name = $node.artifactId
        if ($name.Contains('__')) { $name = '@' + $name.Replace('__', '/') }
        Add-Package 'npm' $name $node.version
    } elseif ($node.groupId -eq 'org.webjars' -and $node.artifactId -in @('jquery', 'bootstrap', 'jstree')) {
        Add-Package 'npm' $node.artifactId $node.version
    }
    foreach ($child in $node.children) { Visit-Dependency $child }
}
$tree = Get-Content -LiteralPath $DependencyTree -Raw | ConvertFrom-Json
foreach ($child in $tree.children) { Visit-Dependency $child }
$queries = @($packages.GetEnumerator() | Sort-Object Name | ForEach-Object { $_.Value })
$findings = @()
for ($offset = 0; $offset -lt $queries.Count; $offset += 100) {
    $batch = @($queries[$offset..([Math]::Min($offset + 99, $queries.Count - 1))])
    $response = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' `
        -Body (@{ queries = $batch } | ConvertTo-Json -Depth 10) -TimeoutSec 60
    if ($response.results.Count -ne $batch.Count) { throw 'Incomplete OSV response' }
    for ($index = 0; $index -lt $batch.Count; $index++) {
        $result = $response.results[$index]
        if ($result.error) { throw "OSV query failed: $($result.error)" }
        $ids = @($result.vulns | ForEach-Object { $_.id })
        while ($result.next_page_token) {
            $next = $batch[$index].Clone()
            $next.page_token = $result.next_page_token
            $page = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' `
                -Body (@{ queries = @($next) } | ConvertTo-Json -Depth 10) -TimeoutSec 60
            if ($page.results.Count -ne 1) { throw 'Incomplete OSV pagination response' }
            $result = $page.results[0]
            if ($result.error) { throw "OSV pagination failed: $($result.error)" }
            $ids += @($result.vulns | ForEach-Object { $_.id })
        }
        foreach ($id in ($ids | Sort-Object -Unique)) {
            $advisory = Invoke-RestMethod -Uri "https://api.osv.dev/v1/vulns/$id" -TimeoutSec 60
            $findings += @{ query = $batch[$index]; advisory = $advisory }
            Write-Output "$($batch[$index].package.name) $($batch[$index].version): $id - $($advisory.summary)"
        }
    }
}
@{
    checkedAt = [DateTime]::UtcNow.ToString('o')
    source = 'https://api.osv.dev'
    dependencyTreeSha256 = (Get-FileHash -LiteralPath $DependencyTree -Algorithm SHA256).Hash
    queries = $queries
    findings = $findings
} | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $Report -Encoding UTF8
Write-Output "Checked $($queries.Count) package versions; $($findings.Count) package/advisory matches. Report: $Report"
if ($findings.Count -gt 0) { exit 1 }
