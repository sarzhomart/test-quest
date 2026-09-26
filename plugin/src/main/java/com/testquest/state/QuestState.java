package com.testquest.state;

import com.testquest.model.QuestTask;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QuestState {
    public int totalPoints = 0;
    public String model = "gemini-3.6-flash";
    public boolean snapshotCaptureEnabled = true;
    public List<QuestTask> activeTasks = new ArrayList<>();
    public Set<String> completedFingerprints = new HashSet<>();

    public QuestState() {
    }
}

