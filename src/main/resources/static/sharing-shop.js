(() => {
    const guide = document.getElementById("allocation-guide");
    document.querySelector("[data-allocation-guide]")?.addEventListener("click", () => guide.showModal());

    const deck = document.querySelector(".day-deck");
    if (!deck) return;
    const cards = [...deck.querySelectorAll(".day-card")];
    const todayIndex = cards.findIndex(
        (card) => card.dataset.date === deck.dataset.today,
    );
    const live = document.querySelector("[data-day-live]");
    let active = -1,
        current = null,
        serverOffset = 0,
        refreshTimer,
        scrollTimer,
        controller,
        retries = 0;
    const cache = new Map();
    const createElement = (tag, className, text) => {
        const result = document.createElement(tag);
        if (className) result.className = className;
        if (text !== undefined) result.textContent = text;
        return result;
    };
    function createMemberList(members) {
        const list = createElement("ul", "day-members");
        if (!members.length) {
            list.append(createElement("li", "member-empty", "해당 직원이 없어요."));
            return list;
        }
        for (const person of members) {
            const row = createElement("li", "day-member");
            const link = createElement("a", "day-member-link");
            link.href = deck.dataset.photoBase + encodeURIComponent(person.id);
            const avatar = createElement("span", "day-member-avatar");
            avatar.setAttribute("aria-hidden", "true");
            if (person.hasPhoto) {
                const image = createElement("img");
                image.src = link.href + "/photo";
                image.alt = "";
                image.loading = "lazy";
                avatar.append(image);
            } else avatar.textContent = person.name.slice(0, 1);
            link.append(
                avatar,
                createElement("span", "day-member-name", person.name),
            );
            row.append(
                link,
                createElement("span", "day-member-attendance", person.attendance),
            );
            list.append(row);
        }
        return list;
    }
    function createRosterSection(title, people, className = "") {
        const section = createElement("section", "day-roster " + className);
        const heading = createElement("div", "day-roster-heading");
        heading.append(
            createElement("h3", "", title),
            createElement("span", "", people.length + "명"),
        );
        section.append(heading, createMemberList(people));
        return section;
    }
    function render(day) {
        const content = cards[active].querySelector("[data-day-content]");
        content.replaceChildren();
        if (day.phase === "OFF") {
            const off = createElement("div", "day-off");
            off.append(
                createElement("span", "day-off-symbol", "☾"),
                createElement("h3", "", "쉬어가는 날이에요"),
                createElement("p", "", day.offReason),
            );
            content.append(off);
            return;
        }
        const status = createElement("div", "day-result-status");
        if (day.phase === "WAITING") {
            status.append(
                createElement("p", "", "배정 결과 공개까지"),
                createElement("strong", "day-countdown", "--:--:--"),
                createElement("small", "", "오전 08:50:10에 결과를 확인해요."),
            );
        } else if (day.phase === "READY")
            status.append(
                createElement(
                    "span",
                    "day-ready-label",
                    "오늘의 업무 배정이 완료됐어요.",
                ),
            );
        else if (day.phase === "MISSING")
            status.append(createElement("p", "", "저장된 배정 기록이 없어요."));
        else
            status.append(
                createElement("p", "", "배정 결과를 확인하고 있어요."),
                createElement("small", "", "준비가 끝나면 자동으로 보여드릴게요."),
            );
        content.append(status);
        const quota = createElement("div", "day-quota");
        for (const [label, amount] of [
            [
                "오전 · " + day.morningHours,
                day.phase === "READY" ? day.morning.length : day.morningSlots,
            ],
            [
                "오후 · " + day.eveningHours,
                day.phase === "READY" ? day.evening.length : day.eveningSlots,
            ],
        ]) {
            const part = createElement("div");
            part.append(
                createElement("span", "", label),
                createElement("strong", "", amount + "명"),
            );
            quota.append(part);
        }
        content.append(quota);
        if (day.phase === "READY") {
            content.append(
                createRosterSection("오전 담당", day.morning, "morning-result"),
                createRosterSection("오후 담당", day.evening, "evening-result"),
            );

            const detail = createElement("details", "day-roster-detail");
            detail.append(
                createElement("summary", "", "참여·휴가 현황 보기"),
                createRosterSection("참여 직원", day.participants),
                createRosterSection("휴가 직원", day.leaves),
            );
            content.append(detail);
        } else
            content.append(
                createRosterSection("참여 직원", day.participants),
                createRosterSection("휴가 직원", day.leaves, "leave-roster"),
            );
        if (day.excluded?.length)
            content.append(
                createRosterSection(
                    "다른 업무로 제외",
                    day.excluded,
                    "excluded-roster",
                ),
            );
        for (const warning of day.warnings)
            content.append(createElement("p", "day-warning", warning));
        tick();
    }
    function tick() {
        const counter =
            active >= 0 && cards[active].querySelector(".day-countdown");
        if (!counter || !current) return;
        let seconds = Math.max(
            0,
            Math.ceil(
                (Date.parse(current.refreshAt) - (Date.now() + serverOffset)) /
                    1000,
            ),
        );
        const days = Math.floor(seconds / 86400);
        seconds %= 86400;
        const hours = Math.floor(seconds / 3600),
            minutes = Math.floor(seconds / 60) % 60;
        counter.textContent =
            (days ? days + "일 " : "") +
            [hours, minutes, seconds % 60]
                .map((value) => String(value).padStart(2, "0"))
                .join(":");
    }
    function schedule(delay) {
        clearTimeout(refreshTimer);
        if (!document.hidden)
            refreshTimer = setTimeout(loadActive, Math.max(0, delay));
    }
    async function loadActive() {
        if (active < 0 || document.hidden) return;
        clearTimeout(refreshTimer);
        controller?.abort();
        controller = new AbortController();
        const index = active,
            date = cards[index].dataset.date,
            requestStarted = Date.now();
        try {
            const response = await fetch(
                deck.dataset.apiBase + encodeURIComponent(date),
                {
                    signal: controller.signal,
                    cache: "no-store",
                    credentials: "same-origin",
                    headers: { Accept: "application/json" },
                },
            );
            if (!response.ok)
                throw new Error(
                    response.status === 401
                        ? "로그인 후 다시 확인해주세요."
                        : "결과를 불러오지 못했어요.",
                );
            const day = await response.json();
            if (active !== index) return;
            serverOffset =
                Date.parse(day.serverNow) +
                (Date.now() - requestStarted) / 2 -
                Date.now();
            current = day;
            cache.set(date, day);
            render(day);
            if (day.phase === "WAITING")
                schedule(
                    Math.min(
                        30000,
                        Math.max(
                            50,
                            Date.parse(day.refreshAt) -
                                Date.now() -
                                serverOffset,
                        ),
                    ),
                );
            else if (day.phase === "PREPARING")
                schedule(retries++ < 15 ? 2000 : 15000);
            else retries = 0;
        } catch (error) {
            if (error.name === "AbortError" || active !== index) return;
            const content = cards[index].querySelector("[data-day-content]");
            content.replaceChildren(createElement("p", "day-warning", error.message));
            const retry = createElement("button", "button", "다시 확인");
            retry.type = "button";
            retry.addEventListener("click", loadActive);
            content.append(retry);
            schedule(15000);
        }
    }
    function activate(index) {
        if (index === active) return;
        active = index;
        current = null;
        retries = 0;
        controller?.abort();
        clearTimeout(refreshTimer);
        cards.forEach((card, i) => {
            card.classList.toggle("is-active", i === index);
            card.inert = i !== index;
            card.setAttribute("aria-hidden", String(i !== index));
        });
        live.textContent =
            cards[index].getAttribute("aria-label") + " 활동 현황";
        document.querySelector("[data-day-prev]").disabled = index === 0;
        document.querySelector("[data-day-next]").disabled =
            index === cards.length - 1;
        const cached = cache.get(cards[index].dataset.date);
        if (cached && ["READY", "OFF", "MISSING"].includes(cached.phase)) {
            current = cached;
            render(cached);
        } else loadActive();
    }
    function center(index, behavior = "smooth") {
        index = Math.max(0, Math.min(cards.length - 1, index));
        const card = cards[index];
        deck.scrollTo({
            left: card.offsetLeft - (deck.clientWidth - card.offsetWidth) / 2,
            behavior,
        });
    }
    deck.addEventListener(
        "scroll",
        () => {
            clearTimeout(scrollTimer);
            scrollTimer = setTimeout(() => {
                const middle = deck.scrollLeft + deck.clientWidth / 2;
                const nearest = cards.reduce(
                    (best, card, i) =>
                        Math.abs(
                            card.offsetLeft + card.offsetWidth / 2 - middle,
                        ) <
                        Math.abs(
                            cards[best].offsetLeft +
                                cards[best].offsetWidth / 2 -
                                middle,
                        )
                            ? i
                            : best,
                    0,
                );
                activate(nearest);
            }, 90);
        },
        { passive: true },
    );
    const getScrollBehavior = () =>
        matchMedia("(prefers-reduced-motion: reduce)").matches
            ? "auto"
            : "smooth";
    document
        .querySelector("[data-day-prev]")
        .addEventListener("click", () => center(active - 1, getScrollBehavior()));
    document
        .querySelector("[data-day-next]")
        .addEventListener("click", () => center(active + 1, getScrollBehavior()));
    document
        .querySelector("[data-day-today]")
        .addEventListener("click", () => center(todayIndex, getScrollBehavior()));
    deck.addEventListener("keydown", (event) => {
        if (
            event.target !== deck ||
            !["ArrowLeft", "ArrowRight"].includes(event.key)
        )
            return;
        event.preventDefault();
        center(active + (event.key === "ArrowLeft" ? -1 : 1), getScrollBehavior());
    });
    document.addEventListener("visibilitychange", () => {
        if (document.hidden) {
            clearTimeout(refreshTimer);
            controller?.abort();
        } else loadActive();
    });
    setInterval(tick, 1000);
    requestAnimationFrame(() => {
        center(Math.max(0, todayIndex), "auto");
        activate(Math.max(0, todayIndex));
    });
})();
