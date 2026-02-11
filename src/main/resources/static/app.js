(() => {
    const app = document.getElementById("app");
    const state = {
        phase: app.dataset.phase || "NORMAL",
        nextAction: app.dataset.next || "PLAYER_SHOOT",
        winner: app.dataset.winner || "NONE",
        round: Number(app.dataset.round || 1),
        suddenRound: Number(app.dataset.sudden || 1),
        playerScore: Number(app.dataset.pscore || 0),
        aiScore: Number(app.dataset.ascore || 0),
        finished: app.dataset.finished === "true",
        playerHistory: parseHistory(app.dataset.phistory),
        aiHistory: parseHistory(app.dataset.ahistory)
    };

    const rows = ["3456789", "wertyuio", "sdfghjkl", "xcvbnm,"];
    const keypad = document.getElementById("keypad");
    const message = document.getElementById("message");
    const lastResult = document.getElementById("last-result");
    const scorePlayer = document.getElementById("score-player");
    const scoreAi = document.getElementById("score-ai");
    const metaPlayer = document.getElementById("meta-player");
    const metaAi = document.getElementById("meta-ai");
    const phaseEl = document.getElementById("phase");
    const roundEl = document.getElementById("round");
    const nextActionEl = document.getElementById("next-action");
    const winnerEl = document.getElementById("winner");
    const historyPlayer = document.getElementById("history-player");
    const historyAi = document.getElementById("history-ai");
    const resetBtn = document.getElementById("reset");
    const newMatchBtn = document.querySelector(".new-match");
    const goal = document.querySelector(".goal");
    const goalFrame = document.querySelector(".goal-frame");
    const ball = document.getElementById("ball");
    const keeper = document.getElementById("keeper");

    let pending = false;
    let lastOutcome = null;
    const ANIM_TOTAL_MS = 1200;

    function parseHistory(raw) {
        if (!raw) return [];
        const trimmed = raw.replace(/^\[|\]$/g, "").trim();
        if (!trimmed) return [];
        return trimmed.split(",").map((s) => s.trim().replace(/['\"]/g, "")).filter(Boolean);
    }

    function buildKeypad() {
        keypad.innerHTML = "";
        rows.forEach((row) => {
            const rowEl = document.createElement("div");
            rowEl.className = "key-row";
            row.split("").forEach((ch) => {
                const btn = document.createElement("button");
                btn.className = "key";
                btn.textContent = ch;
                btn.dataset.key = ch;
                btn.addEventListener("click", onKeyClick);
                rowEl.appendChild(btn);
            });
            keypad.appendChild(rowEl);
        });
    }

    function setMessage(text, isError = false) {
        message.textContent = text || "";
        message.style.color = isError ? "#c0352b" : "#1f6f8b";
    }

    function render() {
        scorePlayer.textContent = state.playerScore;
        scoreAi.textContent = state.aiScore;
        phaseEl.textContent = state.phase === "SUDDEN_DEATH" ? "SUDDEN" : "NORMAL";
        roundEl.textContent = state.phase === "SUDDEN_DEATH"
            ? `SD ${state.suddenRound}`
            : state.round;
        nextActionEl.textContent = state.nextAction === "PLAYER_SHOOT" ? "SHOOT" : "KEEP";
        winnerEl.textContent = state.finished ? state.winner : "NONE";

        historyPlayer.innerHTML = "";
        state.playerHistory.forEach((h) => historyPlayer.appendChild(historyChip(h)));
        historyAi.innerHTML = "";
        state.aiHistory.forEach((h) => historyAi.appendChild(historyChip(h)));
        updateMetaMarks();

        if (lastOutcome) {
            lastResult.textContent = `${lastOutcome.shotKey} vs ${lastOutcome.keepKey} : ${toResultText(lastOutcome.result)}`;
        } else {
            lastResult.textContent = "結果待ち";
        }

        document.querySelectorAll(".key").forEach((btn) => {
            btn.disabled = pending || state.finished;
        });
    }

    function historyChip(value) {
        const chip = document.createElement("div");
        chip.className = "history-chip";
        chip.textContent = value;
        return chip;
    }

    function updateMetaMarks() {
        if (!metaPlayer || !metaAi) return;
        metaPlayer.textContent = toRecentMarks(state.playerHistory, 5);
        metaAi.textContent = toRecentMarks(state.aiHistory, 5);
    }

    function toRecentMarks(history, limit) {
        if (!history || history.length === 0) return "-";
        return history.slice(-limit).map((value) => {
            if (value === "O") return "〇";
            if (value === "X") return "×";
            return "-";
        }).join("");
    }

    function toResultText(result) {
        if (result === "GOAL") return "ゴール";
        if (result === "SAVE") return "セーブ";
        if (result === "BAR") return "バー";
        return result || "";
    }

    async function onKeyClick(e) {
        const key = e.currentTarget.dataset.key;
        await submitKey(key);
    }

    async function onReset() {
        if (pending) return;
        pending = true;
        setMessage("リセット中...");
        render();
        try {
            const res = await fetch("/reset", { method: "POST" });
            const data = await res.json();
            if (!res.ok || !data.ok) {
                setMessage(data.message || "リセットに失敗しました。", true);
                return;
            }
            applyState(data.state);
            lastOutcome = null;
            setMessage("新しい試合を開始しました。");
        } catch (err) {
            setMessage("通信に失敗しました。", true);
        } finally {
            pending = false;
            render();
        }
    }

    function applyState(vm) {
        state.phase = vm.phase;
        state.nextAction = vm.nextAction;
        state.winner = vm.winner;
        state.round = vm.round;
        state.suddenRound = vm.suddenRound;
        state.playerScore = vm.playerScore;
        state.aiScore = vm.aiScore;
        state.finished = vm.finished;
        state.playerHistory = vm.playerHistory || [];
        state.aiHistory = vm.aiHistory || [];
    }

    buildKeypad();
    resetBtn.addEventListener("click", onReset);
    if (newMatchBtn) {
        newMatchBtn.addEventListener("click", onReset);
    }
    document.addEventListener("keydown", onKeyDown);
    render();

    async function submitKey(key) {
        const action = state.nextAction === "PLAYER_SHOOT" ? "SHOOT" : "KEEP";
        if (pending || state.finished) return;
        if (!isValidKey(key)) return;
        pending = true;
        setMessage("処理中...");
        render();
        try {
            if (action === "KEEP") {
                await animateKeeperByInput(key);
            }
            const res = await fetch("/play", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ action, key })
            });
            const data = await res.json();
            if (!res.ok || !data.ok) {
                setMessage(data.message || "エラーが発生しました。", true);
                return;
            }
            applyState(data.state);
            lastOutcome = {
                shotKey: data.shotKey,
                keepKey: data.keepKey,
                result: data.result
            };
            await animateOutcome(lastOutcome);
            setMessage(data.message || "OK");
        } catch (err) {
            setMessage("通信に失敗しました。", true);
        } finally {
            pending = false;
            render();
        }
    }

    function onKeyDown(e) {
        if (pending || state.finished) return;
        const key = normalizeKey(e.key);
        if (!isValidKey(key)) return;
        flashKey(key);
        submitKey(key);
    }

    function isValidKey(key) {
        return rows.some((row) => row.includes(key));
    }

    function normalizeKey(key) {
        if (!key) return "";
        if (key.length === 1) {
            return key.toLowerCase();
        }
        return "";
    }

    function flashKey(key) {
        const btn = document.querySelector(`.key[data-key="${CSS.escape(key)}"]`);
        if (!btn) return;
        btn.classList.add("active");
        setTimeout(() => btn.classList.remove("active"), 150);
    }

    function keyToPos(key) {
        for (let r = 0; r < rows.length; r++) {
            const row = rows[r];
            const c = row.indexOf(key);
            if (c >= 0) {
                return { row: r, col: c, len: row.length };
            }
        }
        return { row: 2, col: 3, len: rows[2].length };
    }

    function placeElement(el, x, y, duration) {
        if (!el) return;
        el.style.transitionDuration = `${duration}ms`;
        el.style.left = `${x}px`;
        el.style.top = `${y}px`;
    }

    function getFrameRect() {
        const g = goal.getBoundingClientRect();
        const f = goalFrame.getBoundingClientRect();
        return {
            left: f.left - g.left,
            top: f.top - g.top,
            width: f.width,
            height: f.height
        };
    }

    function startPosition(frame) {
        return {
            x: frame.left + frame.width / 2,
            y: frame.top + frame.height - 18
        };
    }

    function targetPosition(key, frame) {
        const pos = keyToPos(key);
        const x = frame.left + ((pos.col + 0.5) / pos.len) * frame.width;
        const y = frame.top + ((pos.row + 0.5) / rows.length) * frame.height;
        return { x, y };
    }

    async function animateOutcome(outcome) {
        keeper.classList.add("is-active");
        try {
            const frame = getFrameRect();
            const start = startPosition(frame);
            const target = targetPosition(outcome.shotKey, frame);
            const keepPos = targetPosition(outcome.keepKey, frame);
            applyKeeperPose(frame, keepPos);
            if (outcome.result === "SAVE") {
                keeper.classList.add("keeper--catch");
            }

            placeElement(ball, start.x, start.y, 0);
            placeElement(keeper, keepPos.x, keepPos.y + 40, 200);
            await nextFrame();

            if (outcome.result === "BAR") {
                const barPos = { x: target.x, y: frame.top + 6 };
                placeElement(ball, barPos.x, barPos.y, ANIM_TOTAL_MS * 0.7);
                await wait(ANIM_TOTAL_MS * 0.7);
                placeElement(ball, barPos.x, barPos.y + 24, ANIM_TOTAL_MS * 0.3);
                await wait(ANIM_TOTAL_MS * 0.3);
                return;
            }

            if (outcome.result === "SAVE") {
                const mid = { x: keepPos.x, y: keepPos.y + 20 };
                placeElement(ball, mid.x, mid.y, ANIM_TOTAL_MS * 0.6);
                await wait(ANIM_TOTAL_MS * 0.6);
                if (outcome.shotKey === outcome.keepKey) {
                    return;
                }
                const deflect = {
                    x: mid.x + (mid.x < frame.left + frame.width / 2 ? -40 : 40),
                    y: mid.y + 20
                };
                placeElement(ball, deflect.x, deflect.y, ANIM_TOTAL_MS * 0.4);
                await wait(ANIM_TOTAL_MS * 0.4);
                return;
            }

            placeElement(ball, target.x, target.y, ANIM_TOTAL_MS);
            await wait(ANIM_TOTAL_MS);
        } finally {
            keeper.classList.remove("is-active");
            clearKeeperPose();
        }
    }

    async function animateKeeperByInput(key) {
        if (!keeper || !goal || !goalFrame) return;
        const frame = getFrameRect();
        const keepPos = targetPosition(key, frame);
        applyKeeperPose(frame, keepPos);
        placeElement(keeper, keepPos.x, keepPos.y + 40, 180);
        await wait(180);
    }

    function applyKeeperPose(frame, keepPos) {
        if (!keeper) return;
        clearKeeperPose();
        const centerX = frame.left + frame.width / 2;
        const offset = keepPos.x - centerX;
        const threshold = frame.width * 0.12;
        if (offset < -threshold) {
            keeper.classList.add("keeper--left");
        } else if (offset > threshold) {
            keeper.classList.add("keeper--right");
        } else {
            keeper.classList.add("keeper--center");
        }
    }

    function clearKeeperPose() {
        if (!keeper) return;
        keeper.classList.remove("keeper--left", "keeper--right", "keeper--center", "keeper--catch");
    }

    function wait(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    function nextFrame() {
        return new Promise((resolve) => requestAnimationFrame(() => resolve()));
    }
})();
