package com.example.PKGAME.domain;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    public enum Phase { NORMAL, SUDDEN_DEATH }
    public enum NextAction { PLAYER_SHOOT, PLAYER_KEEP }
    public enum Winner { NONE, PLAYER, AI }

    private Phase phase = Phase.NORMAL;
    private NextAction nextAction = NextAction.PLAYER_SHOOT;
    private Winner winner = Winner.NONE;

    private int round = 1;
    private int suddenRound = 1;
    private int playerScore = 0;
    private int aiScore = 0;
    private int playerKicks = 0;
    private int aiKicks = 0;

    private boolean finished = false;

    private final List<Character> playerHistory = new ArrayList<>();
    private final List<Character> aiHistory = new ArrayList<>();
    private final List<Character> playerShotHistory = new ArrayList<>();
    private final List<String> playerZoneHistory = new ArrayList<>();

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public NextAction getNextAction() {
        return nextAction;
    }

    public void setNextAction(NextAction nextAction) {
        this.nextAction = nextAction;
    }

    public Winner getWinner() {
        return winner;
    }

    public void setWinner(Winner winner) {
        this.winner = winner;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getSuddenRound() {
        return suddenRound;
    }

    public void setSuddenRound(int suddenRound) {
        this.suddenRound = suddenRound;
    }

    public int getPlayerScore() {
        return playerScore;
    }

    public void setPlayerScore(int playerScore) {
        this.playerScore = playerScore;
    }

    public int getAiScore() {
        return aiScore;
    }

    public void setAiScore(int aiScore) {
        this.aiScore = aiScore;
    }

    public int getPlayerKicks() {
        return playerKicks;
    }

    public void setPlayerKicks(int playerKicks) {
        this.playerKicks = playerKicks;
    }

    public int getAiKicks() {
        return aiKicks;
    }

    public void setAiKicks(int aiKicks) {
        this.aiKicks = aiKicks;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public List<Character> getPlayerHistory() {
        return playerHistory;
    }

    public List<Character> getAiHistory() {
        return aiHistory;
    }

    public List<Character> getPlayerShotHistory() {
        return playerShotHistory;
    }

    public List<String> getPlayerZoneHistory() {
        return playerZoneHistory;
    }
}
