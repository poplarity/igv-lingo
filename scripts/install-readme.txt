IGV 中文汉化 · 使用说明
=====================================

这个文件夹里装的是 IGV（Integrative Genomics Viewer）的中文界面补丁。
不需要安装任何软件，也不需要懂编程，跟着下面做就行。


一、Mac 用户（macOS）
-------------------------------------
1. 双击 install-mac.command

   如果系统提示“无法打开，因为来自身份不明的开发者”：
   右键点它 → 选“打开” → 再点“打开”。

2. 等窗口里出现“安装完成 🎉”

3. 完全退出 IGV：在 IGV 窗口按 Command + Q
   （只关窗口不算退出，一定要 Command + Q）

4. 重新打开 IGV，菜单栏应该变成：
   文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助


二、Windows 用户
-------------------------------------
1. 双击 install-windows.bat

   如果系统弹出“Windows 已保护你的电脑”：
   点“更多信息” → “仍要运行”。

2. 等弹出“安装完成”的提示

3. 完全退出 IGV（菜单 File → Exit，或在任务栏右键关闭）

4. 重新打开 IGV，菜单栏应该变成中文


三、不想要了，怎么恢复英文
-------------------------------------
Mac：     双击 uninstall-mac.command
Windows： 双击 uninstall-windows.bat

然后重启 IGV 就恢复英文了。


四、没生效怎么办
-------------------------------------
1. 确认是真的退出了 IGV（Mac 用 Command+Q），而不是只关了窗口
2. 看日志文件：
   Mac：     ~/.igv/lingo/igv-lingo.log
   Windows： C:\Users\你的用户名\.igv\lingo\igv-lingo.log
3. 如果日志里写着“没有可用语言包”，说明文件没复制全，重新解压一次
4. 还是不行，把日志内容发到项目的 Issues：
   https://github.com/poplarity/igv-lingo/issues


五、这东西会改我的 IGV 吗？
-------------------------------------
不会。IGV 本身的文件一个都没动。
它只是在你自己的用户目录（~/.igv/lingo）放了一份词表，
并在 ~/.igv/java_arguments 里加了一行，让 IGV 启动时读这份词表。

想随时彻底删除：删掉 ~/.igv/lingo 文件夹，并删掉 ~/.igv/java_arguments 里那行
-javaagent 即可。安装脚本在修改前都会自动备份 java_arguments。
