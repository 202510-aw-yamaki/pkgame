package com.example.PKGAME.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GameEngine {
    public enum Result { BAR, SAVE, GOAL }

    private enum CommentCategory {
        PRE_NORMAL,
        PRE_PRESSURE,
        PRE_TREND_BIASED,
        PRE_TREND_SCATTER,
        PRE_AI_LEARNING,
        POST_GOAL,
        POST_SAVE_CATCH,
        POST_SAVE_DEFLECT,
        POST_BAR
    }

    public static class TurnOutcome {
        private final GameState state;
        private final char shotKey;
        private final char keepKey;
        private final Result result;
        private final String comment;
        private final boolean suddenDeath;
        private final boolean finished;
        private final GameState.Winner winner;

        public TurnOutcome(GameState state, char shotKey, char keepKey, Result result, String comment,
                           boolean suddenDeath, boolean finished, GameState.Winner winner) {
            this.state = state;
            this.shotKey = shotKey;
            this.keepKey = keepKey;
            this.result = result;
            this.comment = comment;
            this.suddenDeath = suddenDeath;
            this.finished = finished;
            this.winner = winner;
        }

        public GameState getState() {
            return state;
        }

        public char getShotKey() {
            return shotKey;
        }

        public char getKeepKey() {
            return keepKey;
        }

        public Result getResult() {
            return result;
        }

        public String getComment() {
            return comment;
        }

        public boolean isSuddenDeath() {
            return suddenDeath;
        }

        public boolean isFinished() {
            return finished;
        }

        public GameState.Winner getWinner() {
            return winner;
        }
    }

    private static final String[] ROWS = {"3456789", "wertyuio", "sdfghjkl", "xcvbnm,"};
    private static final List<Character> KEYS = new ArrayList<>();
    private static final Map<Character, Integer> KEY_INDEX = new HashMap<>();
    private static final Map<Character, Pos> KEY_POS = new HashMap<>();
    private static final Map<Character, Set<Character>> SAVE_SETS = new HashMap<>();
    private static final Random RNG = new Random();

    private static final Map<CommentCategory, String[]> COMMENT_BANK = initCommentBank();
    private static final int RECENT_LIMIT = 10;

    private final Deque<String> recentComments = new ArrayDeque<>();
    private int[] countShot;
    private int[] countKeep;
    private int totalShots = 0;
    private int totalKeeps = 0;

    public GameEngine() {
        initKeys();
        countShot = new int[KEYS.size()];
        countKeep = new int[KEYS.size()];
    }

    public GameState newGame() {
        return new GameState();
    }

    public TurnOutcome playerShoots(GameState state, char playerShot) {
        ensureAction(state, GameState.NextAction.PLAYER_SHOOT);
        char aiKeeper = chooseAiKeeperKey();
        CommentCategory preCat = pickPreCategory(state, state.getPlayerScore(), state.getAiScore(),
                state.getPlayerKicks(), state.getAiKicks(), state.getPhase());
        Result result = resolveKick(playerShot, aiKeeper);

        String preComment = pickComment(preCat, buildContext("プレイヤー", state, playerShot, aiKeeper));

        if (result == Result.GOAL) {
            state.setPlayerScore(state.getPlayerScore() + 1);
        }
        updatePlayerShot(playerShot);
        state.setPlayerKicks(state.getPlayerKicks() + 1);
        trackPlayerShot(state, playerShot);
        state.getPlayerHistory().add(resultSymbol(result));

        String postComment = pickComment(resultCategory(result, playerShot, aiKeeper),
                buildContext("プレイヤー", state, playerShot, aiKeeper));

        if (state.getPhase() == GameState.Phase.NORMAL) {
            GameState.Winner early = earlyDecisionCheck(state.getPlayerScore(), state.getAiScore(),
                    state.getPlayerKicks(), state.getAiKicks());
            if (early != GameState.Winner.NONE) {
                finish(state, early);
                return outcome(state, playerShot, aiKeeper, result, joinComments(preComment, postComment));
            }
        }

        state.setNextAction(GameState.NextAction.PLAYER_KEEP);
        return outcome(state, playerShot, aiKeeper, result, joinComments(preComment, postComment));
    }

    public TurnOutcome playerKeeps(GameState state, char playerKeeper) {
        ensureAction(state, GameState.NextAction.PLAYER_KEEP);
        char aiShot = chooseAiShooterKey();
        CommentCategory preCat = pickPreCategory(state, state.getAiScore(), state.getPlayerScore(),
                state.getAiKicks(), state.getPlayerKicks(), state.getPhase());
        Result result = resolveKick(aiShot, playerKeeper);

        String preComment = pickComment(preCat, buildContext("AI", state, aiShot, playerKeeper));

        if (result == Result.GOAL) {
            state.setAiScore(state.getAiScore() + 1);
        }
        updatePlayerKeep(playerKeeper);
        state.setAiKicks(state.getAiKicks() + 1);
        state.getAiHistory().add(resultSymbol(result));

        String postComment = pickComment(resultCategory(result, aiShot, playerKeeper),
                buildContext("AI", state, aiShot, playerKeeper));

        if (state.getPhase() == GameState.Phase.NORMAL) {
            GameState.Winner early = earlyDecisionCheck(state.getPlayerScore(), state.getAiScore(),
                    state.getPlayerKicks(), state.getAiKicks());
            if (early != GameState.Winner.NONE) {
                finish(state, early);
                return outcome(state, aiShot, playerKeeper, result, joinComments(preComment, postComment));
            }
        }

        advanceRoundOrSudden(state);
        return outcome(state, aiShot, playerKeeper, result, joinComments(preComment, postComment));
    }

    public boolean isValidKey(char ch) {
        return KEY_INDEX.containsKey(ch);
    }

    private void advanceRoundOrSudden(GameState state) {
        if (state.getPhase() == GameState.Phase.NORMAL) {
            if (state.getRound() < 5) {
                state.setRound(state.getRound() + 1);
            } else {
                if (state.getPlayerScore() != state.getAiScore()) {
                    GameState.Winner w = state.getPlayerScore() > state.getAiScore()
                            ? GameState.Winner.PLAYER
                            : GameState.Winner.AI;
                    finish(state, w);
                    return;
                }
                state.setPhase(GameState.Phase.SUDDEN_DEATH);
                state.setSuddenRound(1);
            }
        } else {
            if (state.getPlayerScore() != state.getAiScore()) {
                GameState.Winner w = state.getPlayerScore() > state.getAiScore()
                        ? GameState.Winner.PLAYER
                        : GameState.Winner.AI;
                finish(state, w);
                return;
            }
            state.setSuddenRound(state.getSuddenRound() + 1);
        }
        state.setNextAction(GameState.NextAction.PLAYER_SHOOT);
    }

    private TurnOutcome outcome(GameState state, char shotKey, char keepKey, Result result, String comment) {
        return new TurnOutcome(state, shotKey, keepKey, result, comment,
                state.getPhase() == GameState.Phase.SUDDEN_DEATH,
                state.isFinished(), state.getWinner());
    }

    private void finish(GameState state, GameState.Winner winner) {
        state.setWinner(winner);
        state.setFinished(true);
    }

    private void ensureAction(GameState state, GameState.NextAction expected) {
        if (state.isFinished()) {
            throw new IllegalStateException("Game already finished.");
        }
        if (state.getNextAction() != expected) {
            throw new IllegalStateException("Unexpected action: " + state.getNextAction());
        }
    }

    private void initKeys() {
        if (!KEYS.isEmpty()) {
            return;
        }
        for (int r = 0; r < ROWS.length; r++) {
            String row = ROWS[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                KEY_POS.put(ch, new Pos(r, c));
                KEY_INDEX.put(ch, KEYS.size());
                KEYS.add(ch);
            }
        }
        for (char k : KEYS) {
            SAVE_SETS.put(k, computeSaveSet(k));
        }
    }

    private Set<Character> getSaveSet(char keeperKey) {
        return SAVE_SETS.get(keeperKey);
    }

    private Set<Character> computeSaveSet(char keeperKey) {
        Set<Character> set = new HashSet<>();
        Pos pos = KEY_POS.get(keeperKey);
        if (pos == null) {
            return set;
        }
        addIfValid(set, pos.row, pos.col);
        addIfValid(set, pos.row, pos.col - 1);
        addIfValid(set, pos.row, pos.col + 1);
        addIfValid(set, pos.row - 1, pos.col);
        addIfValid(set, pos.row - 1, pos.col + 1);
        addIfValid(set, pos.row + 1, pos.col - 1);
        addIfValid(set, pos.row + 1, pos.col);
        return set;
    }

    private void addIfValid(Set<Character> set, int row, int col) {
        if (row < 0 || row >= ROWS.length) {
            return;
        }
        String rowStr = ROWS[row];
        if (col < 0 || col >= rowStr.length()) {
            return;
        }
        set.add(rowStr.charAt(col));
    }

    private boolean isBarKey(char shotKey) {
        Pos pos = KEY_POS.get(shotKey);
        if (pos == null) {
            return false;
        }
        String row = ROWS[pos.row];
        if (pos.row == 0) {
            return true;
        }
        if (pos.row == 1 || pos.row == 2) {
            return pos.col == 0 || pos.col == row.length() - 1;
        }
        if (pos.row == 3) {
            return pos.col == 0 || pos.col == row.length() - 1;
        }
        return false;
    }

    private Result resolveKick(char shotKey, char keeperKey) {
        if (isBarKey(shotKey) && RNG.nextInt(3) == 0) {
            return Result.BAR;
        }
        Set<Character> saveSet = getSaveSet(keeperKey);
        if (saveSet.contains(shotKey)) {
            return Result.SAVE;
        }
        return Result.GOAL;
    }

    private char chooseAiKeeperKey() {
        double epsilon = epsilonRate();
        if (RNG.nextDouble() < epsilon) {
            return KEYS.get(RNG.nextInt(KEYS.size()));
        }
        int bestScore = -1;
        List<Character> bestKeys = new ArrayList<>();
        for (char g : KEYS) {
            int score = 0;
            for (char k : getSaveSet(g)) {
                Integer idx = KEY_INDEX.get(k);
                if (idx != null) {
                    score += countShot[idx];
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestKeys.clear();
                bestKeys.add(g);
            } else if (score == bestScore) {
                bestKeys.add(g);
            }
        }
        return bestKeys.get(RNG.nextInt(bestKeys.size()));
    }

    private char chooseAiShooterKey() {
        double epsilon = epsilonRate();
        if (RNG.nextDouble() < epsilon) {
            return KEYS.get(RNG.nextInt(KEYS.size()));
        }
        if (totalKeeps == 0) {
            return KEYS.get(RNG.nextInt(KEYS.size()));
        }
        double bestScore = Double.POSITIVE_INFINITY;
        List<Character> bestKeys = new ArrayList<>();
        for (char s : KEYS) {
            int savedCount = 0;
            for (char g : KEYS) {
                if (getSaveSet(g).contains(s)) {
                    int idx = KEY_INDEX.get(g);
                    savedCount += countKeep[idx];
                }
            }
            double pSaved = (double) savedCount / (double) totalKeeps;
            if (pSaved < bestScore) {
                bestScore = pSaved;
                bestKeys.clear();
                bestKeys.add(s);
            } else if (pSaved == bestScore) {
                bestKeys.add(s);
            }
        }
        return bestKeys.get(RNG.nextInt(bestKeys.size()));
    }

    private double epsilonRate() {
        int n = totalShots + totalKeeps;
        double eps = 0.30 - n * 0.005;
        if (eps < 0.05) {
            eps = 0.05;
        }
        return eps;
    }

    private void updatePlayerShot(char shotKey) {
        int idx = KEY_INDEX.get(shotKey);
        countShot[idx]++;
        totalShots++;
    }

    private void updatePlayerKeep(char keepKey) {
        int idx = KEY_INDEX.get(keepKey);
        countKeep[idx]++;
        totalKeeps++;
    }

    private GameState.Winner earlyDecisionCheck(int playerScore, int aiScore, int playerKicks, int aiKicks) {
        int remainingPlayer = 5 - playerKicks;
        int remainingAI = 5 - aiKicks;
        if (playerScore > aiScore + remainingAI) {
            return GameState.Winner.PLAYER;
        }
        if (aiScore > playerScore + remainingPlayer) {
            return GameState.Winner.AI;
        }
        return GameState.Winner.NONE;
    }

    private void trackPlayerShot(GameState state, char shotKey) {
        state.getPlayerShotHistory().add(shotKey);
        state.getPlayerZoneHistory().add(zoneOf(shotKey));
    }

    private String zoneOf(char shotKey) {
        if (isLeftZone(shotKey)) {
            return "左";
        }
        if (isRightZone(shotKey)) {
            return "右";
        }
        Pos pos = KEY_POS.get(shotKey);
        if (pos != null) {
            if (pos.row == 0) {
                return "上";
            }
            if (pos.row == 3) {
                return "下";
            }
        }
        return "中央";
    }

    private boolean isLeftZone(char shotKey) {
        return shotKey == '3' || shotKey == 'w' || shotKey == 's' || shotKey == 'x';
    }

    private boolean isRightZone(char shotKey) {
        return shotKey == '9' || shotKey == 'o' || shotKey == 'l' || shotKey == ',';
    }

    private char resultSymbol(Result result) {
        return result == Result.GOAL ? 'O' : 'X';
    }

    private static class Context {
        String kicker;
        int round;
        int scoreP;
        int scoreA;
        char shotKey;
        char keepKey;
        String zone;
        String trend;
    }

    private Context buildContext(String kicker, GameState state, char shotKey, char keepKey) {
        Context ctx = new Context();
        ctx.kicker = kicker;
        ctx.round = state.getPhase() == GameState.Phase.SUDDEN_DEATH ? state.getSuddenRound() : state.getRound();
        ctx.scoreP = state.getPlayerScore();
        ctx.scoreA = state.getAiScore();
        ctx.shotKey = shotKey;
        ctx.keepKey = keepKey;
        ctx.zone = shotKey == '\0' ? "" : zoneOf(shotKey);
        ctx.trend = recentTrend(state);
        return ctx;
    }

    private String joinComments(String pre, String post) {
        if (pre.isEmpty()) {
            return post;
        }
        if (post.isEmpty()) {
            return pre;
        }
        return pre + " / " + post;
    }

    private CommentCategory resultCategory(Result result, char shotKey, char keepKey) {
        if (result == Result.BAR) {
            return CommentCategory.POST_BAR;
        }
        if (result == Result.SAVE) {
            return (shotKey == keepKey) ? CommentCategory.POST_SAVE_CATCH : CommentCategory.POST_SAVE_DEFLECT;
        }
        return CommentCategory.POST_GOAL;
    }

    private CommentCategory pickPreCategory(GameState state, int scoreSelf, int scoreOpp, int kicksSelf, int kicksOpp, GameState.Phase phase) {
        if (phase == GameState.Phase.SUDDEN_DEATH) {
            return CommentCategory.PRE_PRESSURE;
        }
        if (isUnderPressure(scoreSelf, scoreOpp, kicksSelf)) {
            return CommentCategory.PRE_PRESSURE;
        }
        String trend = recentTrend(state);
        if ("偏り".equals(trend)) {
            return CommentCategory.PRE_TREND_BIASED;
        }
        if ("散らし".equals(trend)) {
            return CommentCategory.PRE_TREND_SCATTER;
        }
        if (aiLearningFeel()) {
            return CommentCategory.PRE_AI_LEARNING;
        }
        return CommentCategory.PRE_NORMAL;
    }

    private boolean isUnderPressure(int scoreSelf, int scoreOpp, int kicksSelf) {
        int remaining = 5 - kicksSelf;
        if (scoreOpp > scoreSelf && (scoreOpp - scoreSelf) >= Math.max(1, remaining - 1)) {
            return true;
        }
        return false;
    }

    private boolean aiLearningFeel() {
        return epsilonRate() <= 0.15 || (totalShots + totalKeeps) >= 20;
    }

    private String recentTrend(GameState state) {
        int n = state.getPlayerZoneHistory().size();
        if (n < 3) {
            return "";
        }
        Map<String, Integer> counts = new HashMap<>();
        for (int i = n - 3; i < n; i++) {
            String z = state.getPlayerZoneHistory().get(i);
            counts.put(z, counts.getOrDefault(z, 0) + 1);
        }
        int max = 0;
        for (int v : counts.values()) {
            if (v > max) {
                max = v;
            }
        }
        if (max >= 2) {
            return "偏り";
        }
        return "散らし";
    }

    private String pickComment(CommentCategory cat, Context ctx) {
        String[] options = COMMENT_BANK.get(cat);
        if (options == null || options.length == 0) {
            return "";
        }
        String picked = "";
        int tries = 0;
        while (tries < 8) {
            String raw = options[RNG.nextInt(options.length)];
            picked = formatComment(raw, ctx);
            if (!recentComments.contains(picked)) {
                break;
            }
            tries++;
        }
        if (picked.isEmpty()) {
            picked = formatComment(options[0], ctx);
        }
        recentComments.addLast(picked);
        while (recentComments.size() > RECENT_LIMIT) {
            recentComments.removeFirst();
        }
        return picked;
    }

    private String formatComment(String template, Context ctx) {
        String s = template;
        s = s.replace("%k", ctx.kicker == null ? "" : ctx.kicker);
        s = s.replace("%r", String.valueOf(ctx.round));
        s = s.replace("%p", String.valueOf(ctx.scoreP));
        s = s.replace("%a", String.valueOf(ctx.scoreA));
        s = s.replace("%s", ctx.shotKey == '\0' ? "" : String.valueOf(ctx.shotKey));
        s = s.replace("%g", ctx.keepKey == '\0' ? "" : String.valueOf(ctx.keepKey));
        s = s.replace("%z", ctx.zone == null ? "" : ctx.zone);
        s = s.replace("%t", ctx.trend == null ? "" : ctx.trend);
        return s;
    }

    private static Map<CommentCategory, String[]> initCommentBank() {
        Map<CommentCategory, String[]> bank = new HashMap<>();
        bank.put(CommentCategory.PRE_NORMAL, new String[] {
                "さあ%r本目、%kのキッカーです。",
                "落ち着いて狙いたいところ。どこを蹴る？",
                "ここは決めたい一番。%kの一蹴です。"
        });
        bank.put(CommentCategory.PRE_PRESSURE, new String[] {
                "外せば厳しい。プレッシャーのかかる一本です。",
                "ここは決めないと苦しい展開。集中の一蹴。",
                "重圧の中でのキッカー。勝負どころです。"
        });
        bank.put(CommentCategory.PRE_TREND_BIASED, new String[] {
                "同じコースが続いています。ここで変えるか？",
                "偏りが見えてきました。読む側が有利かも。",
                "%zゾーンに偏っています。意表を突けるか？"
        });
        bank.put(CommentCategory.PRE_TREND_SCATTER, new String[] {
                "散らして揺さぶっています。キーパーは迷いそう。",
                "読み合いは五分。どこへ行くか読めません。",
                "コースを散らしてきています。次はどこだ？"
        });
        bank.put(CommentCategory.PRE_AI_LEARNING, new String[] {
                "AIが学習してきています。読み合いが深い。",
                "対戦数が増えた今、読みが鋭くなっています。",
                "蓄積データが効いてくる時間帯です。"
        });
        bank.put(CommentCategory.POST_GOAL, new String[] {
                "決まった！ゴール！",
                "コースに突き刺した！見事なゴール！",
                "強烈なシュートがゴールに吸い込まれた！"
        });
        bank.put(CommentCategory.POST_SAVE_CATCH, new String[] {
                "止めた！キャッチ！",
                "完璧にコースを当てた。キーパーの勝ち！",
                "がっちり捕えた！これ以上ないセーブ！"
        });
        bank.put(CommentCategory.POST_SAVE_DEFLECT, new String[] {
                "弾いた！ナイスセーブ！",
                "ギリギリで触った！危ないところだった！",
                "指先が届いた！見事な反応！"
        });
        bank.put(CommentCategory.POST_BAR, new String[] {
                "バーに弾かれた！",
                "惜しい！あと数センチ！",
                "クロスバーに嫌われた！"
        });
        return bank;
    }

    private static class Pos {
        int row;
        int col;
        Pos(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }
}
