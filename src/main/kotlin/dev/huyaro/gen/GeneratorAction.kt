package dev.huyaro.gen

import com.intellij.database.psi.DbTable
import com.intellij.database.util.DasUtil
import com.intellij.database.view.getSelectedPsiElements
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBEmptyBorder
import dev.huyaro.gen.meta.Column
import dev.huyaro.gen.meta.Table
import dev.huyaro.gen.model.DataModel
import dev.huyaro.gen.model.GeneratorOptions
import dev.huyaro.gen.model.Language
import dev.huyaro.gen.model.TypeRegistration
import dev.huyaro.gen.ui.GeneratorDialog
import dev.huyaro.gen.ui.TypesDialog
import dev.huyaro.gen.util.camelCase
import dev.huyaro.gen.util.initOptionsByModule
import dev.huyaro.gen.util.trimAndSplit
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.awt.Dimension
import javax.swing.JComponent

/**
 * main dialog
 *
 * @author huyaro
 * @date 2022-11-06
 */
internal class GeneratorAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = getEventProject(e)
        val allModules = project?.let { ModuleManager.getInstance(it) }?.sortedModules
        val psiElements = getSelectedPsiElements(e.dataContext)
        // Prompt that a table must be selected
        if (allModules.isNullOrEmpty() || psiElements.isEmpty) {
            Messages.showMessageDialog(project, "Select at least one table!", "Warning", Messages.getWarningIcon())
            return
        }

        val selectedTables = psiElements
            .filter { it is DbTable }
            .map { it as DbTable }
            .toList()
        // remove root modules
        val validModules = allModules
            .filter { ModuleRootManager.getInstance(it).getSourceRoots(JavaSourceRootType.SOURCE).isNotEmpty() }
            .toList()
        val dataModel = DataModel(validModules, selectedTables)
        // show dialog
        DslConfigDialogUI(project, dataModel, "Code GeneratorX").show()
    }

}

private class DslConfigDialogUI(val project: Project, val dataModel: DataModel, dialogTitle: String) :
    DialogWrapper(project, null, true, IdeModalityType.MODELESS, false) {

    private val log = Logger.getInstance(GeneratorAction::class.java)
    private val typeService = TypeRegistration.getInstance()
    private val options = initLogs(initOptionsByModule(dataModel.modules[0]), dataModel.tables)

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent {
        val tabPanel = JBTabbedPane()
        tabPanel.minimumSize = Dimension(400, 400)
        tabPanel.preferredSize = Dimension(800, 800)

        val genInst = GeneratorDialog(project, options, dataModel)
        val genDialog = genInst.initPanel()

        val generatorPanel = panel {
            row {
                genDialog.border = JBEmptyBorder(10)
                scrollCell(genDialog)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
            }.resizableRow().layout(RowLayout.INDEPENDENT)

            row {
                button("Cancel") {
                    super.close(0, true)
                }.horizontalAlign(HorizontalAlign.RIGHT)
                    .resizableColumn()

                button("Generate") {
                    genDialog.apply()
                    // validate
                    if (options.author.isEmpty()) {
                        Messages.showMessageDialog("Author Can't be empty!", "Warning", null)
                    } else if (options.rootPackage.isEmpty()) {
                        Messages.showMessageDialog("Package Can't be empty!", "Warning", null)
                    } else if (options.fileTypes.isEmpty()) {
                        Messages.showMessageDialog("FileType Can't be empty!", "Warning", null)
                    } else {
                        // fetch table data
                        val tableList = dataModel.tables.map { buildTable(it) }
                        try {
                            val outLogs = CodeGenerator(project, options, tableList).generate()
                            genInst.flushLogs(outLogs)
                            notify("Generate code succeed!")
                        } catch (e: RuntimeException) {
                            log.error("Generate Error: ${e.message}")
                            notify("Generate code failed! ${e.message}", NotificationType.ERROR)
                        }
                    }
                }
            }.layout(RowLayout.PARENT_GRID)
                .topGap(TopGap.SMALL)
        }
        // generator dialog
        tabPanel.add("Generator", generatorPanel)

        // type dialog
        val typesPanel = TypesDialog(project).initPanel()
        tabPanel.add("RegisteredType", typesPanel)

        return tabPanel
    }

    /**
     * Popup Notifications
     */
    private fun notify(content: String, notifyType: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("dev.huyaro.genX.Notification")
            .createNotification(content, notifyType)
            .notify(project)
    }

    /**
     * build logs
     */
    private fun initLogs(genOpts: GeneratorOptions, tables: List<DbTable>): GeneratorOptions {
        val maxLen = tables.maxOf { it.name.length }
        val logs = tables.joinToString(separator = "\n") {
            "[${it.name.padEnd(maxLen)}]  ==>  [${camelCase(it.name, true)}]"
        }

        genOpts.logs += "============================Mapping [Table] And [Class]============================\n"
        genOpts.logs += logs
        return genOpts
    }


    /**
     * build full data for table metadata
     */
    private fun buildTable(dbTable: DbTable): Table {
        val columnFilter = options.columnFilter
        val lang = options.language
        val indices = DasUtil.getIndices(dbTable)
        val table = Table(name = dbTable.name, comment = dbTable.comment)

        val excludeCols = trimAndSplit(columnFilter.exclude)
        // filter columns and build table data
        var columns = DasUtil.getColumns(dbTable).toList()
        if (excludeCols.isNotEmpty()) {
            val compare: (String, String) -> Boolean = { s1: String, s2: String ->
                if (columnFilter.useRegex) Regex(s1).matches(s2) else s1.equals(s2, true)
            }
            excludeCols.forEach {
                columns = columns.filter { col -> !compare(it, col.name) }.toList()
            }
        }

        columns.forEach {
            val colName = it.name
            // There may be more than one identified type. e.g. tinyint unsigned
            val colType = it.dataType.typeName.let { name -> name.split(" ")[0] }
            val isPrimaryKey = DasUtil.isPrimary(it)
            val jvmType = typeService.getJvmType(colType)
            val jvmTypeName = if (lang == Language.KOTLIN) jvmType.simpleName else {
                if (isPrimaryKey) jvmType.javaPrimitiveType?.simpleName else jvmType.javaObjectType.simpleName
            }!!
            val column = Column(
                name = colName,
                typeName = colType,
                jvmType = jvmType,
                jvmTypeName = jvmTypeName,
                primaryKey = isPrimaryKey,
                autoGenerated = DasUtil.isAutoGenerated(it),
                length = it.dataType.length,
                scale = it.dataType.scale,
                nullable = !it.isNotNull,
                defaultValue = it.default,
                comment = it.comment,
                uniqKey = indices.filter { idx ->
                    !isPrimaryKey && idx.isUnique && DasUtil.containsName(colName, idx.columnsRef)
                }.size() > 0
            )
            when {
                column.primaryKey -> table.keyColumns.add(column.name)
                column.uniqKey -> table.refColumns.add(column.name)
            }
            table.columns.add(column)
        }
        return table
    }
}