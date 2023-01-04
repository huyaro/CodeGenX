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
    var language: Language = Language.Java,
    var framework: Framework = Framework.Jimmer,
    var columnFilter: FilterRule = FilterRule(),
    var tableNaming: NamingRule = NamingRule(),
    var columnNaming: NamingRule = NamingRule(),
    var strategy: StrategyOptions = StrategyOptions(),
    var logs: String = ""
)

@ApiStatus.Internal
data class StrategyOptions(
    var operator: Operator = Operator.Add,
    var target: OptTarget = OptTarget.Table,
    var position: OptPosition = OptPosition.Suffix,
    var optValue: String = "",
)

/**
 * 过滤配置
 */
@ApiStatus.Internal
data class FilterRule(
    var exclude: String = "", var useRegex: Boolean = true
)

/**
 * 命名配置
 */
@ApiStatus.Internal
data class NamingRule(
    var prefix: String = "", var suffix: String = ""
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
    Entity, Repository
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
    Add, Remove
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
    Suffix, Prefix
}


// ==================For TypesDialog==================

enum class Tag {
    INTERNAL, CUSTOM
}
