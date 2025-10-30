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
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header 区域：标题 + 说明
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel title = new JLabel("UT 提示词");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 1));
        JLabel subtitle = new JLabel("基于 Git 变更生成 UT 提示词，支持全局与当前文件。");
        subtitle.setForeground(new Color(110, 110, 110));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);

        // Content 区域：操作按钮分组
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        // 全局操作块
        JPanel globalBlock = new JPanel();
        globalBlock.setLayout(new BoxLayout(globalBlock, BoxLayout.Y_AXIS));
        globalBlock.setBorder(BorderFactory.createTitledBorder("全局"));
        JButton globalBtn = new JButton("获取UT提示词（全局）");
        globalBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalBlock.add(globalBtn);
        JLabel globalHint = new JLabel("比较当前分支与 master，扫描非测试 Java 文件。");
        globalHint.setForeground(new Color(110, 110, 110));
        globalHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalBlock.add(Box.createVerticalStrut(4));
        globalBlock.add(globalHint);

        // 当前文件操作块
        JPanel fileBlock = new JPanel();
        fileBlock.setLayout(new BoxLayout(fileBlock, BoxLayout.Y_AXIS));
        fileBlock.setBorder(BorderFactory.createTitledBorder("当前文件"));
        JButton currentFileBtn = new JButton("生成UT提示词（当前文件）");
        currentFileBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        fileBlock.add(currentFileBtn);
        JLabel fileHint = new JLabel("分析编辑器选中文件与 master 的差异。");
        fileHint.setForeground(new Color(110, 110, 110));
        fileHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        fileBlock.add(Box.createVerticalStrut(4));
        fileBlock.add(fileHint);

        content.add(globalBlock);
        content.add(Box.createVerticalStrut(12));
        content.add(fileBlock);

        // 将 header 与 content 放入主面板
        mainPanel.add(header, BorderLayout.NORTH);
        mainPanel.add(content, BorderLayout.CENTER);

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
        Content twContent = contentFactory.createContent(mainPanel, "UT 提示词", false);
        toolWindow.getContentManager().addContent(twContent);
    }
}


