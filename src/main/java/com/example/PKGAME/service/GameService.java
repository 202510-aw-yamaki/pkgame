package com.example.PKGAME.service;

import com.example.PKGAME.domain.GameEngine;
import com.example.PKGAME.domain.GameState;
import org.springframework.stereotype.Service;

@Service
public class GameService {
    private GameEngine engine = new GameEngine();
    private GameState state = engine.newGame();

    public GameState newGame() {
        engine = new GameEngine();
        state = engine.newGame();
        return state;
    }

    public GameState reset() {
        return newGame();
    }

    public GameState getState() {
        return state;
    }

    public GameEngine.TurnOutcome playerShoot(char key) {
        return engine.playerShoots(state, key);
    }

    public GameEngine.TurnOutcome playerKeep(char key) {
        return engine.playerKeeps(state, key);
    }

    public boolean isValidKey(char key) {
        return engine.isValidKey(key);
    }
}
