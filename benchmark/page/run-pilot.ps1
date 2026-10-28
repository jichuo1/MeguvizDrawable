param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Adb,
    [ValidateSet('pilot','confirmation')][string]$Phase = 'pilot',
    [switch]$UseMatchingInstalled
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$out = Join-Path $root "docs/engineering/evidence/runs/page-$Phase-$stamp"
$app = Join-Path $root 'benchmark/page/app/build/outputs/apk/release/app-release.apk'
$test = Join-Path $root 'benchmark/page/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk'
$candidateJar = Join-Path $root 'build/staging-maven/io/github/jichuo1/meguviz/meguviz-drawable-core/0.1.0-dev.3/meguviz-drawable-core-0.1.0-dev.3.jar'
$candidateAar = Join-Path $root 'build/staging-maven/io/github/jichuo1/meguviz/meguviz-drawable-android/0.1.0-dev.3/meguviz-drawable-android-0.1.0-dev.3.aar'
foreach ($required in @($app,$test,$candidateJar,$candidateAar)) {
    if (!(Test-Path -LiteralPath $required)) { throw 'Build both APKs and stage the candidate before running.' }
}
$old = @(& git -C $root show 'cb4e6ed:android/src/main/java/io/github/jichuo1/meguviz/drawable/android/RecipeDrawable.java')
if ($LASTEXITCODE -ne 0) { throw 'Cannot read baseline source from Git.' }
$expected = [string]::Join("`n", $old).Replace('package io.github.jichuo1.meguviz.drawable.android;', 'package io.github.jichuo1.meguviz.drawable.pagebench;').Replace('RecipeDrawable','BaselineRecipeDrawable').TrimEnd()
$actual = [IO.File]::ReadAllText((Join-Path $root 'benchmark/page/app/src/main/java/io/github/jichuo1/meguviz/drawable/pagebench/BaselineRecipeDrawable.java')).Replace("`r`n", "`n").TrimEnd()
if ($actual -cne $expected) { throw 'Baseline source differs from the frozen version apart from package/class rename.' }
if (Test-Path -LiteralPath $out) { throw 'Evidence destination already exists.' }
New-Item -ItemType Directory -Path $out | Out-Null
$status = & $Adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or $status.Trim() -ne 'device') { throw 'Selected device is unavailable.' }
$packages = @(& $Adb -s $Serial shell pm list packages)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect installed packages.' }
$targets = @(
    @{ Name='io.github.jichuo1.meguviz.drawable.pagebench'; Apk=$app; Label='app' },
    @{ Name='io.github.jichuo1.meguviz.drawable.pagebench.test'; Apk=$test; Label='test' }
)
if (!$UseMatchingInstalled) {
    foreach ($target in $targets) {
        if (@($packages | Where-Object { $_.Trim() -eq "package:$($target.Name)" }).Count -ne 0) {
            throw "Package $($target.Name) exists; refusing to replace it."
        }
    }
} else {
    foreach ($target in $targets) {
        if (@($packages | Where-Object { $_.Trim() -eq "package:$($target.Name)" }).Count -eq 0) {
            throw "Reuse requires both benchmark packages to be installed; missing $($target.Name)."
        }
    }
}
foreach ($target in $targets) {
    $present = @($packages | Where-Object { $_.Trim() -eq "package:$($target.Name)" }).Count -ne 0
    if ($present -and !$UseMatchingInstalled) { throw "Package $($target.Name) exists; refusing to replace it." }
    if (!$present) {
        & $Adb -s $Serial install $target.Apk *> (Join-Path $out "$($target.Label)-install.log")
        if ($LASTEXITCODE -ne 0) { throw "Install failed; see $($target.Label)-install.log." }
    }
    $remote = @(& $Adb -s $Serial shell pm path $target.Name | ForEach-Object { $_.Trim() } | Where-Object { $_.StartsWith('package:') })
    if ($LASTEXITCODE -ne 0 -or $remote.Count -ne 1) { throw "Unexpected installed package paths for $($target.Name)." }
    $installed = Join-Path $out "$($target.Label)-installed.apk"
    & $Adb -s $Serial pull ($remote[0].Substring(8)) $installed *> (Join-Path $out "$($target.Label)-pull.log")
    if ($LASTEXITCODE -ne 0) { throw "Cannot read back $($target.Name)." }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $target.Apk).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $installed).Hash) {
        throw "Installed bytes differ for $($target.Name); no package will be overwritten."
    }
}
$sourceFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $root 'benchmark/page/app/src'),(Join-Path $root 'core/src/main'),(Join-Path $root 'android/src/main') -Recurse -File | ForEach-Object FullName
) + @((Join-Path $root 'benchmark/page/app/build.gradle.kts'),(Join-Path $root 'benchmark/page/PROTOCOL.md'),
    (Join-Path $root 'benchmark/page/CONFIRMATION.md'),(Join-Path $root 'benchmark/page/run-pilot.ps1'),
    (Join-Path $root 'benchmark/page/analyze.py'))
