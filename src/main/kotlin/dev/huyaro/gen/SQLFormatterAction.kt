package dev.huyaro.gen

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import dev.huyaro.gen.util.notify
import dev.huyaro.gen.util.valueWrapper
import java.awt.datatransfer.StringSelection

/**
 * @author huyaro
 * @date 2023-12-27
 * @description copy sql and replace parameters
 */
class SQLFormatterAction : AnAction() {

    private val sqlReg = Regex("(select|insert|update|delete).{10,}")
    private val nativeSqlReg = Regex(", variables: \\[(.*)],")
    private val nativePlaceHolderReg = Regex("\\?,?")
    private val prettyPlaceHolderReg = Regex("\\?\\s+/\\*\\s+([^?]*)\\s+\\*/")
    private val nativeSqlKey = ", variables: ["
    private val prettySqlCommandKey = "Affected row count: "
    private val prettySqlQueryKey = "JDBC response status: "

    override fun actionPerformed(evt: AnActionEvent) {
        val selectedText = evt.getRequiredData(CommonDataKeys.EDITOR).selectionModel.selectedText
        val resultSql = selectedText?.let {
            val replacedSql = it.replace("\n", " ")
            sqlReg.find(replacedSql)?.let { res ->
                res.groupValues[0].let { sql ->
                    if (prettyPlaceHolderReg.containsMatchIn(sql) || sql.contains(prettySqlCommandKey)) {
                        replacePrettySql(sql)
                    } else if (nativePlaceHolderReg.containsMatchIn(sql) || sql.contains(nativeSqlKey)) {
                        replaceNativeSql(sql)
                    } else {
                        sql.substringBeforeLast(nativeSqlKey)
                            .substringBeforeLast(prettySqlCommandKey)
                            .substringBeforeLast(prettySqlQueryKey)
                    }
                }
            }
        } ?: ""

        if (resultSql.isNotBlank()) {
            CopyPasteManager.getInstance().setContents(StringSelection(resultSql))
            evt.project?.let { notify("Jimmer SQL", "SQL statement has been copied.", it) }
        } else {
            evt.project?.let {
                notify(
                    "Jimmer SQL",
                    "Bad SQL statement!!!", it,
                    notifyType = NotificationType.ERROR
                )
            }
        }
    }

    /**
     * 替换已格式化的SQL中的紧跟占位符的参数
     */
    private fun replacePrettySql(sqlText: String): String {
        var sql = sqlText.substringBeforeLast(prettySqlCommandKey).substringBeforeLast(prettySqlQueryKey)
        while (prettyPlaceHolderReg.containsMatchIn(sql)) {
            prettyPlaceHolderReg.find(sql)?.let {
                sql = sql.replaceFirst(it.groupValues[0], valueWrapper(it.groupValues[1]))
            }
        }
        return sql
    }

    /**
     * 替换未格式的sql参数中的问号占位符
     */
    private fun replaceNativeSql(sqlText: String): String {
        return nativeSqlReg.find(sqlText)?.let {
            var sql = sqlText.substringBeforeLast(nativeSqlKey)
            val params = it.groupValues[1]
            if (params.isNotBlank()) {
                val paramValues = params.split(", ")
                val placeHolderParamCount = nativePlaceHolderReg.findAll(sql).count()
                if (paramValues.size != placeHolderParamCount) {
                    ""
                } else {
                    for (i in 0 until placeHolderParamCount) {
                        sql = sql.replaceFirst("?", valueWrapper(paramValues[i]))
                    }
                    sql
                }
            } else {
                sql
            }
        } ?: sqlText
    }

}