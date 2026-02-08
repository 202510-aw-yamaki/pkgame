package com.example.PKGAME.controller;

import com.example.PKGAME.domain.GameState;
import com.example.PKGAME.service.GameService;
import com.example.PKGAME.viewmodel.StateViewModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final GameService gameService;

    public HomeController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/")
    public String index(Model model) {
        GameState state = gameService.getState();
        StateViewModel vm = new StateViewModel(
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
        model.addAttribute("vm", vm);
        return "index";
    }
}
