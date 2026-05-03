package com.github.slmpc.epsilon_baritone.elytra;

public final class TimelinessCounter {

    private int count;
    private final int updateInterval;
    private int lastUpdateTick = Integer.MIN_VALUE;

    public TimelinessCounter(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    public void accumulate(int currentTick) {
        if (currentTick - lastUpdateTick > updateInterval) {
            count = 1;
            lastUpdateTick = currentTick;
        } else {
            count++;
        }
    }

    public int getCount(int currentTick) {
        return currentTick - lastUpdateTick > updateInterval ? 0 : count;
    }

    public void clear(int currentTick) {
        count = 0;
        lastUpdateTick = currentTick;
    }
}

