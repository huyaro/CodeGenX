package dev.huyaro.gen.ui

import com.intellij.database.psi.DbTable
import com.intellij.database.util.DasUtil
import com.intellij.database.view.actions.font
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PackageChooserDialog
import com.intellij.ide.util.TreeJavaClassChooserDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import dev.huyaro.gen.model.*
import dev.huyaro.gen.util.*
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.ItemEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.DefaultCellEditor
import javax.swing.JTextField
import javax.swing.ListSelectionModel

/**
 * Generator Dialog
 *
 * @author huyaro
 * @date 2022-11-06
 */
class GeneratorDialog(
    private val project: Project?,
    private val options: GeneratorOptions,
    private val data: DataModel
) {

    private lateinit var outDir: Cell<TextFieldWithBrowseButton>
    private lateinit var txtPkg: Cell<JBTextField>
    private lateinit var txtLog: Cell<JBTextArea>
    private lateinit var chkEntity: Cell<JBCheckBox>
    private lateinit var chkRepository: Cell<JBCheckBox>
    private lateinit var chkInput: Cell<JBCheckBox>
    private lateinit var txtExcludeCols: Cell<JBTextField>

    lateinit var logger: LoggerComponent
    lateinit var optionPanel: DialogPanel

    fun initPanel(): DialogPanel {
        val genPanel = DialogPanel(BorderLayout())
        optionPanel = initOptionPanel()
        genPanel.add(optionPanel, BorderLayout.NORTH)

        val logPanel = panel {
            row {
                label("Logs")
            }
            row {
                txtLog = textArea().rows(10)
                    .align(AlignX.FILL)
                    .bindText(options::logs)
                    .font(Font("Hack", Font.ROMAN_BASELINE, 14))
                txtLog.component.isEditable = false
            }
        }

        logger = LoggerComponent(txtLog)
        val tablePanel = StrategyTableInfo(data.tables, options, logger, txtExcludeCols).initTable()
        genPanel.add(tablePanel, BorderLayout.CENTER)

        // add log panel
        genPanel.add(logPanel, BorderLayout.SOUTH)

        return genPanel
    }

    private fun initOptionPanel(): DialogPanel {
        return panel {
            row("Module:") {
                val cmbModule = comboBox(data.modules)
                    .bindItem(options::activeModule.toNullableProperty())
                    .comment("Select the module")
                // add item changed event
                cmbModule.component.addActionListener {
                    val selectedIndex = (it.source as ComboBox<*>).selectedIndex
                    val curOpts = buildOptions(data.modules[selectedIndex])
                    outDir.text(curOpts.outputDir)
                    txtPkg.text(curOpts.rootPackage)
                }

                txtPkg = textField().label("Package: ")
                    .bindText(options::rootPackage)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Select root package")
                button("Choose...") {
                    val chooserDialog = PackageChooserDialog("Choose Package", project)
                    chooserDialog.show()
                    val psiPackage = chooserDialog.selectedPackage
                    if (psiPackage != null) {
                        txtPkg.text(psiPackage.qualifiedName)
                    }
                }
            }.layout(RowLayout.PARENT_GRID)

            row("Author: ") {
                textField()
                    .bindText(options::author)
                    .comment("Input your name")
                val superCls = textField()
                    .label("SuperClass: ")
                    .bindText(options::superClass)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Select or enter the superclass of entity")
                // 避免外部包无法选择类, 让父类输入框可手动输入父类包名
                // superCls.component.isEditable = false

                button("Choose...") {
                    val classChooserDialog = TreeJavaClassChooserDialog("Choose SuperClass...", project)
                    classChooserDialog.show()
                    val selectedCls = classChooserDialog.selected
                    if (selectedCls != null) {
                        selectedCls.qualifiedName?.let { superCls.text(it) }
                        txtExcludeCols.text("")
                        options.excludeCols = ""
                        // 反射获取superclass的已定义字段放入到excludeColumns中
                        if (selectedCls.isInterface) {
                            val joinColumns = selectedCls.methods.joinToString(", ") { method ->
                                method.annotations
                                    .firstOrNull { it.qualifiedName == "org.babyfish.jimmer.sql.Column" }
                                    ?.let { it.findAttributeValue("name")?.text?.replace("\"", "") }
                                    ?: toUnderline(method.name).replace("^get_(.*)".toRegex(), "$1")
                            }
                            txtExcludeCols.text(joinColumns)
                            options.excludeCols = joinColumns
                        }
                    }
                }
            }.layout(RowLayout.PARENT_GRID)

            row("OutputDir: ") {
                outDir = textFieldWithBrowseButton(
                    project = project,
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                ).bindText(options::outputDir)
                    .gap(RightGap.SMALL)
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
                .rowComment("Select the output directory. e.g.: /Project/src/main/java")

            twoColumnsRow({
                panel {
                    buttonsGroup {
                        row("FileMode: ") {
                            FileMode.entries.forEach {
                                radioButton(it.name, it)
                            }
                        }.rowComment("Select the file output method")
                    }.bind(options::fileMode)

                    row("FileType: ") {
                        chkEntity = checkBox(FileType.Entity.name)
                            .bindSelected(options::entityType)
                        chkRepository = checkBox(FileType.Repository.name)
                            .bindSelected(options::repositoryType)
                            .enabledIf(chkEntity.selected)
                        chkInput = checkBox(FileType.Service.name)
                            .bindSelected(options::serviceType)
                            .enabledIf(chkEntity.selected)
                            .visible(false)
                    }.rowComment("Entity type must be selected!")
                }
            }, {
                panel {
                    buttonsGroup {
                        row("Language: ") {
                            Language.entries.forEach {
                                radioButton(it.name, it)
                            }
                        }.rowComment("Select the language")
                    }.bind(options::language)

                    buttonsGroup {
                        row("Framework: ") {
                            Framework.entries.forEach {
                                radioButton(it.name, it)
                            }
                        }.rowComment("Select the framework")
                    }.bind(options::framework)
                }
            }).layout(RowLayout.INDEPENDENT)

            row {
                txtExcludeCols = textField()
                    .label("Exclude columns: ")
                    .bindText(options::excludeCols)
                    .align(AlignX.FILL)
            }.rowComment("Use commas to separate multiple items")
            row {
                label("Naming rules         ↓")
                contextHelp(
                    "Use the last button on the toolbar [Test Rules] to output detailed logs.",
                    "Usage help"
                )
            }
        }
    }
}

class LoggerComponent(private val txtLog: Cell<JBTextArea>) {
    /**
     * 输出日志
     */
    fun flush(lines: String) {
        val logComp = txtLog.component
        val nowTime = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(LocalDateTime.now())
        logComp.insert("\n>>>$nowTime\n$lines\n", logComp.text.length)
        logComp.autoscrolls = true
    }
}

/**
 * strategy table
 */
private class StrategyTableInfo(
    val tableList: List<DbTable>,
    val options: GeneratorOptions,
    val logger: LoggerComponent,
    val txtComp: Cell<JBTextField>
) {
    private val tableModel = ListTableModel<StrategyRule>(
        StrategyTableColumnInfo("Operator"),
        StrategyTableColumnInfo("Target"),
        StrategyTableColumnInfo("Position"),
        StrategyTableColumnInfo("Value")
    )
    private var table = JBTable(tableModel)
    private val optItems = mapOf(
        0 to Operator.entries.map { it.name },
        1 to OptTarget.entries.map { it.name },
        2 to OptPosition.entries.map { it.name }
    )

    init {
        table.apply {
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            tableHeader.reorderingAllowed = false
            rowSelectionAllowed = true
            fillsViewportHeight = true
            rowHeight = 25
        }
        // render columns with comboBox or text
        renderColumns()
    }

    /**
     * init strategy config table
     */
    fun initTable(): JBScrollPane {
        // add toolbar
        val decorator = ToolbarDecorator.createDecorator(table)
        // add "test" tool button, only test first table
        val testButtonAction = TestButtonAction(tableList[0], tableModel, txtComp, logger)
        decorator.addExtraAction(testButtonAction)
        // override "add" event
        decorator.setAddAction { _ -> addDefaultRow() }
        // wrapper table with scroll
        return JBScrollPane(decorator.createPanel())
    }

    /**
     * render columns with comboBox or text
     */
    private fun renderColumns() {
        optItems.map { entry ->
            val comboBox = ComboBox(entry.value.toTypedArray())
            val column = table.columnModel.getColumn(entry.key)
            column.cellEditor = DefaultCellEditor(comboBox)

            // Add comboBox changed listener
            comboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    val item = tableModel.getItem(table.selectedRow)
                    val itemValue = it.item as String
                    when (entry.key) {
                        0 -> item.operator = itemValue
                        1 -> item.target = itemValue
                        2 -> item.position = itemValue
                    }
                }
            }
        }

        val textField = JTextField("")
        val colText = table.columnModel.getColumn(3)
        colText.cellEditor = DefaultCellEditor(textField)
        textField.addActionListener { evt ->
            val item = tableModel.getItem(table.selectedRow)
            val optVal = evt.actionCommand?.trim() ?: ""
            if (optVal.isNotBlank() && isNamingNormal(optVal)) {
                item.optValue = optVal
            } else {
                logger.flush("Wrong naming [$optVal] !!")
            }
        }
    }

    /**
     * sync options strategy rules
     */
    private fun addDefaultRow() {
        tableModel.addRow(StrategyRule())
        options.strategyRules = tableModel.items
    }
}

