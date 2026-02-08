package com.example.PKGAME.viewmodel;

import java.util.List;

public class StateViewModel {
    private final String phase;
    private final String nextAction;
    private final String winner;
    private final int round;
    private final int suddenRound;
    private final int playerScore;
    private final int aiScore;
    private final boolean finished;
    private final List<Character> playerHistory;
    private final List<Character> aiHistory;

    public StateViewModel(String phase, String nextAction, String winner,
                          int round, int suddenRound, int playerScore, int aiScore,
                          boolean finished, List<Character> playerHistory, List<Character> aiHistory) {
        this.phase = phase;
        this.nextAction = nextAction;
        this.winner = winner;
        this.round = round;
        this.suddenRound = suddenRound;
        this.playerScore = playerScore;
        this.aiScore = aiScore;
        this.finished = finished;
        this.playerHistory = playerHistory;
        this.aiHistory = aiHistory;
    }

    public String getPhase() {
        return phase;
    }

    public String getNextAction() {
        return nextAction;
    }

    public String getWinner() {
        return winner;
    }

    public int getRound() {
        return round;
    }

    public int getSuddenRound() {
        return suddenRound;
    }

    public int getPlayerScore() {
        return playerScore;
    }

    public int getAiScore() {
        return aiScore;
    }

    public boolean isFinished() {
        return finished;
    }

    public List<Character> getPlayerHistory() {
        return playerHistory;
    }

    public List<Character> getAiHistory() {
        return aiHistory;
    }
}
