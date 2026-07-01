$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$javaHome = "D:\company\Program Files\Java\17"
if (!(Test-Path "$javaHome\bin\java.exe")) {
    throw "Java 17 not found: $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

mvn -q -DskipTests compile
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"

$classes = (Resolve-Path ".\target\classes").Path
$deps = (Get-Content ".\target\classpath.txt" -Raw).Trim()
$classpath = ($classes + ";" + $deps).Replace("\", "/")
$argFile = Join-Path (Resolve-Path ".\target") "java-args.txt"
$content = "-cp`r`n`"$classpath`"`r`ncom.fr.ai.debugagent.DebugAgentApplication`r`n"
[System.IO.File]::WriteAllText($argFile, $content, [System.Text.UTF8Encoding]::new($false))

Write-Host "Starting business-debug-agent on http://localhost:8080"
& "$javaHome\bin\java.exe" "@$argFile"