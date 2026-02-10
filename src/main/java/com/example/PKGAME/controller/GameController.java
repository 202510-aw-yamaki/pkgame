package com.example.PKGAME.controller;

import com.example.PKGAME.domain.GameEngine;
import com.example.PKGAME.domain.GameState;
import com.example.PKGAME.service.GameService;
import com.example.PKGAME.viewmodel.StateViewModel;
import com.example.PKGAME.web.PlayRequest;
import com.example.PKGAME.web.PlayResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/play")
    public ResponseEntity<PlayResponse> play(@RequestBody PlayRequest request) {
        String action = request.getAction();
        String key = request.getKey();
        if (action == null || key == null || key.length() != 1) {
            return ResponseEntity.badRequest()
                    .body(new PlayResponse(false, "action と key が不正です。", null, null, null,
                            toViewModel(gameService.getState())));
        }
        char ch = key.charAt(0);
        if (!gameService.isValidKey(ch)) {
            return ResponseEntity.badRequest()
                    .body(new PlayResponse(false, "不正なキーです。", null, null, null,
                            toViewModel(gameService.getState())));
        }

        GameEngine.TurnOutcome outcome;
        if ("SHOOT".equalsIgnoreCase(action)) {
            if (gameService.getState().getNextAction() != GameState.NextAction.PLAYER_SHOOT) {
                return ResponseEntity.badRequest()
                        .body(new PlayResponse(false, "現在のキーパーターンです。", null, null, null,
                                toViewModel(gameService.getState())));
            }
            outcome = gameService.playerShoot(ch);
        } else if ("KEEP".equalsIgnoreCase(action)) {
            if (gameService.getState().getNextAction() != GameState.NextAction.PLAYER_KEEP) {
                return ResponseEntity.badRequest()
                        .body(new PlayResponse(false, "現在のシューターターンです。", null, null, null,
                                toViewModel(gameService.getState())));
            }
            outcome = gameService.playerKeep(ch);
        } else {
            return ResponseEntity.badRequest()
                    .body(new PlayResponse(false, "action は SHOOT / KEEP を指定してください。", null, null, null,
                            toViewModel(gameService.getState())));
        }

        return ResponseEntity.ok(new PlayResponse(true, outcome.getComment(),
                String.valueOf(outcome.getShotKey()),
                String.valueOf(outcome.getKeepKey()),
                outcome.getResult().name(),
                toViewModel(outcome.getState())));
    }

    @PostMapping("/reset")
    public ResponseEntity<PlayResponse> reset() {
        GameState state = gameService.reset();
        return ResponseEntity.ok(new PlayResponse(true, "新しい試合を開始しました。",
                null, null, null, toViewModel(state)));
    }

    private StateViewModel toViewModel(GameState state) {
        return new StateViewModel(
                state.getPhase().name(),
                state.getNextAction().name(),
                state.getWinner().name(),
                state.getRound(),
                state.getSuddenRound(),
                state.getPlayerScore(),
                state.getAiScore(),
                state.isFinished(),
                state.getPlayerHistory(),
                state.getAiHistory()
        );
    }
}