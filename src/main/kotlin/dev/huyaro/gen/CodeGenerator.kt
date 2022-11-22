package dev.huyaro.gen

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.util.io.exists
import dev.huyaro.gen.meta.Table
import dev.huyaro.gen.model.FileMode
import dev.huyaro.gen.model.FileType
import dev.huyaro.gen.model.GeneratorOptions
import dev.huyaro.gen.util.VelocityTemplate
import dev.huyaro.gen.util.camelCase
import dev.huyaro.gen.util.trimAndSplit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.io.path.notExists

/**
 * @author yanghu
 * @date 2022-11-15
 * @description generate code with config
 */
class CodeGenerator
constructor(private val project: Project, private val options: GeneratorOptions, private var tables: List<Table>) {

    private val log = Logger.getInstance(CodeGenerator::class.java)

    private val pluginId = "dev.huyaro.gen.x"
    private val templatePath = "templates"
    private val fileTypeMapping = FileType.values().associateWith { it.name.lowercase() }

    private val templateEngine: VelocityTemplate = VelocityTemplate()

    fun generate(): String {
        val extensionsRootType = ExtensionsRootType.getInstance()
        val resource = extensionsRootType.findResourceDirectory(PluginId.findId(pluginId)!!, templatePath, false)
        val outLogs = StringBuilder()
        if (!resource.exists()) {
            outLogs.append("Resource director [${resource}] not exists!\n")
            return outLogs.toString()
        }

        var outFiles = listOf<Path>()
        this.tables = naming(tables)
        options.fileTypes.forEach {
            val templateFile = getTemplate(resource, it)
                .apply {
                    if (notExists()) {
                        val line = "Template File $this not Exists!\n"
                        log.error(line)
                        outLogs.append(line)
                    }
                }

            tables.forEach { tab ->
                getTargetFile(it, tab.className)
                    .let { fl ->
                        if (fl.exists()) {
                            val line: String
                            if (options.fileMode == FileMode.OVERWRITE) {
                                line = "Delete existing file ${fl.name}\n"
                                Files.delete(fl)
                            } else {
                                line = "Skip existing files [${fl.name}]\n"
                            }
                            log.warn(line)
                            outLogs.append(line)
                        }
                        if (fl.notExists()) {
                            val context = buildContext(it, tab)
                            var line = "Ready to render template [${templateFile}]\n"
                            log.warn(line)
                            outLogs.append(line)
                            // render template
                            templateEngine.render(fl, templateFile, context)
                            outFiles = outFiles.plusElement(fl)

                            line = "Generated File => [${fl}]\n"
                            log.warn(line)
                            outLogs.append(line)
                        }
                    }
            }
        }

        // format file
        if (outFiles.isNotEmpty()) {
            val psiManager = PsiManager.getInstance(project)
            val fileList = outFiles.map { VfsUtil.findFileByIoFile(it.toFile(), true) }.toList()
            val psiFiles = fileList.map { psiManager.findFile(it!!) }.toTypedArray()
            val processor = ReformatCodeProcessor(project, psiFiles, null, false)
            processor.run()
        }

        // out log to insert view
        return outLogs.toString()
    }

    /**
     * 获取模板文件
     */
    private fun getTemplate(resource: Path, fileType: FileType): Path {
        return resource.resolve(options.framework.name.lowercase())
            .resolve("${fileType.name.lowercase()}.${options.language.name.lowercase()}.vm")
    }

    /**
     * 获取输出的目标文件
     */
    private fun getTargetFile(fileType: FileType, fileName: String): Path {
        val pkgPath = options.rootPackage.replace(".", File.separator)
        val pkgName = fileTypeMapping[fileType]!!
        val fullPath = Paths.get(options.outputDir).resolve(pkgPath).resolve(pkgName)
        if (fullPath.notExists()) {
            Files.createDirectories(fullPath)
        }
        return fullPath.resolve("$fileName.${options.language.suffix}")
    }

    /**
     * 构建模板上下文对象
     */
    private fun buildContext(fileType: FileType, tabRef: Table): Map<String, Any> {
        val context = mapOf<String, Any>()
        val today = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())!!
        var imports = tabRef.columns
            .map { it.jvmType.javaObjectType.name }
            .filter { !it.startsWith("java.lang") }
            .toSet()
        if (options.superClass.isNotBlank()) {
            imports = imports.plus(options.superClass)
        }

        return context
            .plus("author" to options.author)
            .plus("date" to today)
            .plus("fullPackage" to "${options.rootPackage}.${fileType.name.lowercase()}")
            .plus("imports" to imports)
            .plus("table" to tabRef)
            .plus("superClass" to options.superClass.split(".").last())
    }

    /**
     * 应用命名策略
     */
    private fun naming(tabsRef: List<Table>): List<Table> {
        val tabPrefix = trimAndSplit(options.tableNaming.prefix)
        val tabSuffix = trimAndSplit(options.tableNaming.suffix)
        val colPrefix = trimAndSplit(options.columnNaming.prefix)
        val colSuffix = trimAndSplit(options.columnNaming.suffix)

        tabsRef.forEach { tab ->
            tabPrefix.forEach { tab.className = tab.className.removePrefix(it) }
            tabSuffix.forEach { tab.className = tab.className.removeSuffix(it) }
            tab.className = camelCase(tab.className, true)

            tab.columns.forEach { col ->
                colPrefix.forEach { col.propName = col.propName.removePrefix(it) }
                colSuffix.forEach { col.propName = col.propName.removeSuffix(it) }
                col.propName = camelCase(col.propName)
            }
        }
        return tabsRef
    }
}


