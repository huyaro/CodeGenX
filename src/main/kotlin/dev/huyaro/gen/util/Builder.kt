package dev.huyaro.gen.util

import com.intellij.database.psi.DbTable
import com.intellij.database.util.DasUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.io.isDirectory
import dev.huyaro.gen.meta.Column
import dev.huyaro.gen.meta.Table
import dev.huyaro.gen.model.GeneratorOptions
import dev.huyaro.gen.model.Language
import dev.huyaro.gen.model.TypeRegistration
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author huyaro
 * @date 2022-11-21
 * @description Build TableModel for Das
 */

/**
 * build generator options
 */
fun buildOptions(module: Module): GeneratorOptions {
    val username = System.getProperty("user.name")
    val sourceRoot = ModuleRootManager.getInstance(module)
        .getSourceRoots(JavaSourceRootType.SOURCE)
        .first { vf -> vf.path.contains("src/main") }

    var pkgRoot = ""
    // guess root package
    val sourceRootDir = Paths.get(sourceRoot.path)
    Files.walk(sourceRootDir, 5)
        .filter { it.isDirectory() && it != sourceRootDir && Files.list(it).count() > 1 }
        .findFirst()
        .ifPresent { p ->
            pkgRoot = sourceRootDir.relativize(p).joinToString(separator = ".")
        }

    return GeneratorOptions(
        activeModule = module, author = username, rootPackage = pkgRoot, outputDir = sourceRoot.path
    )
}


/**
 * build full data for table metadata
 */
fun buildTable(
    typeService: TypeRegistration,
    dbTable: DbTable,
    excludeValue: String,
    lang: Language
): Table {
    val indices = DasUtil.getIndices(dbTable)
    val table = Table(name = dbTable.name, comment = dbTable.comment)

    val excludeCols = trimAndSplit(excludeValue)
    val columns = DasUtil.getColumns(dbTable).toList()

    columns.forEach {
        val colName = it.name
        // There may be more than one identified type. e.g. tinyint unsigned
        val colType = it.dataType.typeName.let { name -> name.split(" ")[0] }
        val isPrimaryKey = DasUtil.isPrimary(it)
        val jvmType = typeService.getJvmType(colType)
        val jvmTypeName = if (lang == Language.Kotlin) jvmType.simpleName else {
            if (isPrimaryKey) {
                jvmType.javaPrimitiveType?.simpleName ?: jvmType.javaObjectType.simpleName
            } else jvmType.javaObjectType.simpleName
        }!!
        val column = Column(
            name = colName,
            typeName = colType,
            jvmType = jvmType,
            jvmTypeName = jvmTypeName,
            primaryKey = isPrimaryKey,
            // 兼容postgres主键自增判断
            autoGenerated = DasUtil.isAutoGenerated(it) || it.default?.startsWith("nextval(") ?: false,
            length = it.dataType.length,
            scale = it.dataType.scale,
            nullable = !it.isNotNull,
            defaultValue = it.default,
            comment = it.comment,
            uniqueKey = indices.filter { idx ->
                !isPrimaryKey && idx.isUnique && DasUtil.containsName(colName, idx.columnsRef)
            }.size() > 0
        )
        when {
            column.primaryKey -> table.keyColumns.add(column.name)
            column.uniqueKey -> table.refColumns.add(column.name)
        }
        // add all columns
        table.allColumns.add(column)

        // Add valid fields after exclusion
        if (excludeCols.isEmpty() || excludeCols.all { re -> !Regex(re).matches(colName) }) {
            table.columns.add(column)
        }
    }
    return table
}