# IGV 中文汉化 · Windows 安装
# 由 install-windows.bat 调用；也可以右键「使用 PowerShell 运行」
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = if ($env:IGV_LINGO_HOME) { $env:IGV_LINGO_HOME } else { Join-Path $env:USERPROFILE ".igv\lingo" }
$argsFile = if ($env:IGV_JAVA_ARGS) { $env:IGV_JAVA_ARGS } else { Join-Path $env:USERPROFILE ".igv\java_arguments" }

function ShowBox($msg) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show($msg, "IGV 中文汉化") | Out-Null
}
function Fail($msg) {
    Write-Host "❌ $msg" -ForegroundColor Red
    if ($env:IGV_LINGO_NO_DIALOG -ne "1") { ShowBox "安装失败：$msg" }
    exit 1
}

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  IGV 中文汉化 · 安装" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

if (-not (Test-Path "$here\igv-lingo-agent.jar")) { Fail "安装包不完整：缺少 igv-lingo-agent.jar" }
if (-not (Test-Path "$here\deny_by_class.tsv")) { Fail "安装包不完整：缺少 deny_by_class.tsv" }

Write-Host "安装到：$dest"
New-Item -ItemType Directory -Force -Path (Join-Path $dest "locales") | Out-Null
Copy-Item "$here\igv-lingo-agent.jar" $dest -Force
Copy-Item "$here\deny_by_class.tsv" $dest -Force
Get-ChildItem "$here\locales" -Directory | ForEach-Object {
    $target = Join-Path $dest "locales\$($_.Name)"
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Copy-Item (Join-Path $_.FullName "strings.tsv") $target -Force
    Write-Host "找到语言包：$($_.Name)"
}
Write-Host "✅ 汉化文件已复制" -ForegroundColor Green

New-Item -ItemType Directory -Force -Path (Split-Path $argsFile) | Out-Null
if (Test-Path $argsFile) {
    Copy-Item $argsFile "$argsFile.bak.$(Get-Date -Format yyyyMMdd-HHmmss)"
}
$lines = @()
if (Test-Path $argsFile) {
    $lines = @(Get-Content $argsFile | Where-Object {
        $_ -notmatch "igv-(lingo|l10n)-agent\.jar" -and $_ -notmatch "^# IGV 多语言"
    })
}
$lines += ""
$lines += "# IGV 多语言（igv-lingo）：删掉这一行即可关闭"
# Java argument files treat backslashes inside quotes as escape characters.
# Keep quotes for paths containing spaces, but use forward slashes for Java.
$javaDest = $dest.Replace('\', '/')
$lines += "`"-javaagent:$javaDest/igv-lingo-agent.jar=$javaDest`""
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($argsFile, ($lines -join "`n") + "`n", $utf8)
Write-Host "✅ 已让 IGV 启动时加载汉化" -ForegroundColor Green

Write-Host ""
Write-Host "最后一步：完全退出 IGV，再重新打开。" -ForegroundColor Yellow
Write-Host "菜单栏会变成：文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助"
if ($env:IGV_LINGO_NO_DIALOG -ne "1") {
    ShowBox "安装完成 ✅`n`n现在请完全退出 IGV，再重新打开。`n`n菜单栏会变成中文。"
}
exit 0
