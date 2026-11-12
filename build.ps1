# Builds SpearHelper without Gradle (Windows PowerShell).
#
# Needs JDK 25 and a "libs" folder with the jars to compile against:
#   libs\<game>\minecraft-<game>-client.jar   official client jar of each game version (26.1, 26.1.1, 26.1.2, 26.2, 26.3)
#   libs\fabric-api-<api>\*.jar               fabric-api-base, fabric-key-mapping-api-v1, fabric-lifecycle-events-v1, fabric-networking-api-v1
#                                             (nested jars from META-INF/jars of the Fabric API jar; api = 26.1.2, 26.2, 26.3)
#   libs\common\*.jar                         fabric-loader, slf4j-api, lwjgl, lwjgl-glfw, jspecify, fastutil, guava,
#                                             datafixerupper, brigadier, logging, netty, joml, gson (from the launcher libraries)
#
# Usage: .\build.ps1 -Jdk "C:\path\to\jdk-25\bin" -Libs ".\libs"
param(
    [Parameter(Mandatory = $true)][string]$Jdk,
    [string]$Libs = "$PSScriptRoot\libs",
    [string]$Version = '1.1.0'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

$targets = [ordered]@{
    '26.1.x' = @{ games = @('26.1', '26.1.1', '26.1.2'); api = '26.1.2'; dep = '>=26.1 <26.2'; loader = '>=0.18.4' }
    '26.2'   = @{ games = @('26.2'); api = '26.2'; dep = '~26.2'; loader = '>=0.18.4' }
    '26.3'   = @{ games = @('26.3'); api = '26.3'; dep = '~26.3'; loader = '>=0.19.3' }
}

$common = @(Get-ChildItem "$Libs\common" -Filter *.jar | ForEach-Object FullName)
New-Item -ItemType Directory -Force "$root\dist" | Out-Null

foreach ($name in $targets.Keys) {
    $t = $targets[$name]
    $api = @(Get-ChildItem "$Libs\fabric-api-$($t.api)" -Filter *.jar | ForEach-Object FullName)
    $work = Join-Path ([IO.Path]::GetTempPath()) "spearhelper-$name"
    foreach ($g in $t.games) {
        if (Test-Path $work) { Remove-Item -Recurse -Force $work }
        New-Item -ItemType Directory "$work\classes" | Out-Null
        $cp = (@("$Libs\$g\minecraft-$g-client.jar") + $common + $api) -join ';'
        & "$Jdk\javac.exe" -proc:none -encoding UTF-8 --release 25 -cp $cp -d "$work\classes" "$root\src\com\s0ftpulse\client\SpearHelperClient.java"
        if ($LASTEXITCODE -ne 0) { throw "javac failed for $g" }
        "compiled against $g"
    }
    Copy-Item -Recurse "$root\resources\*" "$work\classes" -Force
    $mod = [IO.File]::ReadAllText("$root\fabric.mod.json.template").Replace('@VERSION@', "$Version+$name").Replace('@MINECRAFT@', $t.dep).Replace('@LOADER@', $t.loader)
    [IO.File]::WriteAllText("$work\classes\fabric.mod.json", $mod, [Text.UTF8Encoding]::new($false))
    $manifest = "Manifest-Version: 1.0`r`nFabric-Mapping-Namespace: official`r`nFabric-Minecraft-Version: $($t.games[-1])`r`n"
    [IO.File]::WriteAllText("$work\MANIFEST.MF", $manifest, [Text.UTF8Encoding]::new($false))
    $out = "$root\dist\spearhelper-$Version+$name.jar"
    if (Test-Path $out) { Remove-Item $out }
    & "$Jdk\jar.exe" cfm $out "$work\MANIFEST.MF" -C "$work\classes" .
    if ($LASTEXITCODE -ne 0) { throw "jar failed for $name" }
    Get-Item $out | ForEach-Object { "$($_.Name) $($_.Length) bytes" }
}
