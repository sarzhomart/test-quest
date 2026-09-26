package com.testquest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.testquest.model.Difficulty;
import com.testquest.model.TaskType;
import org.junit.jupiter.api.Test;

class TaskPolicyTest {
    @Test
    void pointsAreOwnedByPolicyNotByTheModel() {
        assertEquals(10, TaskPolicy.points(TaskType.LOCATOR, Difficulty.EASY));
        assertEquals(60, TaskPolicy.points(TaskType.COVERAGE, Difficulty.HARD));
        assertEquals(75, TaskPolicy.points(TaskType.BEHAVIORAL, Difficulty.HARD));
    }

    @Test
    void levelUsesOneHundredPointSteps() {
        assertEquals(1, Progression.levelForPoints(99));
        assertEquals(2, Progression.levelForPoints(100));
        assertEquals(3, Progression.levelForPoints(250));
        assertEquals(50, Progression.pointsInCurrentLevel(250));
    }
}
