package dev.huyaro.gen

import com.intellij.database.psi.DbTable
import com.intellij.database.view.getSelectedPsiElements
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import dev.huyaro.gen.model.DataModel
import dev.huyaro.gen.model.GeneratorOptions
import dev.huyaro.gen.model.TypeRegistration
import dev.huyaro.gen.ui.GeneratorDialog
import dev.huyaro.gen.ui.TypesDialog
import dev.huyaro.gen.util.buildOptions
import dev.huyaro.gen.util.buildTable
import dev.huyaro.gen.util.notify
import dev.huyaro.gen.util.toCamelCase
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
        DslConfigDialogUI(project, dataModel, "Code Generator X").show()
    }

    /**
     * only popup on the table
     */
    override fun update(e: AnActionEvent) {
        var visible = true
        val psiElements = getSelectedPsiElements(e.dataContext)
        if (psiElements.isEmpty || psiElements[0] !is DbTable) {
            visible = false
        }
        e.presentation.isEnabledAndVisible = visible
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

private class DslConfigDialogUI(val project: Project, val dataModel: DataModel, dialogTitle: String) :
    DialogWrapper(project, null, true, IdeModalityType.MODELESS, false) {

    private val log = Logger.getInstance(GeneratorAction::class.java)
    private val typeService = TypeRegistration.newInstance()
    private val options = initLogs(buildOptions(dataModel.modules[0]), dataModel.tables)

    init {
        super.init()
        title = dialogTitle
    }

    override fun createCenterPanel(): JComponent {
        val tabPanel = JBTabbedPane()
        tabPanel.minimumSize = Dimension(400, 450)
        tabPanel.preferredSize = Dimension(800, 900)

        val genDialog = GeneratorDialog(project, options, dataModel)
        val genPanel = genDialog.initPanel()

        val genScrollPanel = panel {
            row {
                genPanel.border = JBEmptyBorder(10)
                scrollCell(genPanel)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
            }
                .resizableRow()
                .layout(RowLayout.INDEPENDENT)

            row {
                button("Cancel") {
                    super.close(0, true)
                }.horizontalAlign(HorizontalAlign.RIGHT)
                    .resizableColumn()

                button("Generate") {
                    genDialog.optionPanel.apply()
                    // validate
                    when {
                        options.author.isEmpty() -> {
                            Messages.showMessageDialog("Author Can't be empty!", "Warning", null)
                        }

                        options.rootPackage.isEmpty() -> {
                            Messages.showMessageDialog("Package Can't be empty!", "Warning", null)
                        }

                        !options.entityType -> {
                            Messages.showMessageDialog("FileType Can't be empty!", "Warning", null)
                        }

                        else -> {
                            // fetch table data
                            val tableList = dataModel.tables.map {
                                buildTable(typeService, it, options.excludeCols, options.language)
                            }
                            try {
                                val outLogs = CodeGenerator(project, options, tableList).generate()
                                genDialog.logger.flush(outLogs)
                                notify(
                                    "Generate Code",
                                    "The code has been successfully generated!",
                                    project
                                )
                            } catch (e: RuntimeException) {
                                log.error("Generate Error: ${e.message}")
                                notify(
                                    "Generate Code",
                                    "Build failure!! <b>Error Message</b>: <p>${e.message}<p>",
                                    project,
                                    notifyType = NotificationType.ERROR
                                )
                            }
                        }
                    }
                }
            }.layout(RowLayout.PARENT_GRID)
                .topGap(TopGap.SMALL)
        }
        // generator dialog
        tabPanel.add("Generator", genScrollPanel)

        // type dialog
        val typesPanel = TypesDialog(project).initPanel()
        tabPanel.add("RegisteredType", typesPanel)

        return tabPanel
    }


    /**
     * build logs
     */
    private fun initLogs(genOpts: GeneratorOptions, tables: List<DbTable>): GeneratorOptions {
        val maxLen = tables.maxOf { it.name.length }
        val logs = tables.joinToString(separator = "\n") {
            "[${it.name.padEnd(maxLen)}]  ==>  [${toCamelCase(it.name, true)}]"
        }

        genOpts.logs += "============================Mapping [Table] to [Class]============================\n"
        genOpts.logs += logs
        return genOpts
    }


}