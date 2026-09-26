package com.testquest.model;

import java.util.ArrayList;
import java.util.List;

public final class QuestTask {
    public String id = "";
    public String fingerprint = "";
    public TaskType type = TaskType.LOCATOR;
    public Difficulty difficulty = Difficulty.EASY;
    public String title = "";
    public String objective = "";
    public String targetFile = "";
    public String testClass = "";
    public String testMethod = "";
    public int points = 0;
    public List<String> acceptanceCriteria = new ArrayList<>();
    public List<ValidationRule> validationRules = new ArrayList<>();
    public String baselineFileHash = "";
    public String baselineSource = "";
    public int baselineTestCount = 0;
    public int baselineAssertionCount = 0;
    public long createdAtEpochMs = 0L;
    public TaskStatus status = TaskStatus.ACTIVE;
    public boolean pointsAwarded = false;

    public QuestTask() {
    }
}
