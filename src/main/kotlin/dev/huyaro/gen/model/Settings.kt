package dev.huyaro.gen.model

import com.intellij.database.psi.DbTable
import com.intellij.openapi.module.Module
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

/**
 * @author yanghu
 * @date 2022-11-14
 * @description generator config
 */
@ApiStatus.Internal
data class DataModel(
    val modules: List<Module>,
    val tables: MutableList<DbTable>
)

@ApiStatus.Internal
data class GeneratorOptions(
    var activeModule: Module?,
    var author: String = System.getProperty("user.name"),
    var rootPackage: String = "",
    var outputDir: String = "",
    var fileMode: FileMode = FileMode.SKIPPING,
    var superClass: String = "",
    var fileTypes: Set<FileType> = mutableSetOf(FileType.ENTITY),
    var language: Language = Language.JAVA,
    var framework: Framework = Framework.JIMMER,
    var columnFilter: FilterRule = FilterRule(),
    var tableNaming: NamingRule = NamingRule(),
    var columnNaming: NamingRule = NamingRule(),
    var logs: String = ""
)


// ==================For GeneratorDialog==================
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
    OVERWRITE, SKIPPING
}

/**
 * 文件类型
 */
@ApiStatus.Internal
enum class FileType {
    ENTITY
}

/**
 * 语言
 */
@ApiStatus.Internal
enum class Language(val suffix: String) {
    JAVA("java"), KOTLIN("kt")
}

/**
 * 框架
 */
@ApiStatus.Internal
enum class Framework {
    JIMMER
}


// ==================For TypesDialog==================

enum class Tag {
    BUILD_IN, CUSTOM
}

/**
 * 表格中的每行数据
 */
class TypePair() {
    lateinit var tag: Tag
    lateinit var jdbcType: String
    lateinit var jvmType: String

    constructor(tag: Tag, jdbcType: String, jvmType: String) : this() {
        this.tag = tag
        this.jdbcType = jdbcType
        this.jvmType = jvmType
    }

    // Change string type to kotlin type
    fun readJvmType(): KClass<*> {
        return Class.forName(this.jvmType).kotlin
    }
}

/**
 * 列数据
 */
class TableColumnInfo(name: String, private val editable: Boolean = false) :
    ColumnInfo<TypePair, String>(name) {

    override fun valueOf(item: TypePair): String {
        return when {
            name.equals("Tag") -> item.tag.name.lowercase()
            name.equals("JdbcType") -> item.jdbcType
            name.equals("JvmType") -> item.readJvmType().javaObjectType.name
            else -> ""
        }
    }

    override fun isCellEditable(item: TypePair?): Boolean {
        return editable
    }

    override fun setValue(item: TypePair?, value: String?) {
        super.setValue(item, value)
    }
}