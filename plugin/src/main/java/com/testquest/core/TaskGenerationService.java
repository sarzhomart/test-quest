package com.testquest.core;

import com.intellij.openapi.project.Project;
import com.testquest.gemini.GeneratedTaskBatch;
import com.testquest.gemini.GeminiClient;
import com.testquest.gemini.TaskPrompt;
import com.testquest.model.QuestTask;
import com.testquest.state.ApiKeyStore;
import com.testquest.state.QuestState;
import com.testquest.state.QuestStateService;
import java.util.List;
import java.util.Set;

public final class TaskGenerationService {
    private final GeminiClient client = new GeminiClient();

    public List<QuestTask> generate(Project project) throws Exception {
        QuestStateService stateService = QuestStateService.getInstance(project);
        QuestState state = stateService.snapshot();
        ScanContext context = ProjectScanner.scan(project);
        Set<String> completed = Set.copyOf(state.completedFingerprints);
        GeneratedTaskBatch batch = client.generate(
                ApiKeyStore.get(project),
                state.model,
                TaskPrompt.systemInstruction(),
                TaskPrompt.userPrompt(context, completed),
                TaskPrompt.schema(),
                ProjectScanner.projectRoot(project)
        );
        List<QuestTask> tasks =
                TaskPolicy.finalizeGenerated(project, batch.tasks, completed);
        stateService.replaceTasks(tasks);
        return tasks;
    }
}
