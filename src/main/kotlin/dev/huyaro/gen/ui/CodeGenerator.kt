package dev.huyaro.gen.ui

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
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
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.notExists

/**
 * @author huyaro
 * @date 2022-11-15
 * @description generate code with config
 */
class CodeGenerator(
    private val project: Project,
    private val options: GeneratorOptions,
    private var tables: List<Table>
) {

    private val log = Logger.getInstance(CodeGenerator::class.java)

    private val pluginId = "dev.huyaro.gen.x"
    private val templatePath = "templates"
    private val fileTypeMapping = FileType.entries.associateWith { it.name.lowercase() }

    private val templateEngine: VelocityTemplate = VelocityTemplate()

    fun generate(): String {
        val extensionsRootType = ExtensionsRootType.getInstance()
        val resource = extensionsRootType.findResourceDirectory(PluginId.findId(pluginId)!!, templatePath, false)
        val outLogs = StringBuilder()
        check(resource.exists()) {
            outLogs.append("Resource director [${resource}] not exists!\n")
            return outLogs.toString()
        }

        val logOut: (String) -> Unit = {
            outLogs.append(it)
            log.debug(it)
        }

        val outFiles = mutableListOf<Path>()
        this.tables = applyNaming(tables)
        val fileTypes = relateFileType(options.entityType, options.repositoryType, options.serviceType)
        fileTypes.forEach {
            val templateFile = getTemplate(resource, it)

            tables.forEach { tab ->
                getTargetFile(it, tab.className).let { fl ->
                    if (fl.exists()) {
                        if (options.fileMode == FileMode.Overwrite) {
                            logOut("Delete existing file [${fl.name}]\n")
                            Files.delete(fl)
                        } else {
                            logOut("Skip existing file [${fl.name}]\n")
                        }
                    }
                    // Don't Change it to else!!!! Mainly for detailed output logs.
                    if (fl.notExists()) {
                        val context = buildContext(it, tab)
                        logOut("Ready to render template [${templateFile.name}]\n")
                        // render template
                        templateEngine.render(fl, templateFile, context)
                        logOut("Generated File => [${fl}]\n")
                        outFiles.add(fl)
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
     * 处理选中的文件类型. 当entity未选中时, repository不生效
     */
    private fun relateFileType(entity: Boolean, repository: Boolean, serviceType: Boolean): Set<FileType> {
        val fileTypes = mutableSetOf<FileType>()
        if (entity) {
            fileTypes.add(FileType.Entity)
            if (repository) {
                fileTypes.add(FileType.Repository)
            }
            if (serviceType) {
                fileTypes.add(FileType.Service)
            }
        }
        return fileTypes
    }

    /**
     * 获取模板文件
     */
    private fun getTemplate(resource: Path, fileType: FileType): Path {
        val tmpFile = resource
            .resolve(options.framework.name.lowercase())
            .resolve("${fileType.name.lowercase()}.${options.language.name.lowercase()}.vm")
        check(tmpFile.exists()) { "Template file [${tmpFile.fileName}] not found!" }
        return tmpFile
    }

    /**
     * 获取输出的目标文件
     */
    private fun getTargetFile(fileType: FileType, fileName: String): Path {
        val pkgPath = options.rootPackage.replace(".", File.separator)
        val pkgName = fileTypeMapping[fileType] ?: FileType.Entity.name.lowercase()
        val fullPath = Paths.get(options.outputDir).resolve(pkgPath).resolve(pkgName)
        fullPath.takeIf { it.notExists() }?.let { Files.createDirectories(it) }

        val outName = when (fileType) {
            FileType.Repository -> if (options.framework == Framework.Jimmer) "${fileName}Repository" else "${fileName}Mapper"
            FileType.Service -> "${fileName}Service"
            FileType.Entity -> fileName
        }
        return fullPath.resolve("$outName.${options.language.suffix}")
    }

    /**
     * 构建模板上下文对象
     */
    private fun buildContext(fileType: FileType, tabRef: Table): Map<String, Any> {
        val context = mapOf<String, Any>()
        val today = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())!!
        val typeContext = when (fileType) {
            FileType.Repository -> buildRepositoryContext(tabRef)
            else -> buildEntityContext(tabRef, fileType)
        }

        return context
            .plus("author" to options.author)
            .plus("date" to today)
            .plus("fullPackage" to "${options.rootPackage}.${fileType.name.lowercase()}")
            .plus(typeContext)
    }

    /**
     * 构建repository类型上下文. 忽略没有主键的Entity.
     */
    private fun buildRepositoryContext(tabRef: Table): Map<String, Any> {
        check(tabRef.keyColumns.isNotEmpty()) { "[${tabRef.name}] primary key does not exist!\n" }

        val context = mutableMapOf<String, Any>()
        val keyClassName = tabRef.allColumns
            .first { it.name == tabRef.keyColumns[0] }
            .let {
                if (options.language == Language.Java) it.jvmType.javaObjectType.simpleName
                else it.jvmType.simpleName
            }!!
        context["entityKeyType"] = keyClassName

        val entityCls = "${options.rootPackage}.entity.${tabRef.className}"
        context["entityName"] = tabRef.className

        val importList = mutableListOf<String>()
        importList.add(entityCls)

        if (options.framework == Framework.Jimmer) {
            var superClass = "org.babyfish.jimmer.spring.repository."
            superClass += if (options.language == Language.Java) "JRepository" else "KRepository"
            val reposAnnot = "org.springframework.stereotype.Repository"
            importList.add(superClass)
            importList.add(reposAnnot)
            if (keyClassName == "UUID") {
                importList.add("java.util.UUID")
            }
        } else {
            val superClass = "com.baomidou.mybatisplus.core.mapper.BaseMapper"
            importList.add(superClass)
            if (keyClassName == "UUID") {
                importList.add("java.util.UUID")
            }
        }

        context["imports"] = importList
        return context
    }

    /**
     * 构建entity类型上下文
     */
    private fun buildEntityContext(tabRef: Table, fileType: FileType): Map<String, Any> {
        val context = mapOf<String, Any>()
        var imports = tabRef.columns
            .map { it.jvmType.javaObjectType.name }
            .filter { !it.startsWith("java.lang") }
            .toSet()
        // 判断是否需要添加uuid
        tabRef.columns
            .firstOrNull { it.primaryKey && it.jvmTypeName == "UUID" }
            ?.let {
                imports = imports.plus("org.babyfish.jimmer.sql.meta.UUIDIdGenerator")
            }
        // 判断是否需要添加@nullable,仅java需要
        if (options.language == Language.Java) {
            tabRef.columns
                .map { col ->
                    imports = if (col.nullable) {
                        imports.plus("org.jetbrains.annotations.Nullable")
                    } else {
                        imports.plus("org.jetbrains.annotations.NotNull")
                    }
                }
        }
        options.superClass
            .takeIf { it.isNotBlank() }
            ?.let { superClass -> imports = imports.plus(superClass) }
        fileType
            .takeIf { it == FileType.Service && options.serviceType }
            ?.let {
                val pkgPath = options.rootPackage
                val pkgName = fileTypeMapping[FileType.Entity]
                imports = imports.plus("${pkgPath}.${pkgName}.${tabRef.className}")
            }

        return context
            .plus("imports" to imports)
            .plus("table" to tabRef)
            .plus("superClass" to options.superClass.split(".").last())
    }

    /**
     * 应用命名策略
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
     * 格式化文件
     */
    private fun formatFiles(outFiles: List<Path>) {
        val psiManager = PsiManager.getInstance(project)
        val genFiles = outFiles.map { VfsUtil.findFileByIoFile(it.toFile(), true) }.toList()
        val psiFiles = genFiles.map { psiManager.findFile(it!!) }.toTypedArray()
        val processor = ReformatCodeProcessor(project, psiFiles, null, false)
        processor.run()
    }
}


