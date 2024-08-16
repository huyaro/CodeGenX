package dev.huyaro.gen

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import dev.huyaro.gen.ui.DtoTypeGeneratorDialog
import dev.huyaro.gen.ui.DtoTypeModel


/**
 * dto types generator
 *
 * @author huyaro
 * @since  2024-03-29
 */
class DtoGeneratorAction : DumbAwareAction() {

    override fun actionPerformed(evt: AnActionEvent) {
        val project = getEventProject(evt)
        val actionName = evt.presentation.text
        // 获取编辑器
        // val editor = evt.getRequiredData(CommonDataKeys.EDITOR)
        // 当前打开的文件
        val virtualFile = evt.getRequiredData(CommonDataKeys.PSI_FILE).virtualFile
        // 根据选择的dto类型展示对话框
        val dtoModel = DtoTypeModel()
        DtoTypeGeneratorDialog(project!!, actionName, virtualFile, dtoModel).show()
    }

    /**
     * only popup on the dto file
     */
    override fun update(e: AnActionEvent) {
        var visible = true
        val virtualFile = e.getRequiredData(CommonDataKeys.PSI_FILE).virtualFile
        val extName = virtualFile.extension
        if (extName.isNullOrEmpty() || !extName.equals("dto", true)) {
            visible = false
        }
        e.presentation.isEnabledAndVisible = visible
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

