# IGV 中文汉化 · Windows 卸载
$ErrorActionPreference = "Stop"
$argsFile = if ($env:IGV_JAVA_ARGS) { $env:IGV_JAVA_ARGS } else { Join-Path $env:USERPROFILE ".igv\java_arguments" }
function ShowBox($msg) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show($msg, "IGV 中文汉化") | Out-Null
}
Write-Host "正在关闭汉化…"
if (Test-Path $argsFile) {
    Copy-Item $argsFile "$argsFile.bak.$(Get-Date -Format yyyyMMdd-HHmmss)"
    $lines = @(Get-Content $argsFile | Where-Object {
        $_ -notmatch "igv-(lingo|l10n)-agent\.jar" -and $_ -notmatch "^# IGV 多语言"
    })
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($argsFile, ($lines -join "`n") + "`n", $utf8)
    Write-Host "✅ 已移除加载行：$argsFile"
}
Write-Host "重启 IGV 后恢复英文。"
if ($env:IGV_LINGO_NO_DIALOG -ne "1") { ShowBox "汉化已关闭。`n`n请重启 IGV，界面会恢复英文。" }
