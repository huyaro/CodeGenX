package dev.huyaro.gen.ui

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.huyaro.gen.model.Operator
import dev.huyaro.gen.model.OptPosition
import dev.huyaro.gen.model.OptTarget
import dev.huyaro.gen.model.StrategyOptions
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel


/**
 * Strategy config
 *
 * @author huyaro
 * @date 2023-01-03
 */
class StrategyDialog(private var strategy: StrategyOptions) {

    private lateinit var defaultListModel: DefaultListModel<Int>

    fun initPanel(): DialogPanel {
        val stgPanel = DialogPanel(BorderLayout())
        stgPanel.add(initOptionPanel(), BorderLayout.WEST)
        stgPanel.add(initListPanel(), BorderLayout.CENTER)
        return stgPanel
    }

    private fun initOptionPanel(): DialogPanel {
        return panel {
            buttonsGroup {
                row("Operator: ") {
                    Operator.values().forEach {
                        radioButton(it.name, it)
                    }
                }.rowComment("Select related operation")
            }.bind(strategy::operator)

            buttonsGroup {
                row("Target: ") {
                    OptTarget.values().forEach {
                        radioButton(it.name, it)
                    }
                }.rowComment("Select related target")
            }.bind(strategy::target)

            buttonsGroup {
                row("Position: ") {
                    OptPosition.values().forEach {
                        radioButton(it.name, it)
                    }
                }.rowComment("Select related position")
            }.bind(strategy::position)

            row("Value: ") {
                textField().bindText(strategy::optValue)
            }
        }
    }

    private fun initListPanel(): JPanel {
        defaultListModel = DefaultListModel<Int>()
        for (i in 0..9) {
            defaultListModel.addElement(i)
        }
        val list = JBList(defaultListModel)

        // 修饰每一行的元素
        val coloredListCellRenderer: ColoredListCellRenderer<Int?> = object : ColoredListCellRenderer<Int?>() {
            override fun customizeCellRenderer(
                list: JList<out Int?>,
                value: Int?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                append("$value-suffix")
            }
        }
        list.cellRenderer = coloredListCellRenderer

        // 触发快速查找
        ListSpeedSearch(list)

        // 增加工具栏（新增按钮、删除按钮、上移按钮、下移按钮）
        val decorator = ToolbarDecorator.createDecorator(list)
        // 新增元素动作
        decorator.setAddAction { _ -> addAction() }

        return decorator.createPanel()
    }

    private fun addAction() {
        val newItem = Messages.showInputDialog("Input A Item", "Add", Messages.getInformationIcon())
        if (StringUtils.isNotBlank(newItem)) {
            defaultListModel.addElement(Integer.valueOf(newItem))
        }
    }
}
