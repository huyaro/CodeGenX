package dev.huyaro.gen.model

import com.intellij.database.psi.DbTable
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

/**
 * @author huyaro
 * @date 2022-11-14
 * @description generator source data
 */
@ApiStatus.Internal
data class DataModel(
    val modules: List<Module>,
    val tables: MutableList<DbTable>
)

// ==================For GeneratorDialog==================
/**
 * generator options
 */
@ApiStatus.Internal
data class GeneratorOptions(
    var activeModule: Module?,
    var author: String = System.getProperty("user.name"),
    var rootPackage: String = "",
    var outputDir: String = "",
    var fileMode: FileMode = FileMode.Skipping,
    var superClass: String = "",
    var entityType: Boolean = true,
    var repositoryType: Boolean = true,
    var inputType: Boolean = false,
    var language: Language = Language.Java,
    var framework: Framework = Framework.Jimmer,
    var excludeCols: String = "",
    var strategyRules: MutableList<StrategyRule> = mutableListOf(),
    var logs: String = ""
)

@ApiStatus.Internal
data class StrategyRule(
    var operator: String = Operator.Add.name,
    var target: String = OptTarget.Table.name,
    var position: String = OptPosition.Prefix.name,
    var optValue: String = "",
)

/**
 * 文件覆盖模式
 */
@ApiStatus.Internal
enum class FileMode {
    Overwrite, Skipping
}

/**
 * 文件类型
 */
@ApiStatus.Internal
enum class FileType {
    Entity, Repository, Input
}

/**
 * 语言
 */
@ApiStatus.Internal
enum class Language(val suffix: String) {
    Java("java"), Kotlin("kt")
}

/**
 * 框架
 */
@ApiStatus.Internal
enum class Framework {
    Jimmer
}

// ==================For StrategyDialog==================

/**
 * 操作
 */
@ApiStatus.Internal
enum class Operator {
    Add, Remove;
}

/**
 * 操作目标
 */
@ApiStatus.Internal
enum class OptTarget {
    Table, Column
}

/**
 * 操作位置
 */
@ApiStatus.Internal
enum class OptPosition {
    Prefix, Suffix
}


// ==================For TypesDialog==================

enum class Tag {
    INTERNAL, CUSTOM
}
