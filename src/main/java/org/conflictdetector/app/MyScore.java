package org.conflictdetector.app;

public class MyScore {

    double trustness;
    double alpha;
    double punishment;
    int attempts;
    int loadedRules;
    int bannedRules;
    long startTime;

    public MyScore() {
        this.trustness = 1.0;
        this.alpha = 0.01;
        this.punishment = 0.0;
        this.attempts = 0;
        this.loadedRules = 0;
        this.bannedRules = 0;
        this.startTimer();
    }

    public MyScore(double trustness, double alpha, double punishment) {
        this.trustness = trustness;
        this.alpha = alpha;
        this.punishment = punishment;
        this.attempts = 0;
        this.loadedRules = 0;
        this.bannedRules = 0;
        this.startTimer();
    }

    public MyScore(double trustness, double alpha, double punishment, int attempts, int loadedRules, int bannedRules) {
        this.trustness = trustness;
        this.alpha = alpha;
        this.punishment = punishment;
        this.attempts = attempts;
        this.loadedRules = loadedRules;
        this.bannedRules = bannedRules;
        this.startTimer();
    }

    public double getTrustness() {
        return this.trustness;
    }

    public void setTrustness(double trustness) {
        this.trustness = trustness;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public double getPunishment() {
        return this.punishment;
    }

    public void setPunishment(double punishment) {
        this.punishment = punishment;
    }

    public int getAttempts() {
        return this.attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getLoadedRules() {
        return loadedRules;
    }

    public void setLoadedRules(int loadedRules) {
        this.loadedRules = loadedRules;
    }

    public int getBannedRules() {
        return bannedRules;
    }

    public void setBannedRules(int bannedRules) {
        this.bannedRules = bannedRules;
    }

    public void startTimer() {
        this.startTime = (long) (System.currentTimeMillis());
    }

    public long getTime() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - this.startTime) / 1000;
    }

}
