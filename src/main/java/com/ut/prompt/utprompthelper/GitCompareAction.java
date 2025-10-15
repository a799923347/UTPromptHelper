package com.ut.prompt.utprompthelper;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitCompareAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            showError(project, "No project found.");
            return;
        }

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            showError(project, "Project base directory not found.");
            return;
        }

        String projectPath = baseDir.getPath();
        Map<String, List<String>> fileChanges = new HashMap<>();

        try {
            Process process = Runtime.getRuntime().exec(
                "git diff master --unified=0 -w",
                new String[]{},
                new java.io.File(projectPath)
            );

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String currentFile = null;
            boolean skipFile = false;
            boolean inHunk = false;
            List<String> hunkLines = new ArrayList<>();
            int hunkStart = 0;
            int hunkLinesCount = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("diff --git")) {
                    // 处理上一个文件的hunk
                    if (currentFile != null && !skipFile && !hunkLines.isEmpty()) {
                        processHunk(currentFile, hunkStart, hunkLinesCount, hunkLines, fileChanges);
                    }
                    
                    // 提取文件名
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        currentFile = parts[2].substring(2); // 移除"a/"前缀
                        // 检查是否为Java文件且不在测试目录下
                        if (currentFile.endsWith(".java") && !currentFile.contains("src/test/")) {
                            skipFile = false;
                        } else {
                            skipFile = true;
                        }
                    }
                    inHunk = false;
                    hunkLines.clear();
                } else if (line.startsWith("@@") && !skipFile && currentFile != null) {
                    // 解析行数范围
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        String newRange = parts[2].substring(1); // 移除"+"前缀
                        String[] rangeParts = newRange.split(",");
                        hunkStart = Integer.parseInt(rangeParts[0]);
                        hunkLinesCount = 1;
                        if (rangeParts.length > 1) {
                            hunkLinesCount = Integer.parseInt(rangeParts[1]);
                        }
                        inHunk = true;
                        hunkLines.clear();
                    }
                } else if (inHunk && (line.startsWith("+") || line.startsWith("-")) && !skipFile && currentFile != null) {
                    hunkLines.add(line);
                } else if (inHunk && line.startsWith(" ") && !skipFile && currentFile != null) {
                    // 上下文行，结束当前hunk
                    if (!hunkLines.isEmpty()) {
                        processHunk(currentFile, hunkStart, hunkLinesCount, hunkLines, fileChanges);
                        hunkLines.clear();
                    }
                    inHunk = false;
                }
            }
            
            // 处理最后一个文件的hunk
            if (currentFile != null && !skipFile && !hunkLines.isEmpty()) {
                processHunk(currentFile, hunkStart, hunkLinesCount, hunkLines, fileChanges);
            }

            process.waitFor();
            if (fileChanges.isEmpty()) {
                showInfo(project, "No changes found or all changes are in test files.");
            } else {
                showResultsDialog(project, fileChanges);
            }
        } catch (Exception ex) {
            showError(project, "Error executing git diff: " + ex.getMessage());
        }
    }

    private void processHunk(String fileName, int hunkStart, int hunkLinesCount, List<String> hunkLines, Map<String, List<String>> fileChanges) {
        // 检查hunk中是否只有导入语句、注释等非核心变更
        boolean hasSignificantChanges = false;
        
        for (String line : hunkLines) {
            String content = line.substring(1).trim(); // 移除+或-前缀
            if (!isImportOrComment(content)) {
                hasSignificantChanges = true;
                break;
            }
        }
        
        // 只有当hunk包含重要变更时才记录
        if (hasSignificantChanges) {
            int end = hunkStart + hunkLinesCount - 1;
            String changeInfo = hunkStart + "-" + end;
            fileChanges.computeIfAbsent(fileName, k -> new ArrayList<>()).add(changeInfo);
        }
    }

    private boolean isImportOrComment(String content) {
        if (content == null || content.trim().isEmpty()) {
            return true;
        }
        
        String trimmed = content.trim();
        
        // 检查是否为导入语句
        if (trimmed.startsWith("import ")) {
            return true;
        }
        
        // 检查是否为注释
        if (trimmed.startsWith("//") || 
            trimmed.startsWith("/*") || 
            trimmed.startsWith("*") ||
            trimmed.startsWith("*/")) {
            return true;
        }
        
        // 检查是否为包声明
        if (trimmed.startsWith("package ")) {
            return true;
        }
        
        // 检查是否为空行或只有空白字符
        if (trimmed.isEmpty()) {
            return true;
        }
        
        return false;
    }

    private void showError(Project project, String message) {
        JOptionPane.showMessageDialog(
            null,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    private void showInfo(Project project, String message) {
        JOptionPane.showMessageDialog(
            null,
            message,
            "Info",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showResultsDialog(Project project, Map<String, List<String>> fileChanges) {
        DialogWrapper dialog = new DialogWrapper(project) {
            private final Map<String, JCheckBox> checkBoxMap = new HashMap<>();
            private final JTextArea previewArea = new JTextArea(10, 50);
            private final JTextArea promptTextArea = new JTextArea(3, 50);
            private final String defaultPrompt = "基于当前代码库的单测代码风格 生成单测 优先创建新的测试类 要求要覆盖所有改动到的代码 优先使用MockitoJUnitRunner 必要时再使用PowerMockRunner 必要时才mock静态方法 单测生成后检查下编译报错和运行通过情况 有的话进行修复 需要覆盖的代码如下";
            
            {
                setTitle("Git Compare Results - 按文件汇总");
                init();
            }

            @Override
            protected @Nullable JComponent createCenterPanel() {
                JPanel mainPanel = new JPanel();
                mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
                
                // 创建文件选择区域
                JPanel filePanel = new JPanel(new BorderLayout());
                filePanel.setBorder(BorderFactory.createTitledBorder("选择要生成单元测试的文件"));
                
                JPanel checkBoxPanel = new JPanel();
                checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));
                
                // 添加全选/全不选按钮
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JButton selectAllBtn = new JButton("全选");
                JButton selectNoneBtn = new JButton("全不选");
                buttonPanel.add(selectAllBtn);
                buttonPanel.add(selectNoneBtn);
                
                // 为每个文件创建选择框
                for (Map.Entry<String, List<String>> entry : fileChanges.entrySet()) {
                    String fileName = entry.getKey();
                    List<String> changes = entry.getValue();
                    
                    JCheckBox checkBox = new JCheckBox();
                    checkBox.setSelected(true); // 默认选中
                    
                    // 创建文件信息面板
                    JPanel fileInfoPanel = new JPanel(new BorderLayout());
                    fileInfoPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                    
                    // 创建可选择的文本区域显示文件信息
                    JTextArea fileInfoText = new JTextArea();
                    fileInfoText.setEditable(false);
                    fileInfoText.setOpaque(false);
                    fileInfoText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                    fileInfoText.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
                    
                    // 构建文件信息文本
                    StringBuilder fileInfo = new StringBuilder();
                    fileInfo.append(fileName).append("\n");
                    fileInfo.append("变更行数: ").append(changes.size()).append(" 处\n");
                    fileInfo.append("位置: ").append(String.join(", ", changes));
                    fileInfoText.setText(fileInfo.toString());
                    
                    fileInfoPanel.add(checkBox, BorderLayout.WEST);
                    fileInfoPanel.add(fileInfoText, BorderLayout.CENTER);
                    
                    checkBoxMap.put(fileName, checkBox);
                    checkBoxPanel.add(fileInfoPanel);
                }
                
                // 全选/全不选按钮事件
                selectAllBtn.addActionListener(e -> {
                    for (JCheckBox checkBox : checkBoxMap.values()) {
                        checkBox.setSelected(true);
                    }
                    updatePreview();
                });
                
                selectNoneBtn.addActionListener(e -> {
                    for (JCheckBox checkBox : checkBoxMap.values()) {
                        checkBox.setSelected(false);
                    }
                    updatePreview();
                });
                
                // 添加选择框变化监听器
                for (JCheckBox checkBox : checkBoxMap.values()) {
                    checkBox.addActionListener(e -> updatePreview());
                }
                
                // 添加提示词编辑框变化监听器
                promptTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    @Override
                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        updatePreview();
                    }
                    
                    @Override
                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        updatePreview();
                    }
                    
                    @Override
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        updatePreview();
                    }
                });
                
                JScrollPane fileScrollPane = new JScrollPane(checkBoxPanel);
                fileScrollPane.setPreferredSize(new Dimension(600, 300));
                
                filePanel.add(buttonPanel, BorderLayout.NORTH);
                filePanel.add(fileScrollPane, BorderLayout.CENTER);
                
                // 创建提示词编辑区域
                JPanel promptPanel = new JPanel(new BorderLayout());
                promptPanel.setBorder(BorderFactory.createTitledBorder("UT提示词 (可编辑)"));
                
                promptTextArea.setText(defaultPrompt);
                promptTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                promptTextArea.setLineWrap(true);
                promptTextArea.setWrapStyleWord(true);
                JScrollPane promptScrollPane = new JScrollPane(promptTextArea);
                promptScrollPane.setPreferredSize(new Dimension(600, 80));
                promptPanel.add(promptScrollPane, BorderLayout.CENTER);
                
                // 创建预览区域
                JPanel previewPanel = new JPanel(new BorderLayout());
                previewPanel.setBorder(BorderFactory.createTitledBorder("预览 (将复制的内容)"));
                
                previewArea.setEditable(false);
                previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                JScrollPane previewScrollPane = new JScrollPane(previewArea);
                previewPanel.add(previewScrollPane, BorderLayout.CENTER);
                
                // 创建按钮面板
                JPanel copyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton copyBtn = new JButton("📋 复制选中内容的UT提示词到剪贴板");
                copyBtn.addActionListener(e -> copyToClipboard());
                
                JButton openCursorBtn = new JButton("🚀 在 Cursor 中打开");
                openCursorBtn.addActionListener(e -> openInCursor());
                
                copyPanel.add(copyBtn);
                copyPanel.add(openCursorBtn);
                
                // 组装主面板
                mainPanel.add(filePanel);
                mainPanel.add(Box.createVerticalStrut(10));
                mainPanel.add(promptPanel);
                mainPanel.add(Box.createVerticalStrut(10));
                mainPanel.add(previewPanel);
                mainPanel.add(Box.createVerticalStrut(10));
                mainPanel.add(copyPanel);
                
                // 初始化预览
                updatePreview();
                
                return mainPanel;
            }
            
            private void updatePreview() {
                StringBuilder content = new StringBuilder();
                content.append(promptTextArea.getText()).append("\n");
                content.append("\n");
                
                for (Map.Entry<String, List<String>> entry : fileChanges.entrySet()) {
                    String fileName = entry.getKey();
                    List<String> changes = entry.getValue();
                    JCheckBox checkBox = checkBoxMap.get(fileName);
                    
                    if (checkBox != null && checkBox.isSelected()) {
                        content.append(fileName).append("\n");
                        content.append("   变更行数: ").append(changes.size()).append(" 处\n");
                        content.append("   具体位置: ");
                        
                        for (int i = 0; i < changes.size(); i++) {
                            if (i > 0) {
                                content.append(", ");
                            }
                            content.append(changes.get(i));
                        }
                        content.append("\n\n");
                    }
                }
                
                previewArea.setText(content.toString());
            }
            
            private void copyToClipboard() {
                String content = previewArea.getText();
                if (content.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "没有选中任何文件！", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(content);
                clipboard.setContents(selection, null);
                
                JOptionPane.showMessageDialog(null, "内容已复制到剪贴板！", "成功", JOptionPane.INFORMATION_MESSAGE);
            }
            
            private void openInCursor() {
                try {
                    // 获取当前项目路径
                    String projectPath = project.getBasePath();
                    if (projectPath == null) {
                        JOptionPane.showMessageDialog(null, "无法获取项目路径！", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
                    // 构建 Cursor 命令
                    String cursorCommand = "cursor " + projectPath;
                    
                    // 在 macOS 上使用 open 命令打开 Cursor
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        cursorCommand = "open -a Cursor " + projectPath;
                    } else if (os.contains("win")) {
                        cursorCommand = "cursor " + projectPath;
                    } else {
                        cursorCommand = "cursor " + projectPath;
                    }
                    
                    // 执行命令
                    Process process = Runtime.getRuntime().exec(cursorCommand);
                    process.waitFor();
                    
                    JOptionPane.showMessageDialog(null, "正在 Cursor 中打开项目...", "成功", JOptionPane.INFORMATION_MESSAGE);
                    
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "打开 Cursor 失败: " + ex.getMessage() + "\n\n请确保已安装 Cursor 编辑器", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        dialog.show();
    }
}
