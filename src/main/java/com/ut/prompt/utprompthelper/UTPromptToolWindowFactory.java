package com.ut.prompt.utprompthelper;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.awt.*;

public class UTPromptToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel buttonRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton globalBtn = new JButton("获取UT提示词（全局）");
        buttonRow1.add(globalBtn);

        JPanel buttonRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton currentFileBtn = new JButton("生成UT提示词（当前文件）");
        buttonRow2.add(currentFileBtn);

        panel.add(Box.createVerticalStrut(8));
        panel.add(buttonRow1);
        panel.add(buttonRow2);
        panel.add(Box.createVerticalGlue());

        // 绑定全局按钮到现有动作 GitCompareAction
        globalBtn.addActionListener(e -> {
            AnAction action = ActionManager.getInstance().getAction("GitCompareAction");
            if (action != null) {
                DataContext dataContext = SimpleDataContext.builder()
                        .add(CommonDataKeys.PROJECT, project)
                        .build();
                AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.TOOLWINDOW_CONTENT, dataContext);
                action.actionPerformed(event);
            }
        });

        // 绑定当前文件按钮到现有动作 FileBasedPromptAction
        currentFileBtn.addActionListener(e -> {
            AnAction action = ActionManager.getInstance().getAction("FileBasedPromptAction");
            if (action != null) {
                // 取当前选中文件
                com.intellij.openapi.vfs.VirtualFile[] selected = FileEditorManager.getInstance(project).getSelectedFiles();
                com.intellij.openapi.vfs.VirtualFile file = selected != null && selected.length > 0 ? selected[0] : null;

                DataContext dataContext = SimpleDataContext.builder()
                        .add(CommonDataKeys.PROJECT, project)
                        .add(CommonDataKeys.VIRTUAL_FILE, file)
                        .build();
                AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.TOOLWINDOW_CONTENT, dataContext);
                action.actionPerformed(event);
            }
        });

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "UT 提示词", false);
        toolWindow.getContentManager().addContent(content);
    }
}