$hashInputs = @(@($app,$test,$candidateJar,$candidateAar) + $sourceFiles | Sort-Object)
$beforeHashes = @(Get-FileHash -Algorithm SHA256 -LiteralPath $hashInputs | ForEach-Object { "$($_.Hash) $($_.Path)" })
$beforeHashes | Set-Content -LiteralPath (Join-Path $out 'sha256.txt') -Encoding utf8
$api = @(& $Adb -s $Serial shell getprop ro.build.version.sdk)
if ($LASTEXITCODE -ne 0 -or ($Phase -eq 'confirmation' -and ($api -join '').Trim() -ne '37')) { throw 'The required API 37 device is unavailable.' }
$api | Set-Content -LiteralPath (Join-Path $out 'api.txt') -Encoding utf8
& git -C $root rev-parse HEAD > (Join-Path $out 'source-head.txt')
function Check-Battery([string]$label) {
    $battery = @(& $Adb -s $Serial shell dumpsys battery)
    if ($LASTEXITCODE -ne 0) { throw 'Battery status unavailable.' }
    $battery | Set-Content -LiteralPath (Join-Path $out "battery-$label.txt") -Encoding utf8
    $joined = $battery -join "`n"
    $temperature = [regex]::Match($joined, '(?m)^\s*temperature:\s*(\d+)\s*$')
    $level = [regex]::Match($joined, '(?m)^\s*level:\s*(\d+)\s*$')
    if (!$temperature.Success -or !$level.Success -or [int]$temperature.Groups[1].Value -lt 150 -or
        [int]$temperature.Groups[1].Value -gt 400 -or [int]$level.Groups[1].Value -lt 20 -or
        $joined -notmatch '(?m)^\s*USB powered:\s*true\s*$') {
        throw "Battery conditions outside the registered bounds; see battery-$label.txt."
    }
}
$blocks = if ($Phase -eq 'confirmation') { 30 } else { 4 }
$order = @()
for ($block=0; $block -lt $blocks; $block++) {
    if (($block % 2) -eq 0) { $order += @('baseline','candidate') }
    else { $order += @('candidate','baseline') }
}
"phase=$Phase`nblocks=$blocks`norder=AB,BA`n" | Set-Content -LiteralPath (Join-Path $out 'protocol.txt') -Encoding utf8
Check-Battery 'before'
$pixelHash = $null
for ($i=0; $i -lt $order.Count; $i++) {
    $path = $order[$i]
    $log = Join-Path $out ('run-{0:D2}-{1}.log' -f ($i + 1),$path)
    & $Adb -s $Serial shell am instrument -w -r -e path $path 'io.github.jichuo1.meguviz.drawable.pagebench.test/io.github.jichuo1.meguviz.drawable.pagebench.PageInstrumentation' *> $log
    $code = $LASTEXITCODE
    $raw = Get-Content -LiteralPath $log -Raw
    if ($code -ne 0 -or $raw -notmatch "PAGEBENCH PASS path=$path" -or $raw -notmatch 'INSTRUMENTATION_CODE:\s*-1') {
        throw "Run $($i + 1) failed; preserve $log."
    }
    $header = [regex]::Match($raw, 'PAGEBENCH PASS path=(baseline|candidate) api=(\d+) refresh=([0-9.]+) pixel_hash=(-?\d+)')
    if (!$header.Success) { throw "Run $($i + 1) has no complete measurement header." }
    $refresh = [double]::Parse($header.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
    if ($Phase -eq 'confirmation' -and ([int]$header.Groups[2].Value -ne 37 -or $refresh -lt 119.5 -or $refresh -gt 120.5)) {
        throw "Run $($i + 1) has an unexpected API or refresh rate."
    }
    if ($null -eq $pixelHash) { $pixelHash = $header.Groups[4].Value }
    elseif ($pixelHash -cne $header.Groups[4].Value) { throw "Run $($i + 1) has a different preflight image." }
    Write-Host "Completed run $($i + 1)/$($order.Count): $path"
    if (($i % 2) -eq 1) {
        Check-Battery ('block-{0:D2}' -f (($i + 1) / 2))
        $intermediateHashes = @(Get-FileHash -Algorithm SHA256 -LiteralPath $hashInputs | ForEach-Object { "$($_.Hash) $($_.Path)" })
        if (Compare-Object $beforeHashes $intermediateHashes) { throw 'Inputs changed during measurement; preserve logs and stop.' }
    }
}
Check-Battery 'after'
$afterHashes = @(Get-FileHash -Algorithm SHA256 -LiteralPath $hashInputs | ForEach-Object { "$($_.Hash) $($_.Path)" })
if (Compare-Object $beforeHashes $afterHashes) { throw 'Inputs changed during measurement; preserve logs and mark this campaign invalid.' }
Write-Host "$Phase raw results: $out"
