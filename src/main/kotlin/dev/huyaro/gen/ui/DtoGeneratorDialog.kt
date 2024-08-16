package dev.huyaro.gen.ui

import com.intellij.database.util.common.containsElements
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.utils.vfs.getPsiFile
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBDimension
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Dimension
import javax.swing.*


/**
 * dto generator dialog
 *
 * @author huyaro
 * @since  2024-03-29
 */
class DtoTypeGeneratorDialog(
    private val project: Project,
    actionName: String,
    private val vFile: VirtualFile,
    private val dtoModel: DtoTypeModel
) :
    DialogWrapper(project, true, IdeModalityType.MODELESS) {

    init {
        title = "Generate $actionName"
        isResizable = false
        super.init()
    }

    private lateinit var radioMacro: Cell<JBRadioButton>
    private lateinit var radioProp: Cell<JBRadioButton>

    override fun createCenterPanel(): JComponent {
        val panel = panel {

            group("Entity Info", indent = true) {
                row("Export: ") {
                    textFieldWithBrowseButton("Choose Entity", project) {
                        it.path
                    }.bindText(dtoModel::entity)
                        .resizableColumn()
                        .align(AlignX.FILL)
                }.layout(RowLayout.PARENT_GRID)

                row("To: ") {
                    textFieldWithBrowseButton("Choose Entity", project) {
                        it.path
                    }.bindText(dtoModel::toPackage)
                        .resizableColumn()
                        .align(AlignX.FILL)
                }.layout(RowLayout.PARENT_GRID)
            }

            buttonsGroup {
                row("Mode: ") {
                    radioMacro = radioButton(PropertyType.Macro.name, PropertyType.Macro)
                        .resizableColumn()
                    radioProp = radioButton(PropertyType.Properties.name, PropertyType.Properties)
                        .resizableColumn()
                }
                    .layout((RowLayout.PARENT_GRID))
                    .rowComment("Select the attribute type of the Dto")
            }.bind(dtoModel::propType)

            row {
                jbList(
                    listOf("#allScalars", "#allScalars(this)"),
                    textListCellRenderer(AllIcons.Ide.ConfigFile)
                )
            }
                .visibleIf(radioMacro.selected)
                .layout(RowLayout.PARENT_GRID)

            row {
                jbList(
                    // listOf("id", "name", "text", "createTime"),
                    (1..99).map { "Item $it" },
                    textListCellRenderer(AllIcons.Nodes.PropertyRead),
                     false
                )
            }
                .visibleIf(radioProp.selected)
                .layout(RowLayout.PARENT_GRID)

            row {
                checkBox("Use 'export'").bindSelected(dtoModel::useExported)
            }
        }
        panel.preferredSize = Dimension(400, 500)

        return panel
    }

    override fun doOKAction() {
        println("xxx")
    }


    /**
     * 对jbList进行dsl封装
     */
    private fun <T> Row.jbList(
        items: List<T>,
        renderer: ListCellRenderer<T>,
        singleMode: Boolean = true
    ): Cell<JBScrollPane> {
        val selectionModal =
            if (singleMode) ListSelectionModel.SINGLE_SELECTION
            else ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        val list = JBList(items)
        list.cellRenderer = renderer
        list.selectionMode = selectionModal

        val scroll = JBScrollPane(list)
        scroll.preferredSize = JBDimension(400, 400)

        return cell(scroll)
    }

    /**
     * Simplified version with one text cell
     */
    private fun <T> textListCellRenderer(icon: Icon): ListCellRenderer<T> {
        return PropertyCellRenderer(icon)
    }

    private fun getEntityProperties(): List<PropertyItem> {
        val psiFile = vFile.getPsiFile(project)
        if (psiFile is PsiJavaFile) {
            val psiClass = psiFile.classes.firstOrNull() ?: return emptyList()
            psiClass.annotations.containsElements { ann -> ann.qualifiedName.equals("") }
            if (psiClass.isInterface) {
                return psiClass.allMethods.map { PropertyItem(it.name, it.returnType?.canonicalText ?: "Void") }
            }
        } else if (psiFile is KtFile) {
            psiFile.annotations
        }
        return emptyList()
    }

    private fun getKtMethods(): List<PropertyItem> {
        val psiFile = vFile.getPsiFile(project)
        val methodList = mutableListOf<PropertyItem>()

        val interfaces = PsiTreeUtil.findChildrenOfType(psiFile, KtClassOrObject::class.java)
        for (element in interfaces) {
            if (element.isInterfaceClass()) {
                val methods = element.declarations.filterIsInstance<KtNamedFunction>()
                for (method in methods) {
                    // 这里对方法进行处理
                    println(method.name)
                }
            }
        }

        if (psiFile is KtFile && psiFile.isInterfaceClass()) {
            for (declaration in psiFile.declarations) {
                if (declaration is KtNamedFunction) {
                    methodList.add(PropertyItem(declaration.name!!, declaration.getReturnTypeReference()?.name!!))
                }
            }
        }
        return emptyList()
    }
}

private class PropertyCellRenderer<String>(
    private val textIcon: Icon
) : SimpleListCellRenderer<String>() {

    override fun customize(
        jlist: JList<out String>,
        value: String?,
        index: Int,
        seleted: Boolean,
        focus: Boolean
    ) {
        text = value?.toString() ?: ""
        icon = textIcon
    }
}

/**
 * 对话框的数据模型
 */
data class DtoTypeModel(
    var entity: String = "",
    var toPackage: String = "",
    var propType: PropertyType = PropertyType.Macro,
    var selectedProperties: List<String> = emptyList(),
    var useExported: Boolean = false
)

enum class PropertyType {
    Macro, Properties
}

internal data class PropertyItem(val name: String, val type: String)

internal enum class ActionMenu(val action: String, val identifier: String) {
    ViewDto("View Dto", ""),
    InputDto("Input Dto", "input"),
    SpecDto("Spec Dto", "specification")
}
