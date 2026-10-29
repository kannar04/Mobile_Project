[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$OutputDirectory = "build/java-beautified-copy-aosp",
    [string]$FormatterVersion = "1.37.0",
    [ValidateSet("AOSP", "Google")]
    [string]$Style = "AOSP"
)

$ErrorActionPreference = "Stop"
$project = [System.IO.Path]::GetFullPath($ProjectRoot)
$sourceRoot = [System.IO.Path]::GetFullPath((Join-Path $project "app/src"))
$output = [System.IO.Path]::GetFullPath((Join-Path $project $OutputDirectory))
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $project "build"))

if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "Java source root was not found: $sourceRoot"
}
if (-not $output.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar)) {
    throw "Output must stay under the project's build directory."
}
if ($output.StartsWith($sourceRoot + [System.IO.Path]::DirectorySeparatorChar)) {
    throw "Output cannot be inside the source tree."
}

$javaFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter "*.java" | Sort-Object FullName)
if ($javaFiles.Count -eq 0) { throw "No Java files found under $sourceRoot" }

$before = @{}
foreach ($file in $javaFiles) {
    $relative = $file.FullName.Substring($project.Length).TrimStart('\', '/')
    $before[$relative] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
}

if (Test-Path -LiteralPath $output) {
    $resolved = (Resolve-Path -LiteralPath $output).Path
    if (-not $resolved.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar)) {
        throw "Refusing to clear output outside build: $resolved"
    }
    try {
        [System.IO.Directory]::Delete($resolved, $true)
    } catch [System.IO.IOException] {
        # IDE indexers can hold the comparison directory open on Windows. Every
        # mirrored Java file is overwritten below, so continuing is safe.
        Write-Warning "Output directory is being indexed; overwriting its Java copies in place."
    }
}
[System.IO.Directory]::CreateDirectory($output) | Out-Null

$copies = New-Object System.Collections.Generic.List[string]
foreach ($file in $javaFiles) {
    $relative = $file.FullName.Substring($project.Length).TrimStart('\', '/')
    $destination = Join-Path $output $relative
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
    [System.IO.File]::Copy($file.FullName, $destination, $true)
    $copies.Add($destination)
}

$toolDirectory = Join-Path $buildRoot "formatter-tools"
[System.IO.Directory]::CreateDirectory($toolDirectory) | Out-Null
$jar = Join-Path $toolDirectory "google-java-format-$FormatterVersion-all-deps.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    $url = "https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/$FormatterVersion/google-java-format-$FormatterVersion-all-deps.jar"
    Invoke-WebRequest -Uri $url -OutFile $jar -UseBasicParsing
}
if ((Get-Item -LiteralPath $jar).Length -lt 1000000) { throw "Formatter download is incomplete." }
$styleArguments = @()
if ($Style -eq "AOSP") { $styleArguments += "--aosp" }

for ($offset = 0; $offset -lt $copies.Count; $offset += 40) {
    $last = [Math]::Min($offset + 39, $copies.Count - 1)
    $batch = @($copies[$offset..$last])
    $arguments = @("-jar", $jar) + $styleArguments + @("--replace") + $batch
    & java @arguments
    if ($LASTEXITCODE -ne 0) { throw "google-java-format failed for copy batch starting at $offset" }
    # Import cleanup can expose additional wrapping in dense legacy files.
    & java @arguments
    if ($LASTEXITCODE -ne 0) { throw "google-java-format second pass failed for copy batch starting at $offset" }
}

# A second, bounded dry run proves the formatted copy is stable and needs no further edits.
for ($offset = 0; $offset -lt $copies.Count; $offset += 40) {
    $last = [Math]::Min($offset + 39, $copies.Count - 1)
    $batch = @($copies[$offset..$last])
    $arguments = @("-jar", $jar) + $styleArguments + @("--dry-run", "--set-exit-if-changed") + $batch
    & java @arguments
    if ($LASTEXITCODE -ne 0) { throw "Formatted copy is not idempotent at batch $offset." }
}

$sourceChanged = New-Object System.Collections.Generic.List[string]
$formattedCount = 0
$unchangedCount = 0
foreach ($file in $javaFiles) {
    $relative = $file.FullName.Substring($project.Length).TrimStart('\', '/')
    $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    if ($sourceHash -ne $before[$relative]) { $sourceChanged.Add($relative) }
    $copyHash = (Get-FileHash -LiteralPath (Join-Path $output $relative) -Algorithm SHA256).Hash
    if ($copyHash -eq $sourceHash) { $unchangedCount++ } else { $formattedCount++ }
}
if ($sourceChanged.Count -ne 0) {
    throw "Original source changed unexpectedly: $($sourceChanged -join ', ')"
}

$report = Join-Path $output "BEAUTIFY_REPORT.md"
@"
# Java beautification copy report

- Formatter: google-java-format $FormatterVersion
- Style: $Style
- Source: $sourceRoot
- Copy: $output
- Java files copied: $($javaFiles.Count)
- Files whose formatting changed: $formattedCount
- Files already formatted: $unchangedCount
- Original source SHA-256 verification: PASS
- Formatter idempotence check: PASS

The formatter ran only against the copied files under $OutputDirectory. No file under app/src was written by this script.
"@ | Set-Content -LiteralPath $report -Encoding UTF8

Write-Host "Beautified copy created: $output"
Write-Host "Changed formatting: $formattedCount / $($javaFiles.Count) files"
Write-Host "Original source integrity: PASS"
Write-Host "Report: $report"
