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
import dev.huyaro.gen.model.*
import dev.huyaro.gen.util.VelocityTemplate
import dev.huyaro.gen.util.naming
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.io.path.notExists

/**
 * @author huyaro
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

        val outFiles = mutableListOf<Path>()
        this.tables = applyNaming(tables)
        val fileTypes = relateFileType(options.entityType, options.repositoryType)
        fileTypes.forEach {
            val templateFile = getTemplate(resource, it).apply {
                if (notExists()) {
                    val line = "Template File $this not Exists!\n"
                    log.error(line)
                    outLogs.append(line)
                }
            }

            tables.forEach { tab ->
                getTargetFile(it, tab.className).let { fl ->
                    if (fl.exists()) {
                        val line: String
                        if (options.fileMode == FileMode.Overwrite) {
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
                        outFiles.add(fl)

                        line = "Generated File => [${fl}]\n"
                        log.warn(line)
                        outLogs.append(line)
                    }
                }
            }
        }
        // format process
        formatFiles(outFiles)
        // out log to insert view
        return outLogs.toString()
    }

    /**
     * ???????????????????????????. ???entity????????????, repository?????????
     */
    private fun relateFileType(entity: Boolean, repository: Boolean): Set<FileType> {
        val fileTypes = mutableSetOf<FileType>()
        if (entity) {
            fileTypes.add(FileType.Entity)
            if (repository) {
                fileTypes.add(FileType.Repository)
            }
        }
        return fileTypes
    }

    /**
     * ??????????????????
     */
    private fun getTemplate(resource: Path, fileType: FileType): Path {
        return resource.resolve(options.framework.name.lowercase())
            .resolve("${fileType.name.lowercase()}.${options.language.name.lowercase()}.vm")
    }

    /**
     * ???????????????????????????
     */
    private fun getTargetFile(fileType: FileType, fileName: String): Path {
        val pkgPath = options.rootPackage.replace(".", File.separator)
        val pkgName = fileTypeMapping[fileType]!!
        val fullPath = Paths.get(options.outputDir).resolve(pkgPath).resolve(pkgName)
        if (fullPath.notExists()) {
            Files.createDirectories(fullPath)
        }
        val outName = if (fileType == FileType.Repository) "${fileName}Repository" else fileName
        return fullPath.resolve("$outName.${options.language.suffix}")
    }

    /**
     * ???????????????????????????
     */
    private fun buildContext(fileType: FileType, tabRef: Table): Map<String, Any> {
        val context = mapOf<String, Any>()
        val today = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())!!
        val typeContext =
            if (fileType == FileType.Entity) buildEntityContext(tabRef) else buildRepositoryContext(tabRef)

        return context
            .plus("author" to options.author)
            .plus("date" to today)
            .plus("fullPackage" to "${options.rootPackage}.${fileType.name.lowercase()}")
            .plus(typeContext)
    }

    /**
     * ??????repository???????????????. ?????????????????????Entity.
     */
    private fun buildRepositoryContext(tabRef: Table): Map<String, Any> {
        val context = mapOf<String, Any>()
        if (tabRef.keyColumns.size > 0) {
            val keyClassName = tabRef.columns.first { it.name == tabRef.keyColumns[0] }.jvmType.simpleName!!
            val entityCls = "${options.rootPackage}.entity.${tabRef.className}"
            var superClass = "org.babyfish.jimmer.spring.repository."
            superClass += if (options.language == Language.Java) "JRepository" else "KRepository"
            val reposAnnot = "org.springframework.stereotype.Repository"

            return context
                .plus("entityKeyType" to keyClassName)
                .plus("entityName" to tabRef.className)
                .plus("imports" to listOf(entityCls, superClass, reposAnnot))
        }
        return context
    }

    /**
     * ??????entity???????????????
     */
    private fun buildEntityContext(tabRef: Table): Map<String, Any> {
        val context = mapOf<String, Any>()
        var imports = tabRef.columns
            .map { it.jvmType.javaObjectType.name }
            .filter { !it.startsWith("java.lang") }
            .toSet()
        if (options.superClass.isNotBlank()) {
            imports = imports.plus(options.superClass)
        }
        return context
            .plus("imports" to imports)
            .plus("table" to tabRef)
            .plus("superClass" to options.superClass.split(".").last())
    }

    /**
     * ??????????????????
     */
    private fun applyNaming(tabsRef: List<Table>): List<Table> {
        tabsRef.forEach { tab ->
            tab.className = naming(tab.name, options.strategyRules)

            tab.columns.forEach { col ->
                col.propName = naming(col.name, options.strategyRules, OptTarget.Column)
            }
        }
        return tabsRef
    }

    /**
     * ???????????????
     */
    private fun formatFiles(outFiles: List<Path>) {
        val psiManager = PsiManager.getInstance(project)
        val genFiles = outFiles.map { VfsUtil.findFileByIoFile(it.toFile(), true) }.toList()
        val psiFiles = genFiles.map { psiManager.findFile(it!!) }.toTypedArray()
        val processor = ReformatCodeProcessor(project, psiFiles, null, false)
        processor.run()
    }
}


