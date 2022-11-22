package dev.huyaro.gen.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel
import dev.huyaro.gen.model.TableColumnInfo
import dev.huyaro.gen.model.Tag
import dev.huyaro.gen.model.TypePair
import dev.huyaro.gen.model.TypeRegistration
import java.awt.BorderLayout
import javax.swing.ListSelectionModel

/**
 * @author yanghu
 * @date 2022-11-14
 * @description Config Dialog
 */
class TypesDialog constructor(private val project: Project?) {
    private val tableModel = ListTableModel<TypePair>(
        TableColumnInfo("Tag", false),
        TableColumnInfo("JdbcType", true),
        TableColumnInfo("JavaType", false)
    )

    var table = JBTable(tableModel)

    // 初始化数据
//    val typeService = project?.service<TypeRegistrationService>()!!
    private val typeService = TypeRegistration.getInstance()

    init {
        table = table.apply {
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            tableHeader.reorderingAllowed = false
            rowSelectionAllowed = true
            rowHeight = 25
            columnModel.getColumn(0).preferredWidth = 50
            columnModel.getColumn(1).preferredWidth = 100
            columnModel.getColumn(2).preferredWidth = 200
        }
        typeService.state.typePairs.values.forEach { tableModel.addRow(it) }
    }

    fun initPanel(): DialogPanel {
        val cfgPanel = DialogPanel(BorderLayout())
        val scrollTable = JBScrollPane(table)

        // add notes
        val commentRow = panel {
            row {
                label("If the type does not exist, String is used by default!").bold()
            }
        }

        cfgPanel.add(commentRow, BorderLayout.NORTH)
        cfgPanel.add(scrollTable, BorderLayout.CENTER)

        // 添加输入框
        val inputPanel = panel {
            lateinit var comJdbcType: JBTextField
            lateinit var comJavaType: ComboBox<Class<*>>
            row {
                comJdbcType = textField()
                    .label("JdbcType: ")
                    .gap(RightGap.COLUMNS)
                    .component

                val typeItems = typeService.state.supportTypes().map { it.javaObjectType }
                comJavaType = comboBox(typeItems)
                    .label("JavaType: ")
                    .component
            }.rowComment(
                "Spaces in type are not supported! Custom jdbcType with the same name will override the built-in type",
                maxLineLength = Int.MAX_VALUE
            )

            buttonsGroup {
                row {
                    button(" Add ") {
                        if (comJdbcType.text.isEmpty()) {
                            Messages.showMessageDialog("JdbcType Can't be empty!", "Warning", null)
                        } else {
                            // add row
                            val typePair = TypePair(Tag.CUSTOM, comJdbcType.text, comJavaType.item.kotlin)
                            tableModel.addRow(typePair)
                            typeService.register(typePair)
                            comJdbcType.text = ""
                            scrollTable.verticalScrollBar.value = scrollTable.verticalScrollBar.maximum
                        }
                    }
                    button(" Remove ") {
                        var buildFlag = 0
                        var delIndex = listOf<Int>()
                        // Collect the items to be deleted first to avoid the index change problem of direct deletion
                        table.selectedRows.forEach {
                            if (tableModel.getItem(it).tag == Tag.BUILD_IN) {
                                buildFlag++
                            } else {
                                delIndex = delIndex.plus(it)
                            }
                        }
                        // Remove from back to front in index order
                        delIndex = delIndex.sortedDescending()
                        delIndex.forEach {
                            typeService.unregister(tableModel.getItem(it))
                            tableModel.removeRow(it)
                        }
                        // Prompt that built-in type is selected
                        if (buildFlag > 0) {
                            Messages.showMessageDialog(
                                "Delete operations[$buildFlag] for built-in types are ignored!",
                                "Warning",
                                null
                            )
                        }
                    }
                    button(" Reset ") {
                        val answer = Messages.showOkCancelDialog(
                            project,
                            "Are you sure you want to reset to the default mapping type?",
                            "Question", "Confirm", "Cancel", null
                        )
                        if (answer == Messages.OK) {
                            for (i in tableModel.items.lastIndex downTo 0) {
                                tableModel.removeRow(i)
                            }
                            typeService.state.typePairs.values.forEach { tableModel.addRow(it) }
                            scrollTable.verticalScrollBar.value = scrollTable.verticalScrollBar.minimum
                        }
                    }
                }
            }
        }
        cfgPanel.add(inputPanel, BorderLayout.SOUTH)

        return cfgPanel
    }
}

