##这个项目

1. 只支持windows
2. 完成的功能是拷贝latex代码，并转换成`![]()`格式的可插入代码，方便在github中使用
3. 支持热键启动/关闭监控剪切板

##使用方法：

1. 将lib下的两个DLL文件放到`C:\Windows\System32`目录下
2. 在com.melloware.jintellitype.JIntellitype类中第95行左右，找到

		private JIntellitype() {
			try {
			 // Load JNI library
			 System.loadLibrary("JIntellitype64");
			} catch (Throwable exLoadLibrary) {
			 //omit
			}
			
			initializeLibrary();
			this.keycodeMap = getKey2KeycodeMapping();
		}
视自己的操作系统选择填写`System.loadLibrary("JIntellitype64");`或`System.loadLibrary("JIntellitype");`

3. 运行xu.tools.LatexClip

之后就可以使用默认快捷键`ALT+SHIFT+B`开启监控剪切板，`CTRL+SHIFT+C`关闭剪切板。

以上。