package dev.huyaro.gen.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KClass

/**
 * @author yanghu
 * @date 2022-11-15
 * @description build-in types
 */
class TypeMapping {
    private val buildInTypes = mapOf<KClass<*>, Set<String>>(
        Int::class to setOf("int", "integer", "smallint", "mediumint", "tinyint"),
        String::class to setOf("char", "nchar", "varchar", "nvarchar", "clob", "nclob"),
        Double::class to setOf("float", "real"),
        Boolean::class to setOf("bool", "boolean"),
        Long::class to setOf("bigint"),
        Byte::class to setOf("bit"),
        BigDecimal::class to setOf("decimal", "numeric"),
        LocalDate::class to setOf("date"),
        LocalTime::class to setOf("time"),
        LocalDateTime::class to setOf("datetime", "timestamp"),
    )

    var typePairs = mutableMapOf<String, TypePair>()

    init {
        initTypes()
    }

    fun initTypes() {
        buildInTypes
            .forEach { (java, jdbcTypes) ->
                jdbcTypes.forEach {
                    typePairs.putIfAbsent(it, TypePair(Tag.BUILD_IN, it, java))
                }
            }
    }

    fun supportTypes(): Set<KClass<*>> {
        return buildInTypes.keys
    }

    /**
     * 添加jdbcType
     */
    fun addType(javaType: KClass<*>, jdbcType: String) {
        val lowerJdbc = jdbcType.lowercase()
        val newPair = TypePair(Tag.BUILD_IN, lowerJdbc, javaType)
        typePairs.compute(jdbcType) { _, _ -> newPair }
    }

    /**
     * 移除
     */
    fun removeType(jdbcType: String) {
        typePairs.remove(jdbcType.lowercase())
    }

}