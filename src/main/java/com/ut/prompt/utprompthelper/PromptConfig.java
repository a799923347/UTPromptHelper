package com.ut.prompt.utprompthelper;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "UTPromptHelperConfig",
    storages = @Storage("UTPromptHelper.xml")
)
public class PromptConfig implements PersistentStateComponent<PromptConfig> {
    
    private String customPrompt = "";
    private boolean useCustomPrompt = false;
    
    // 默认提示词
    public static final String DEFAULT_PROMPT = "基于代码库的现有单元测试风格，为以下变更生成单元测试，具体要求：\n" +
            "测试覆盖：确保测试覆盖所有变更的代码行，包括边界条件和异常场景。\n" +
            "测试类创建：优先创建新的测试类，保持与生产代码的包结构一致（例如测试类放在 src/test/java对应包下）。\n" +
            "测试运行器：优先使用 MockitoJUnitRunner进行依赖mock和测试执行。仅在必要时（如无法通过Mockito处理静态方法或final类时）才使用 PowerMockRunner。\n" +
            "Mock策略：避免过度mock；只在必要时mock静态方法或复杂依赖。使用Mockito进行对象mock，保持测试简洁。\n" +
            "验证与修复：生成测试代码后，自动检查编译错误和运行通过情况（例如通过IDE或构建工具验证），如有问题（如缺少依赖或语法错误），进行修复以确保测试可运行。\n" +
            "代码风格：遵循代码库现有的测试命名约定（如类名以 Test结尾）、断言风格（如使用AssertJ或JUnit断言）和结构（如使用 @Before初始化）。\n" +
            "需要覆盖的代码如下：";
    
    public static PromptConfig getInstance() {
        return ServiceManager.getService(PromptConfig.class);
    }
    
    @Nullable
    @Override
    public PromptConfig getState() {
        return this;
    }
    
    @Override
    public void loadState(@NotNull PromptConfig state) {
        XmlSerializerUtil.copyBean(state, this);
    }
    
    // Getters and Setters
    public String getCustomPrompt() {
        return customPrompt;
    }
    
    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }
    
    public boolean isUseCustomPrompt() {
        return useCustomPrompt;
    }
    
    public void setUseCustomPrompt(boolean useCustomPrompt) {
        this.useCustomPrompt = useCustomPrompt;
    }
    
    public String getEffectivePrompt() {
        return useCustomPrompt && !customPrompt.trim().isEmpty() ? customPrompt : DEFAULT_PROMPT;
    }
    
    public void resetToDefault() {
        this.customPrompt = "";
        this.useCustomPrompt = false;
    }
}

