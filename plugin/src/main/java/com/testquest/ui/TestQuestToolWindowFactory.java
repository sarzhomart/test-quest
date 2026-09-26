package com.testquest.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class TestQuestToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(
            @NotNull Project project,
            @NotNull ToolWindow toolWindow
    ) {
        QuestPanel panel = new QuestPanel(project);
        Content content = ContentFactory.getInstance()
                .createContent(panel.component(), "", false);
        content.setPreferredFocusableComponent(panel.preferredFocus());
        toolWindow.getContentManager().addContent(content);
    }
}
