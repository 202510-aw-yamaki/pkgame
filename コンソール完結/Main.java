package PKgame;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Main {
    private static final String[] ROWS = {"3456789", "wertyuio", "sdfghjkl", "xcvbnm,"};
    private static final List<Character> KEYS = new ArrayList<>();
    private static final Map<Character, Integer> KEY_INDEX = new HashMap<>();
    private static final Map<Character, Pos> KEY_POS = new HashMap<>();
    private static final Map<Character, Set<Character>> SAVE_SETS = new HashMap<>();
    private static final Random RNG = new Random();

    private static final int FIELD_HEIGHT = 13;
    private static final int FIELD_WIDTH = 27;
    private static final int GOAL_TOP = 1;
    private static final int GOAL_LEFT = 5;
    private static final int GOAL_RIGHT = 21;
    private static final int BALL_START_ROW = 11;
    private static final int BALL_START_COL = 13;
    private static final char[][] FIELD_BASE = initFieldBase();

    private static final List<Character> playerHistory = new ArrayList<>();
    private static final List<Character> aiHistory = new ArrayList<>();
    private static final List<Character> playerShotHistory = new ArrayList<>();
    private static final List<String> playerZoneHistory = new ArrayList<>();

    private static final Deque<String> recentComments = new ArrayDeque<>();
    private static final int RECENT_LIMIT = 10;

    private static int[] countShot;
    private static int[] countKeep;
    private static int totalShots = 0;
    private static int totalKeeps = 0;

    private enum Result { BAR, SAVE, GOAL }
    private enum Winner { NONE, PLAYER, AI }
    private enum Phase { NORMAL, SUDDEN_DEATH }

    private enum CommentCategory {
        PRE_NORMAL,
        PRE_PRESSURE,
        PRE_TREND_BIASED,
        PRE_TREND_SCATTER,
        PRE_AI_LEARNING,
        POST_GOAL,
        POST_SAVE_CATCH,
        POST_SAVE_DEFLECT,
        POST_BAR,
        SUDDEN_START,
        MATCH_END
    }

    private static class Pos {
        int row;
        int col;
        Pos(int row, int col) {
            this.row = row;
            this.col = col;
        }
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
        boolean sudden;
    }

    private static final Map<CommentCategory, String[]> COMMENT_BANK = initCommentBank();

    public static void main(String[] args) throws IOException {
        initKeys();
        countShot = new int[KEYS.size()];
        countKeep = new int[KEYS.size()];

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            int playerScore = 0;
            int aiScore = 0;
            int playerKicks = 0;
            int aiKicks = 0;
            int round = 1;
            boolean finished = false;
            boolean suddenStarted = false;

            playerHistory.clear();
            aiHistory.clear();
            playerShotHistory.clear();
            playerZoneHistory.clear();

            while (round <= 5 && !finished) {
                char playerShot = parseInputKey(br, "プレイヤー（キッカー）入力キー: ");
                char aiKeeper = chooseAiKeeperKey();
                Result res1 = resolveKick(playerShot, aiKeeper);

                Context preCtx1 = buildContext("プレイヤー", round, playerScore, aiScore, playerShot, aiKeeper, Phase.NORMAL);
                CommentCategory preCat1 = pickPreCategory(playerScore, aiScore, playerKicks, aiKicks, Phase.NORMAL);
                printComment(pickComment(preCat1, preCtx1));

                animateKick(playerShot, aiKeeper, res1, playerHistory);

                Context postCtx1 = buildContext("プレイヤー", round, playerScore, aiScore, playerShot, aiKeeper, Phase.NORMAL);
                printComment(pickComment(resultCategory(res1, playerShot, aiKeeper), postCtx1));

                if (res1 == Result.GOAL) {
                    playerScore++;
                }
                updatePlayerShot(playerShot);
                playerKicks++;
                trackPlayerShot(playerShot);
                printKick("プレイヤー", playerShot, aiKeeper, res1, playerScore, aiScore, false, round, playerKicks, aiKicks);

                Winner early1 = earlyDecisionCheck(playerScore, aiScore, playerKicks, aiKicks);
                if (early1 != Winner.NONE) {
                    Context endCtx = buildContext(early1 == Winner.PLAYER ? "プレイヤー" : "AI", round, playerScore, aiScore, '\0', '\0', Phase.NORMAL);
                    printComment(pickComment(CommentCategory.MATCH_END, endCtx));
                    printWinner(early1, playerScore, aiScore);
                    finished = true;
                    break;
                }

                char aiShot = chooseAiShooterKey();
                char playerKeeper = parseInputKey(br, "プレイヤー（キーパー）入力キー: ");
                Result res2 = resolveKick(aiShot, playerKeeper);

                Context preCtx2 = buildContext("AI", round, playerScore, aiScore, aiShot, playerKeeper, Phase.NORMAL);
                CommentCategory preCat2 = pickPreCategory(aiScore, playerScore, aiKicks, playerKicks, Phase.NORMAL);
                printComment(pickComment(preCat2, preCtx2));

                animateKick(aiShot, playerKeeper, res2, aiHistory);

                Context postCtx2 = buildContext("AI", round, playerScore, aiScore, aiShot, playerKeeper, Phase.NORMAL);
                printComment(pickComment(resultCategory(res2, aiShot, playerKeeper), postCtx2));

                if (res2 == Result.GOAL) {
                    aiScore++;
                }
                updatePlayerKeep(playerKeeper);
                aiKicks++;
                printKick("AI", aiShot, playerKeeper, res2, playerScore, aiScore, false, round, playerKicks, aiKicks);

                Winner early2 = earlyDecisionCheck(playerScore, aiScore, playerKicks, aiKicks);
                if (early2 != Winner.NONE) {
                    Context endCtx = buildContext(early2 == Winner.PLAYER ? "プレイヤー" : "AI", round, playerScore, aiScore, '\0', '\0', Phase.NORMAL);
                    printComment(pickComment(CommentCategory.MATCH_END, endCtx));
                    printWinner(early2, playerScore, aiScore);
                    finished = true;
                    break;
                }

                round++;
            }

            if (!finished) {
                if (playerScore != aiScore) {
                    Winner w = (playerScore > aiScore) ? Winner.PLAYER : Winner.AI;
                    Context endCtx = buildContext(w == Winner.PLAYER ? "プレイヤー" : "AI", round, playerScore, aiScore, '\0', '\0', Phase.NORMAL);
                    printComment(pickComment(CommentCategory.MATCH_END, endCtx));
                    printWinner(w, playerScore, aiScore);
                } else {
                    int suddenRound = 1;
                    if (!suddenStarted) {
                        Context suddenCtx = buildContext("", suddenRound, playerScore, aiScore, '\0', '\0', Phase.SUDDEN_DEATH);
                        printComment(pickComment(CommentCategory.SUDDEN_START, suddenCtx));
                        suddenStarted = true;
                    }
                    while (true) {
                        char playerShot = parseInputKey(br, "プレイヤー（キッカー）入力キー: ");
                        char aiKeeper = chooseAiKeeperKey();
                        Result res1 = resolveKick(playerShot, aiKeeper);

                        Context preCtx1 = buildContext("プレイヤー", suddenRound, playerScore, aiScore, playerShot, aiKeeper, Phase.SUDDEN_DEATH);
                        CommentCategory preCat1 = pickPreCategory(playerScore, aiScore, -1, -1, Phase.SUDDEN_DEATH);
                        printComment(pickComment(preCat1, preCtx1));

                        animateKick(playerShot, aiKeeper, res1, playerHistory);

                        Context postCtx1 = buildContext("プレイヤー", suddenRound, playerScore, aiScore, playerShot, aiKeeper, Phase.SUDDEN_DEATH);
                        printComment(pickComment(resultCategory(res1, playerShot, aiKeeper), postCtx1));

                        if (res1 == Result.GOAL) {
                            playerScore++;
                        }
                        updatePlayerShot(playerShot);
                        trackPlayerShot(playerShot);
                        printKick("プレイヤー", playerShot, aiKeeper, res1, playerScore, aiScore, true, suddenRound, -1, -1);

                        char aiShot = chooseAiShooterKey();
                        char playerKeeper = parseInputKey(br, "プレイヤー（キーパー）入力キー: ");
                        Result res2 = resolveKick(aiShot, playerKeeper);

                        Context preCtx2 = buildContext("AI", suddenRound, playerScore, aiScore, aiShot, playerKeeper, Phase.SUDDEN_DEATH);
                        CommentCategory preCat2 = pickPreCategory(aiScore, playerScore, -1, -1, Phase.SUDDEN_DEATH);
                        printComment(pickComment(preCat2, preCtx2));

                        animateKick(aiShot, playerKeeper, res2, aiHistory);

                        Context postCtx2 = buildContext("AI", suddenRound, playerScore, aiScore, aiShot, playerKeeper, Phase.SUDDEN_DEATH);
                        printComment(pickComment(resultCategory(res2, aiShot, playerKeeper), postCtx2));

                        if (res2 == Result.GOAL) {
                            aiScore++;
                        }
                        updatePlayerKeep(playerKeeper);
                        printKick("AI", aiShot, playerKeeper, res2, playerScore, aiScore, true, suddenRound, -1, -1);

                        if (playerScore != aiScore) {
                            Winner w = (playerScore > aiScore) ? Winner.PLAYER : Winner.AI;
                            Context endCtx = buildContext(w == Winner.PLAYER ? "プレイヤー" : "AI", suddenRound, playerScore, aiScore, '\0', '\0', Phase.SUDDEN_DEATH);
                            printComment(pickComment(CommentCategory.MATCH_END, endCtx));
                            printWinner(w, playerScore, aiScore);
                            break;
                        }
                        suddenRound++;
                    }
                }
            }

            System.out.print("もう一試合しますか？ (y/n): ");
            System.out.flush();
            String line = br.readLine();
            if (line == null || line.trim().isEmpty()) {
                break;
            }
            char c = Character.toLowerCase(line.trim().charAt(0));
            if (c != 'y') {
                break;
            }
        }
    }

    private static void initKeys() {
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

    private static char parseInputKey(BufferedReader br, String prompt) throws IOException {
        while (true) {
            System.out.print(prompt);
            System.out.flush();
            String line = br.readLine();
            if (line == null) {
                System.exit(0);
            }
            line = line.trim();
            if (line.length() == 1) {
                char ch = line.charAt(0);
                if (KEY_INDEX.containsKey(ch)) {
                    return ch;
                }
            }
            System.out.println("無効な入力です。許可されたキーで入力してください。");
        }
    }

    private static Set<Character> getSaveSet(char keeperKey) {
        return SAVE_SETS.get(keeperKey);
    }

    private static Set<Character> computeSaveSet(char keeperKey) {
        Set<Character> set = new HashSet<>();
        Pos pos = KEY_POS.get(keeperKey);
        if (pos == null) {
            return set;
        }
        addIfValid(set, pos.row, pos.col);      // center
        addIfValid(set, pos.row, pos.col - 1);  // left
        addIfValid(set, pos.row, pos.col + 1);  // right
        addIfValid(set, pos.row - 1, pos.col);      // up-left
        addIfValid(set, pos.row - 1, pos.col + 1);  // up-right
        addIfValid(set, pos.row + 1, pos.col - 1);  // down-left
        addIfValid(set, pos.row + 1, pos.col);      // down-right
        return set;
    }

    private static void addIfValid(Set<Character> set, int row, int col) {
        if (row < 0 || row >= ROWS.length) {
            return;
        }
        String rowStr = ROWS[row];
        if (col < 0 || col >= rowStr.length()) {
            return;
        }
        set.add(rowStr.charAt(col));
    }

    private static boolean isBarKey(char shotKey) {
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

    private static Result resolveKick(char shotKey, char keeperKey) {
        if (isBarKey(shotKey) && RNG.nextInt(3) == 0) {
            return Result.BAR;
        }
        Set<Character> saveSet = getSaveSet(keeperKey);
        if (saveSet.contains(shotKey)) {
            return Result.SAVE;
        }
        return Result.GOAL;
    }

    private static char chooseAiKeeperKey() {
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

    private static char chooseAiShooterKey() {
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

    private static double epsilonRate() {
        int n = totalShots + totalKeeps;
        double eps = 0.30 - n * 0.005;
        if (eps < 0.05) {
            eps = 0.05;
        }
        return eps;
    }

    private static void updatePlayerShot(char shotKey) {
        int idx = KEY_INDEX.get(shotKey);
        countShot[idx]++;
        totalShots++;
    }

    private static void updatePlayerKeep(char keepKey) {
        int idx = KEY_INDEX.get(keepKey);
        countKeep[idx]++;
        totalKeeps++;
    }

    private static Winner earlyDecisionCheck(int playerScore, int aiScore, int playerKicks, int aiKicks) {
        int remainingPlayer = 5 - playerKicks;
        int remainingAI = 5 - aiKicks;
        if (playerScore > aiScore + remainingAI) {
            return Winner.PLAYER;
        }
        if (aiScore > playerScore + remainingPlayer) {
            return Winner.AI;
        }
        return Winner.NONE;
    }

    private static void printKick(String kicker, char shotKey, char keeperKey, Result result,
                              int playerScore, int aiScore, boolean suddenDeath,
                              int round, int playerKicks, int aiKicks) {
    System.out.println("キッカー: " + kicker);
    System.out.println("プレイヤー役割: " + ("プレイヤー".equals(kicker) ? "キッカー" : "キーパー"));
    System.out.println("シュート: " + shotKey);
    System.out.println("キーパー: " + keeperKey);
    System.out.println("結果: " + resultMessage(kicker, shotKey, keeperKey, result));
    System.out.println("スコア: " + playerScore + " - " + aiScore);
    if (!suddenDeath) {
        int remainingPlayer = 5 - playerKicks;
        int remainingAI = 5 - aiKicks;
        System.out.println("ラウンド " + round + "/5, 残り本数: プレイヤー " + remainingPlayer + ", AI " + remainingAI);
    } else {
        System.out.println("ラウンド サドンデス " + round + ", 残り本数: サドンデス");
    }
    System.out.println();
}private static void printWinner(Winner w, int playerScore, int aiScore) {
    if (w == Winner.PLAYER) {
        System.out.println("勝者: プレイヤー");
    } else if (w == Winner.AI) {
        System.out.println("勝者: AI");
    }
    System.out.println("最終スコア: " + playerScore + " - " + aiScore);
    System.out.println();
}private static String resultMessage(String kicker, char shotKey, char keeperKey, Result result) {
    boolean playerKicker = "プレイヤー".equals(kicker);
    switch (result) {
        case BAR:
            return "ボールがバーにはじかれた";
        case SAVE:
            if (shotKey == keeperKey) {
                return playerKicker ? "がっちりキャッチされた！" : "がっちりキャッチした！";
            }
            return playerKicker ? "押さえ込まれて止められた！" : "飛びついてパンチングしてセーブ！";
        case GOAL:
            return "ゴール";
        default:
            return result.name();
    }
}private static char resultSymbol(Result result) {
        return result == Result.GOAL ? 'O' : 'X';
    }

    private static char[][] initFieldBase() {
    char[][] base = new char[FIELD_HEIGHT][FIELD_WIDTH];
    for (int r = 0; r < FIELD_HEIGHT; r++) {
        for (int c = 0; c < FIELD_WIDTH; c++) {
            base[r][c] = ' ';
        }
    }
    base[GOAL_TOP][GOAL_LEFT] = '+';
    base[GOAL_TOP][GOAL_RIGHT] = '+';
    for (int c = GOAL_LEFT + 1; c < GOAL_RIGHT; c++) {
        base[GOAL_TOP][c] = '-';
    }
    for (int r = GOAL_TOP + 1; r <= GOAL_TOP + 4; r++) {
        base[r][GOAL_LEFT] = '|';
        base[r][GOAL_RIGHT] = '|';
    }
    return base;
}private static void animateKick(char shotKey, char keeperKey, Result result, List<Character> history) {
        Pos target = shotTargetPos(shotKey);
        boolean directCatch = (result == Result.SAVE) && shotKey == keeperKey;
        boolean deflect = (result == Result.SAVE) && !directCatch;
        int frames = 10;
        int lastR = BALL_START_ROW;
        int lastC = BALL_START_COL;
        boolean added = false;
        for (int i = 1; i <= frames; i++) {
            int r;
            int c;
            if (!deflect) {
                r = lerp(BALL_START_ROW, target.row, i, frames);
                c = lerp(BALL_START_COL, target.col, i, frames);
            } else {
                int midR = lerp(BALL_START_ROW, target.row, 6, frames);
                int midC = lerp(BALL_START_COL, target.col, 6, frames);
                if (i <= 6) {
                    r = lerp(BALL_START_ROW, target.row, i, 6);
                    c = lerp(BALL_START_COL, target.col, i, 6);
                } else {
                    int deflectR = Math.min(FIELD_HEIGHT - 2, midR + 2);
                    int deflectC = Math.max(1, midC - 4);
                    r = lerp(midR, deflectR, i - 6, 4);
                    c = lerp(midC, deflectC, i - 6, 4);
                }
            }
            lastR = r;
            lastC = c;
            if (i == frames && history != null && !added) {
                history.add(resultSymbol(result));
                added = true;
            }
            drawFrame(r, c);
            sleepFrame(60);
        }
        if (directCatch) {
            drawFrame(lastR, lastC);
            sleepFrame(120);
        }
    }

    private static Pos shotTargetPos(char shotKey) {
        Pos pos = KEY_POS.get(shotKey);
        if (pos == null) {
            return new Pos(GOAL_TOP + 2, (GOAL_LEFT + GOAL_RIGHT) / 2);
        }
        int targetRow;
        if (pos.row == 0) {
            targetRow = GOAL_TOP;
        } else if (pos.row == 1) {
            targetRow = GOAL_TOP + 1;
        } else if (pos.row == 2) {
            targetRow = GOAL_TOP + 2;
        } else {
            targetRow = GOAL_TOP + 3;
        }
        int targetCol;
        if (pos.col <= 1) {
            targetCol = GOAL_LEFT + 2;
        } else if (pos.col >= ROWS[pos.row].length() - 2) {
            targetCol = GOAL_RIGHT - 2;
        } else {
            targetCol = (GOAL_LEFT + GOAL_RIGHT) / 2;
        }
        return new Pos(targetRow, targetCol);
    }

    private static int lerp(int start, int end, int step, int total) {
        return start + (end - start) * step / total;
    }

    private static void drawFrame(int ballR, int ballC) {
        clearScreen();
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < FIELD_HEIGHT; r++) {
            sb.append(FIELD_BASE[r]);
            sb.append('\n');
        }
        List<String> table = buildScoreboardLines();
        for (String line : table) {
            sb.append(line);
            sb.append('\n');
        }
        System.out.print(sb.toString());
        if (ballR >= 0 && ballR < FIELD_HEIGHT && ballC >= 0 && ballC < FIELD_WIDTH) {
            moveCursor(ballR + 1, ballC + 1);
            System.out.print("・");
        }
        moveCursor(FIELD_HEIGHT + table.size() + 1, 1);
        System.out.flush();
    }

    private static List<String> buildScoreboardLines() {
        int cols = Math.max(5, Math.max(playerHistory.size(), aiHistory.size()));
        int labelWidth = 10;
        int cellWidth = 4;
        String border = "+" + repeat("-", labelWidth) + "+" + repeat("-", cellWidth * cols) + "+";
        List<String> lines = new ArrayList<>();
        lines.add(border);
        StringBuilder header = new StringBuilder();
        header.append("|").append(padRight("", labelWidth)).append("|");
        for (int i = 1; i <= cols; i++) {
            header.append(padCenter(String.valueOf(i), cellWidth));
        }
        header.append("|");
        lines.add(header.toString());
        lines.add(border);
        lines.add(buildHistoryRow("P", playerHistory, cols, labelWidth, cellWidth));
        lines.add(border);
        lines.add(buildHistoryRow("AI", aiHistory, cols, labelWidth, cellWidth));
        lines.add(border);
        return lines;
    }

    private static String buildHistoryRow(String label, List<Character> history, int cols, int labelWidth, int cellWidth) {
        StringBuilder row = new StringBuilder();
        row.append("|").append(padRight(label, labelWidth)).append("|");
        for (int i = 0; i < cols; i++) {
            String cell = "";
            if (i < history.size()) {
                cell = String.valueOf(history.get(i));
            }
            row.append(padCenter(cell, cellWidth));
        }
        row.append("|");
        return row.toString();
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        if (sb.length() > width) {
            return sb.substring(0, width);
        }
        return sb.toString();
    }

    private static String padCenter(String s, int width) {
        if (s == null) {
            s = "";
        }
        int total = width - s.length();
        int left = total / 2;
        int right = total - left;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < left; i++) {
            sb.append(' ');
        }
        sb.append(s);
        for (int i = 0; i < right; i++) {
            sb.append(' ');
        }
        if (sb.length() > width) {
            return sb.substring(0, width);
        }
        return sb.toString();
    }

    private static void clearScreen() {
        System.out.print("\u001b[H\u001b[2J");
        System.out.flush();
    }

    private static void moveCursor(int row, int col) {
        System.out.print("\u001b[" + row + ";" + col + "H");
    }

    private static void sleepFrame(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    bank.put(CommentCategory.SUDDEN_START, new String[] {
            "サドンデス突入。ここからは1本で決着です。",
            "外した瞬間に決着。究極の一騎打ちです。",
            "緊張感が最高潮。サドンデスです。"
    });
    bank.put(CommentCategory.MATCH_END, new String[] {
            "紙一重の勝負でした。",
            "激闘の末に決着です。",
            "最後まで手に汗握る試合でした。"
    });
    return bank;
}private static CommentCategory resultCategory(Result result, char shotKey, char keeperKey) {
        if (result == Result.BAR) {
            return CommentCategory.POST_BAR;
        }
        if (result == Result.SAVE) {
            return (shotKey == keeperKey) ? CommentCategory.POST_SAVE_CATCH : CommentCategory.POST_SAVE_DEFLECT;
        }
        return CommentCategory.POST_GOAL;
    }

    private static CommentCategory pickPreCategory(int scoreSelf, int scoreOpp, int kicksSelf, int kicksOpp, Phase phase) {
        if (phase == Phase.SUDDEN_DEATH) {
            return CommentCategory.PRE_PRESSURE;
        }
        if (isUnderPressure(scoreSelf, scoreOpp, kicksSelf)) {
            return CommentCategory.PRE_PRESSURE;
        }
        String trend = recentTrend();
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

    private static boolean isUnderPressure(int scoreSelf, int scoreOpp, int kicksSelf) {
        if (kicksSelf < 0) {
            return false;
        }
        int remaining = 5 - kicksSelf;
        if (scoreOpp > scoreSelf && (scoreOpp - scoreSelf) >= Math.max(1, remaining - 1)) {
            return true;
        }
        return false;
    }

    private static boolean aiLearningFeel() {
        return epsilonRate() <= 0.15 || (totalShots + totalKeeps) >= 20;
    }

    private static String recentTrend() {
        int n = playerZoneHistory.size();
        if (n < 3) {
            return "";
        }
        Map<String, Integer> counts = new HashMap<>();
        for (int i = n - 3; i < n; i++) {
            String z = playerZoneHistory.get(i);
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

    private static void trackPlayerShot(char shotKey) {
        playerShotHistory.add(shotKey);
        playerZoneHistory.add(zoneOf(shotKey));
    }

    private static String zoneOf(char shotKey) {
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

    private static boolean isLeftZone(char shotKey) {
        return shotKey == '3' || shotKey == 'w' || shotKey == 's' || shotKey == 'x';
    }

    private static boolean isRightZone(char shotKey) {
        return shotKey == '9' || shotKey == 'o' || shotKey == 'l' || shotKey == ',';
    }

    private static Context buildContext(String kicker, int round, int scoreP, int scoreA, char shotKey, char keepKey, Phase phase) {
        Context ctx = new Context();
        ctx.kicker = kicker;
        ctx.round = round;
        ctx.scoreP = scoreP;
        ctx.scoreA = scoreA;
        ctx.shotKey = shotKey;
        ctx.keepKey = keepKey;
        ctx.zone = shotKey == '\0' ? "" : zoneOf(shotKey);
        ctx.trend = recentTrend();
        ctx.sudden = (phase == Phase.SUDDEN_DEATH);
        return ctx;
    }

    private static String pickComment(CommentCategory cat, Context ctx) {
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

    private static String formatComment(String template, Context ctx) {
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

    private static void printComment(String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        System.out.println(s);
    }
}
