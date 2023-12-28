package dev.huyaro.gen.meta

/**
 * 表信息
 *
 * @author huyaro
 * @date 2022-10-10
 */
data class Table(
    /**
     * 表名称
     */
    val name: String = "",

    /**
     * 类名称
     */
    var className: String = name,

    /**
     * 表注释
     */
    val comment: String?,

    /**
     * 主键字段
     */
    val keyColumns: MutableList<String> = mutableListOf(),

    /**
     * 业务主键(通过找到的第一个唯一索引来确认)
     */
    val refColumns: MutableList<String> = mutableListOf(),

    /**
     * 待生成的字段
     */
    val columns: MutableList<Column> = mutableListOf(),

    /**
     * 所有字段
     */
    val allColumns: MutableList<Column> = mutableListOf()
)

