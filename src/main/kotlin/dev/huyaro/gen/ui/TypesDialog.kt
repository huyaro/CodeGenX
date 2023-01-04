package dev.huyaro.gen.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import dev.huyaro.gen.model.Tag
import dev.huyaro.gen.model.TypePair
import dev.huyaro.gen.model.TypeRegistration
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.DefaultCellEditor
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableColumn


/**
 * @author huyaro
 * @date 2022-11-14
 * @description Config Dialog
 */
class TypesDialog constructor(private val project: Project?) {
    private val tableModel = ListTableModel<TypePair>(
        TableColumnInfo("Tag"),
        TableColumnInfo("JdbcType"),
        TableColumnInfo("JvmType")
    )

    private var table = JBTable(tableModel)

    // 初始化数据
    private val typeService = TypeRegistration.newInstance()
    private val typeItems = typeService.typeState.supportTypes().map { it.javaObjectType.name }

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
        val jvmTypes = typeService.typeState.mapping.values
        jvmTypes.forEach { tableModel.addRow(it) }
        // render column
        renderTextColumn(table.columnModel.getColumn(1))
        renderComboboxColumn(table.columnModel.getColumn(2))
    }

    fun initPanel(): DialogPanel {
        val cfgPanel = DialogPanel(BorderLayout())

        // 添加工具栏
        val decorator = ToolbarDecorator.createDecorator(table)
        // add "reset" tool button
        decorator.addExtraAction(ResetButtonAction(project, tableModel))
        // override "add/remove" event
        decorator.setAddAction { _ -> addDefaultRow() }
        decorator.setRemoveAction { _ -> removeRows() }

        // wrapper table with scroll
        val scrollTable = JBScrollPane(decorator.createPanel())

        // add notes
        val commentRow = panel {
            row {
                label("Important notes").bold()
            }
            row {
                text("1. If the type does not exist, [String] is used by default!")
            }
            row {
                text("2. JdbcType with spaces are not supported!！")
            }
        }

        cfgPanel.add(commentRow, BorderLayout.NORTH)
        cfgPanel.add(scrollTable, BorderLayout.CENTER)

        return cfgPanel
    }

    private fun addDefaultRow() {
        val emptyType = TypePair(Tag.CUSTOM, "", "java.lang.String")
        tableModel.insertRow(0, emptyType)
    }

    private fun removeRows() {
        val rows = table.selectedRows
        if (rows.isEmpty()) {
            Messages.showMessageDialog(
                "Select row before deleting!",
                "Warning",
                null
            )
        } else {
            var buildFlag = 0
            var delIndex = listOf<Int>()
            // Collect the items to be deleted first to avoid the index change problem of direct deletion
            rows.forEach {
                if (tableModel.getItem(it).tag == Tag.INTERNAL) {
                    buildFlag++
                } else {
                    delIndex = delIndex.plusElement(it)
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
                    "Remove count[${delIndex.size}], built-in type cannot be deleted!",
                    "Warning",
                    null
                )
            }
        }
    }

    /**
     * Render Assign column as text
     */
    private fun renderTextColumn(column: TableColumn) {
        val textField = JTextField()
        column.cellEditor = DefaultCellEditor(textField)
        textField.addActionListener { evt ->
            val item = tableModel.getItem(table.selectedRow)
            item.jdbcType = evt.actionCommand?.trim() ?: ""
            if (item.jdbcType.isNotBlank()) {
                typeService.register(item)
            }
        }
        val renderer = DefaultTableCellRenderer().apply {
            toolTipText = "Click for edit jdbcType"
        }
        column.cellRenderer = renderer
    }

    /**
     * Render Assign Column as ComboBox
     */
    private fun renderComboboxColumn(column: TableColumn) {
        // Set up the editor for the sport cells.
        val comboBox = ComboBox(typeItems.toTypedArray())
        column.cellEditor = DefaultCellEditor(comboBox)
        // Add combobox changed listener
        comboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                val item = tableModel.getItem(table.selectedRow)
                item.jvmType = it.item.toString()
                if (item.jdbcType.isNotBlank()) {
                    typeService.register(item)
                }
            }
        }

        // Set up tool tips for the sport cells.
        val renderer = DefaultTableCellRenderer().apply {
            toolTipText = "Click for select jvmType"
        }
        column.cellRenderer = renderer
    }
}

/**
 * render table columns
 */
private class TableColumnInfo(name: String) : ColumnInfo<TypePair, String>(name) {

    override fun valueOf(item: TypePair): String {
        return when {
            name.equals("Tag") -> item.tag.name
            name.equals("JdbcType") -> item.jdbcType
            name.equals("JvmType") -> item.readJvmType().javaObjectType.name
            else -> ""
        }
    }

    override fun isCellEditable(item: TypePair?): Boolean {
        return item?.tag == Tag.CUSTOM && !name.equals("Tag")
    }

    override fun setValue(item: TypePair?, value: String?) {
        super.setValue(item, value)
    }
}

/**
 * @author huyaro
 * @date 2023-1-3
 */
private class ResetButtonAction(
    private val project: Project?,
    private val tableModel: ListTableModel<TypePair>
) :
    AnActionButton("Reset", AllIcons.Actions.Refresh) {
    private val typeService = TypeRegistration.newInstance()

    override fun actionPerformed(e: AnActionEvent) {
        val answer = Messages.showOkCancelDialog(
            project,
            "Are you sure you want to reset to the default mapping type?",
            "Question", "Confirm", "Cancel",
            AllIcons.Actions.Refresh
        )
        if (answer == Messages.OK) {
            for (i in tableModel.items.lastIndex downTo 0) {
                tableModel.removeRow(i)
            }
            typeService.typeState.initTypes()
            typeService.typeState.mapping.values.forEach { tableModel.addRow(it) }
            // tableScroll.verticalScrollBar.value = tableScroll.verticalScrollBar.minimum
        }
    }
}
