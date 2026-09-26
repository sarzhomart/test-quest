package com.testquest.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.testquest.model.QuestTask;
import com.testquest.core.Progression;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "TestQuestState", storages = @Storage("testQuest.xml"))
public final class QuestStateService implements PersistentStateComponent<QuestState> {
    private QuestState state = new QuestState();

    public static QuestStateService getInstance(Project project) {
        return project.getService(QuestStateService.class);
    }

    @Override
    public @Nullable QuestState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull QuestState loaded) {
        XmlSerializerUtil.copyBean(loaded, state);
    }

    public synchronized QuestState snapshot() {
        return state;
    }

    public synchronized void replaceTasks(List<QuestTask> tasks) {
        state.activeTasks = new java.util.ArrayList<>(tasks);
    }

    public synchronized boolean award(QuestTask task) {
        if (task.pointsAwarded || task.status == com.testquest.model.TaskStatus.COMPLETED
                || state.completedFingerprints.contains(task.fingerprint)) {
            return false;
        }
        task.pointsAwarded = true;
        task.status = com.testquest.model.TaskStatus.COMPLETED;
        state.completedFingerprints.add(task.fingerprint);
        state.totalPoints += task.points;
        return true;
    }

    public int level() {
        return levelForPoints(state.totalPoints);
    }

    public static int levelForPoints(int points) {
        return Progression.levelForPoints(points);
    }

    public static int pointsInCurrentLevel(int points) {
        return Progression.pointsInCurrentLevel(points);
    }
}
