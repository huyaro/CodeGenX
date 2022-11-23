package dev.huyaro.gen.ui

import com.intellij.database.view.actions.font
import com.intellij.ide.util.PackageChooserDialog
import com.intellij.ide.util.TreeJavaClassChooserDialog
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import dev.huyaro.gen.model.*
import dev.huyaro.gen.util.buildOptions
import dev.huyaro.gen.util.camelCase
import dev.huyaro.gen.util.trimAndSplit
import java.awt.Font
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generator Dialog ui
 *
 * @author huyaro
 * @date 2022-11-06
 */
class GeneratorDialog constructor(
    private val project: Project?,
    private val options: GeneratorOptions,
    private val data: DataModel
) {

    private lateinit var outDir: Cell<TextFieldWithBrowseButton>
    private lateinit var textPkg: Cell<JBTextField>
    private lateinit var txtLogs: Cell<JBTextArea>
    private lateinit var txtPrefix: Cell<JBTextField>
    private lateinit var txtSuffix: Cell<JBTextField>

    fun initPanel(): DialogPanel {
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
                    textPkg.text(curOpts.rootPackage)
                }

                textPkg = textField().label("Package: ")
                    .bindText(options::rootPackage)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
                    .comment("Select root package")
                button("Choose...") {
                    val chooserDialog = PackageChooserDialog("Choose Package", project)
                    chooserDialog.show()
                    val psiPackage = chooserDialog.selectedPackage
                    if (psiPackage != null) {
                        textPkg.text(psiPackage.qualifiedName)
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
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()
                    .comment("Select the superclass of entity. e.g.: com.hello.entity.BaseEntity")
                button("Choose...") {
                    val classChooserDialog = TreeJavaClassChooserDialog("Choose SuperClass...", project)
                    classChooserDialog.show()
                    val selected = classChooserDialog.selected
                    if (selected != null) {
                        selected.qualifiedName?.let { pkg -> superCls.text(pkg) }
                    }
                }
            }.layout(RowLayout.PARENT_GRID)

            row("OutputDir: ") {
                outDir = textFieldWithBrowseButton(
                    project = project,
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                ).bindText(options::outputDir)
                    .gap(RightGap.SMALL)
                    .horizontalAlign(HorizontalAlign.FILL)
            }.layout(RowLayout.PARENT_GRID)
                .rowComment("Select the output directory. e.g.: /Project/src/main/java")

            twoColumnsRow({
                panel {
                    buttonsGroup {
                        row("FileMode: ") {
                            FileMode.values().forEach {
                                radioButton(it.name, it)
                            }
                        }.rowComment("Select the file output method")
                    }.bind(options::fileMode)

                    row("FileType: ") {
                        FileType.values().forEach {
                            checkBox(it.name).bindSelected({ options.fileTypes.contains(it) }) { stat ->
                                options.fileTypes = if (stat) options.fileTypes.plus(it)
                                else options.fileTypes.minus(it)
                            }
                        }
                    }.rowComment("Select the file type")
                }
            }, {
                panel {
                    buttonsGroup {
                        row("Language: ") {
                            Language.values().forEach {
                                radioButton(it.name, it)
                            }
                        }.rowComment("Select the language")
                    }.bind(options::language)

                    buttonsGroup {
                        row("Framework: ") {
                            Framework.values().forEach {
                                radioButton(it.name, it)
                            }
                        }.rowComment("Select the framework")
                    }.bind(options::framework)
                }
            }).layout(RowLayout.INDEPENDENT)

            collapsibleGroup("Filter Strategy") {
                row {
                    checkBox("UseRegex").bindSelected(
                        { options.columnFilter.useRegex },
                        { options.columnFilter.useRegex = it })
                        .gap(RightGap.COLUMNS)
                    textField().label("[Columns] exclude: ")
                        .bindText({ options.columnFilter.exclude }, { options.columnFilter.exclude = it })
                        .horizontalAlign(HorizontalAlign.FILL)
                }.rowComment("Use spaces to separate multiple items")
            }


            collapsibleGroup("Naming Strategy") {
                groupRowsRange("[Table] Remove Prefix Or Suffix") {
                    row {
                        txtPrefix = textField().label("Prefix: ")
                            .bindText({ options.tableNaming.prefix }, { options.tableNaming.prefix = it })
                        txtSuffix = textField().label("Suffix: ")
                            .bindText({ options.tableNaming.suffix }, { options.tableNaming.suffix = it })
                        button("Test It") {
                            namingTest()
                        }
                    }.rowComment("Use spaces to separate multiple items")
                }

                groupRowsRange("[Column] Remove Prefix Or Suffix") {
                    row {
                        textField().label("Prefix: ")
                            .bindText({ options.columnNaming.prefix }, { options.columnNaming.prefix = it })
                        textField().label("Suffix: ")
                            .bindText({ options.columnNaming.suffix }, { options.columnNaming.suffix = it })
                    }.rowComment("Use spaces to separate multiple items")
                }
            }

            group("Logs") {
                row {
                    txtLogs = textArea().rows(10)
                        .horizontalAlign(HorizontalAlign.FILL)
                        .bindText(options::logs)
                        .font(Font("Hack", Font.ROMAN_BASELINE, 14))
                    txtLogs.component.isEditable = false
                }
            }
        }
    }

    /**
     * 输出日志
     */
    fun flushLogs(lines: String) {
        val logComp = txtLogs.component
        val nowTime = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .format(LocalDateTime.now())
        val timeLine = ">>>>>>>>>>$nowTime"
        logComp.insert("\n$timeLine\n$lines\n", logComp.text.length)
        logComp.autoscrolls = true
    }

    /**
     * apply naming rules with table and output logs
     */
    private fun namingTest() {
        // remove prefix or suffix
        val removeFun: (String, String, Boolean) -> (String) =
            { s: String, extra: String, isPrefix: Boolean ->
                if (isPrefix) s.removePrefix(extra) else s.removeSuffix(extra)
            }

        // handle prefix and suffix
        val handleExtras: (Map<String, String>, String, Boolean) -> Map<String, String> =
            { tabMap: Map<String, String>, input: String, isPrefix: Boolean ->
                var tabMapping = tabMap
                val extras = trimAndSplit(input)
                if (extras.isNotEmpty()) {
                    tabMap.forEach { (tab, cls) ->
                        var clsName = cls
                        extras.forEach { extra ->
                            clsName = removeFun(clsName, extra, isPrefix)
                        }
                        tabMapping = tabMapping.plus(tab to clsName)
                    }
                }
                tabMapping
            }

        val tables = data.tables
        val prefixStr = txtPrefix.component.text
        val suffixStr = txtSuffix.component.text

        var tableMapping = tables.associate { it.name to it.name }
        val maxLen = tables.maxOf { it.name.length }
        if (prefixStr.isNotBlank() || suffixStr.isNotBlank()) {
            tableMapping = handleExtras(tableMapping, prefixStr, true)
            tableMapping = handleExtras(tableMapping, suffixStr, false)

            flushLogs("\n========Reset Mapping  [Table] And [Class]========")
            val logs = tableMapping.map { (tab, cls) ->
                "[${tab.padEnd(maxLen)}]  ==>  [${camelCase(cls, true)}]"
            }.joinToString(separator = "\n")
            flushLogs(logs)
        } else {
            val logs = tables.joinToString(separator = "\n") {
                "[${it.name.padEnd(maxLen)}]  ==>  [${camelCase(it.name, true)}]"
            }
            flushLogs("\n========Default Mapping [Table] And [Class]========")
            flushLogs(logs)
        }
    }
}


