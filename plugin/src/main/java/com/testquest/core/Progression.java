package com.testquest.core;

public final class Progression {
    private Progression() {
    }

    public static int levelForPoints(int points) {
        return Math.max(1, (points / 100) + 1);
    }

    public static int pointsInCurrentLevel(int points) {
        return Math.max(0, points % 100);
    }
}