/**
 * render table columns
 */
private class StrategyTableColumnInfo(name: String) : ColumnInfo<StrategyRule, String>(name) {

    override fun valueOf(item: StrategyRule): String {
        return when (name) {
            "Operator" -> item.operator
            "Target" -> item.target
            "Position" -> item.position
            "Value" -> item.optValue
            else -> ""
        }
    }

    override fun isCellEditable(item: StrategyRule?): Boolean {
        return true
    }

    override fun setValue(item: StrategyRule?, value: String?) {
        super.setValue(item, value)
    }
}


/**
 * @author huyaro
 * @date 2023-1-3
 */
private class TestButtonAction(
    private val table: DbTable,
    private val tableModel: ListTableModel<StrategyRule>,
    private val excludeCols: Cell<JBTextField>,
    private val logger: LoggerComponent
) :
    AnAction("Test Rules", "Test whether the rules meet expectations", AllIcons.Actions.Compile) {

    override fun actionPerformed(e: AnActionEvent) {
        // Filter not blank value and table rules
        val tableRules = tableModel.items.filter { it.optValue.isNotBlank() && it.target == OptTarget.Table.name }
        val columnRules = tableModel.items.filter { it.optValue.isNotBlank() && it.target == OptTarget.Column.name }
        // handle table naming rule
        val namingTable = table.name to namingChoose(table.name, tableRules, OptTarget.Table)
        var tableOutLog = "[${namingTable.first}] ==> [${namingTable.second}]"
        // handle column naming rules(Exclude columns after superclass fields)
        val excludeColsArr = trimAndSplit(excludeCols.component.text)
        val cols = DasUtil.getColumns(table)
            .filter { col -> !excludeColsArr.contains(col.name) }
            .map { col ->
                mapOf(col.name to namingChoose(col.name, columnRules))
            }.reduce { acc, map -> acc.plus(map) }
        val maxLen = cols.maxOf { it.key.length }
        logger.flush("==========Apply naming rule testing (Test only one table)==========")
        val colOutLogs = cols
            ?.map { (col, cls) -> "    [${col.padEnd(maxLen)}]  ==>  [$cls]" }
            ?.joinToString(separator = "\n")
        // merge logs
        tableOutLog += "\n$colOutLogs"
        logger.flush(tableOutLog)
    }

    private fun namingChoose(source: String, rules: List<StrategyRule>, target: OptTarget = OptTarget.Column): String {
        return if (rules.isNotEmpty()) naming(source, rules, target)
        else namingByTarget(source, target)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}