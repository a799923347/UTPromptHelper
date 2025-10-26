package com.ut.prompt.utprompthelper;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
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

public class FileBasedPromptAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            showError(project, "No project found.");
            return;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || file.isDirectory()) {
            showError(project, "请选择一个文件");
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
            // 获取当前文件相对于项目根目录的路径
            String relativePath = file.getPath().substring(projectPath.length() + 1);
            
            Process process = Runtime.getRuntime().exec(
                "git diff master --unified=0 -w -- " + relativePath,
                new String[]{},
                new java.io.File(projectPath)
            );

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String currentFile = null;
            boolean inHunk = false;
            int hunkStart = 0;
            int hunkLinesCount = 0;
            List<String> hunkLines = new ArrayList<>();
            boolean skipFile = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("diff --git")) {
                    // 处理当前hunk
                    if (inHunk && !hunkLines.isEmpty() && currentFile != null) {
                        processHunk(currentFile, hunkStart, hunkLinesCount, hunkLines, fileChanges);
                        hunkLines.clear();
                    }
                    
                    // 开始新文件
                    String[] parts = line.split(" ");
                    if (parts.length >= 4) {
                        String filePath = parts[3].substring(2); // 移除"b/"前缀
                        currentFile = filePath;
                        skipFile = false;
                        inHunk = false;
                    }
                } else if (line.startsWith("Binary files") && line.contains("differ")) {
                    skipFile = true;
                } else if (line.startsWith("@@") && !skipFile && currentFile != null) {
                    // 遇到新的hunk，先处理当前hunk
                    if (inHunk && !hunkLines.isEmpty()) {
                        processHunk(currentFile, hunkStart, hunkLinesCount, hunkLines, fileChanges);
                        hunkLines.clear();
                    }
                    // 然后开始新的hunk
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
                    // 上下文行，继续处理但不结束hunk
                    // 只有在遇到新的@@时才结束当前hunk
                }
            }

            // 处理最后一个hunk
            if (inHunk && !hunkLines.isEmpty() && currentFile != null) {
                processHunk(currentFile, hunkStart, hunkLinesCount, hunkLines, fileChanges);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                showError(project, "Git diff failed with exit code: " + exitCode);
                return;
            }

            if (fileChanges.isEmpty()) {
                showError(project, "当前文件没有与master分支的差异");
                return;
            }

            // 显示结果对话框
            showResultDialog(project, fileChanges);
            
        } catch (Exception ex) {
            showError(project, "执行Git命令失败: " + ex.getMessage());
        }
    }

    private void processHunk(String fileName, int hunkStart, int hunkLinesCount, List<String> hunkLines, Map<String, List<String>> fileChanges) {
        // 检查hunk中是否包含重要的代码变更
        boolean hasSignificantChanges = false;
        int significantLines = 0;
        int totalLines = hunkLines.size();
        
        for (String line : hunkLines) {
            String content = line.substring(1).trim(); // 移除+或-前缀
            if (!isImportOrComment(content)) {
                hasSignificantChanges = true;
                significantLines++;
            }
        }
        
        // 如果hunk包含重要变更，则记录
        // 对于包含重要代码的hunk，即使比例较低也应该包含
        if (hasSignificantChanges && significantLines > 0) {
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
        
        // 检查是否是import语句
        if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) {
            return true;
        }
        
        // 检查是否是注释
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("*/")) {
            return true;
        }
        
        // 检查是否是空行或只有空白字符
        if (trimmed.isEmpty()) {
            return true;
        }
        
        return false;
    }

    private void showError(Project project, String message) {
        JOptionPane.showMessageDialog(null, message, "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void showResultDialog(Project project, Map<String, List<String>> fileChanges) {
        FilePromptDialog dialog = new FilePromptDialog(project, fileChanges);
        dialog.show();
    }

    private static class FilePromptDialog extends DialogWrapper {
        private final Project project;
        private final Map<String, List<String>> fileChanges;
        private final Map<String, JCheckBox> checkBoxMap = new HashMap<>();
        private final JTextArea promptTextArea = new JTextArea(3, 50);
        private final JTextArea previewArea = new JTextArea(10, 50);
        private final SimplePromptConfig config = SimplePromptConfig.getInstance();

        public FilePromptDialog(Project project, Map<String, List<String>> fileChanges) {
            super(project);
            this.project = project;
            this.fileChanges = fileChanges;
            setTitle("UT提示词助手");
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
            
            // 设置初始提示词
            promptTextArea.setText(config.getEffectivePrompt());
            promptTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            promptTextArea.setLineWrap(true);
            promptTextArea.setWrapStyleWord(true);
            JScrollPane promptScrollPane = new JScrollPane(promptTextArea);
            promptScrollPane.setPreferredSize(new Dimension(600, 80));
            
            // 创建提示词按钮面板
            JPanel promptButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton savePromptBtn = new JButton("💾 保存提示词");
            JButton resetPromptBtn = new JButton("🔄 重置为默认");
            JButton loadPromptBtn = new JButton("📂 重新加载提示词");
            
            promptButtonPanel.add(savePromptBtn);
            promptButtonPanel.add(resetPromptBtn);
            promptButtonPanel.add(loadPromptBtn);
            
            // 按钮事件处理
            savePromptBtn.addActionListener(e -> savePrompt());
            resetPromptBtn.addActionListener(e -> resetPrompt());
            loadPromptBtn.addActionListener(e -> loadPrompt());
            
            promptPanel.add(promptButtonPanel, BorderLayout.NORTH);
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
        
        private void savePrompt() {
            String currentPrompt = promptTextArea.getText().trim();
            if (currentPrompt.isEmpty()) {
                JOptionPane.showMessageDialog(null, "提示词不能为空！", "警告", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            config.setCustomPrompt(currentPrompt);
            config.setUseCustomPrompt(true);
            
            JOptionPane.showMessageDialog(null, "提示词已保存！", "成功", JOptionPane.INFORMATION_MESSAGE);
        }
        
        private void resetPrompt() {
            int result = JOptionPane.showConfirmDialog(
                null, 
                "确定要重置为默认提示词吗？这将覆盖当前的编辑内容。", 
                "确认重置", 
                JOptionPane.YES_NO_OPTION
            );
            
            if (result == JOptionPane.YES_OPTION) {
                config.resetToDefault();
                promptTextArea.setText(SimplePromptConfig.DEFAULT_PROMPT);
                updatePreview();
                JOptionPane.showMessageDialog(null, "已重置为默认提示词！", "成功", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        
        private void loadPrompt() {
            if (config.isUseCustomPrompt() && !config.getCustomPrompt().trim().isEmpty()) {
                promptTextArea.setText(config.getCustomPrompt());
                updatePreview();
                JOptionPane.showMessageDialog(null, "已加载保存的提示词！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "没有找到保存的提示词！", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
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
    }
}
