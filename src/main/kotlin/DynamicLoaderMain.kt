package mcpatch

import java.awt.Desktop
import java.io.File
import java.lang.instrument.Instrumentation
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import javax.swing.JOptionPane

object DynamicLoaderMain
{
    @JvmStatic
    fun main(args: Array<String>) {
        execute(args.firstOrNull(), null)
    }

    @JvmStatic
    fun premain(agentArgs: String?, ins: Instrumentation?)
    {
        execute(agentArgs, ins)
    }

    fun execute(agentArgs: String?, ins: Instrumentation?)
    {
        try {
            val jarFile = getJarFile() ?: throw BusinessException("当前环境可能为开发环境中，无法获取Jar文件所在路径，启动失败")
            val dynamicLoaderFile = File(jarFile.parent, "mc-patch-dynamic-loader.txt")

            if (!dynamicLoaderFile.exists())
                dynamicLoaderFile.createNewFile()

            val content = dynamicLoaderFile.readLines()

            if (content.isEmpty())
                throw BusinessException("${dynamicLoaderFile.name} 文件内容为空，请在这个文件中指定要启动的McPatchClient的Jar文件名，启动失败")

            val coreFile = content.map { File(jarFile.parent, it.trim()) }.firstOrNull { it.exists() }
                ?: throw BusinessException("${dynamicLoaderFile.name} 文件中指定的要启动的McPatchClient的Jar文件名不存在，启动失败")

            URLClassLoader(arrayOf(coreFile.toURI().toURL())).use { classloader ->
                val main = classloader.loadClass("mcpatch.McPatchClient")
                val premain = main.getMethod("premain", String::class.java, Instrumentation::class.java)

                premain.invoke(null, agentArgs, ins)
            }
        } catch (e: BusinessException) {
            if (Desktop.isDesktopSupported())
                JOptionPane.showMessageDialog(null, e.message, "McPatchDynamicLoader 启动失败", JOptionPane.ERROR_MESSAGE)

            throw e
        }
    }

    fun getJarFile(): File?
    {
        if (javaClass.getResource("")?.protocol == "file")
            return null

        val url = URLDecoder.decode(javaClass.protectionDomain.codeSource.location.file, "UTF-8").replace("\\", "/")

        return File(if (url.endsWith(".class") && "!" in url) {
            val path = url.substring(0, url.lastIndexOf("!"))
            if ("file:/" in path) path.substring(path.indexOf("file:/") + "file:/".length) else path
        } else url)
    }

    class BusinessException(reason: String) : RuntimeException(reason)
}