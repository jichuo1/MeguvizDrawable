param([Parameter(Mandatory = $true)][string]$Path)
$ErrorActionPreference = 'Stop'
$files = @(Get-ChildItem -LiteralPath $Path -Filter 'fork-*.csv' | Sort-Object Name)
if ($files.Count -eq 0) { throw 'No fork files found.' }
Write-Output 'fork,direct_median_ns,lut_median_ns,median_paired_delta_percent,corner_median_ns'
foreach ($file in $files) {
    $samples = @(Get-Content -LiteralPath $file.FullName | Where-Object { $_ -like 'sample,*' } | ForEach-Object {
        $value = $_ -split ','
        [pscustomobject]@{ Round = [int]$value[2]; Scenario = $value[3]; Operations = [long]$value[4]; Elapsed = [long]$value[5]; Checksum = [long]$value[6] }
    })
    if ($samples.Count -ne 21) { throw "Unexpected sample count in $($file.Name)" }
    $direct = @(); $lut = @(); $corner = @(); $paired = @()
    foreach ($round in 0..6) {
        $d = @($samples | Where-Object { $_.Round -eq $round -and $_.Scenario -eq 'state_direct' })
        $l = @($samples | Where-Object { $_.Round -eq $round -and $_.Scenario -eq 'state_lut8' })
        $c = @($samples | Where-Object { $_.Round -eq $round -and $_.Scenario -eq 'corner_normalize' })
        if ($d.Count -ne 1 -or $l.Count -ne 1 -or $c.Count -ne 1 -or $d[0].Operations -ne $l[0].Operations -or $d[0].Checksum -ne $l[0].Checksum -or $d[0].Elapsed -le 0) { throw "Invalid pair in $($file.Name), round $round" }
        $direct += $d[0].Elapsed / $d[0].Operations
        $lut += $l[0].Elapsed / $l[0].Operations
        $corner += $c[0].Elapsed / $c[0].Operations
        $paired += 100 * (1 - $l[0].Elapsed / [double]$d[0].Elapsed)
    }
    $dMedian = @($direct | Sort-Object)[3]
    $lMedian = @($lut | Sort-Object)[3]
    $cMedian = @($corner | Sort-Object)[3]
    $pMedian = @($paired | Sort-Object)[3]
    [string]::Format([System.Globalization.CultureInfo]::InvariantCulture,
        '{0},{1:F3},{2:F3},{3:F3},{4:F3}', [object[]]@($file.BaseName, $dMedian, $lMedian, $pMedian, $cMedian))
}
