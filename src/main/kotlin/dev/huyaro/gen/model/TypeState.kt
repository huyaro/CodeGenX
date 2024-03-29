package dev.huyaro.gen.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.reflect.KClass

/**
 * @author huyaro
 * @date 2022-11-15
 * @description build-in types
 */
class TypeState {
    private val buildInTypes = mapOf<KClass<*>, Set<String>>(
        Int::class to setOf("int", "integer", "smallint", "mediumint", "tinyint"),
        String::class to setOf("char", "nchar", "varchar", "nvarchar", "clob", "nclob"),
        UUID::class to setOf("uuid"),
        Double::class to setOf("float", "real"),
        Boolean::class to setOf("bool", "boolean"),
        Long::class to setOf("bigint"),
        Byte::class to setOf("bit"),
        BigDecimal::class to setOf("decimal", "numeric"),
        LocalDate::class to setOf("date"),
        LocalTime::class to setOf("time"),
        LocalDateTime::class to setOf("datetime", "timestamp"),
    )

    var mapping = mutableMapOf<String, TypePair>()

    init {
        initTypes()
    }

    fun initTypes() {
        mapping.clear()
        buildInTypes
            .forEach { (jvmType, jdbcTypes) ->
                jdbcTypes.forEach {
                    mapping.putIfAbsent(it, TypePair(Tag.INTERNAL, it, jvmType.javaObjectType.name))
                }
            }
    }

    fun supportTypes(): Set<KClass<*>> {
        return buildInTypes.keys
    }

    /**
     * 添加jdbcType
     */
    fun addType(jvmType: String, jdbcType: String) {
        mapping[jdbcType.lowercase()] = TypePair(Tag.CUSTOM, jdbcType, jvmType)
    }

    /**
     * 移除
     */
    fun removeType(jdbcType: String) {
        mapping.remove(jdbcType.lowercase())
    }

}

/**
 * table row data
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
