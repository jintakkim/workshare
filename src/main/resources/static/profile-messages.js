(() => {
    const POLL_INTERVAL_MS = 15000;

    function fitHeight(input) {
        input.style.height = "auto";
        input.style.height = input.scrollHeight + 2 + "px";
    }

    function updateComposer(input) {
        const form = input.closest("form");
        const length = input.value.length;
        const counter = form.querySelector("[data-message-counter]");
        counter.textContent = length + " / " + input.maxLength;
        counter.classList.toggle("is-near-limit", length > input.maxLength - 50);
        form.querySelector("[data-message-submit]").disabled = !input.value.trim();
        fitHeight(input);
    }

    function isEditing(section) {
        return section.querySelector(".message-edit:not([hidden])") !== null;
    }

    function closeEditor(article) {
        const editor = article.querySelector(".message-edit");
        editor.reset();
        editor.hidden = true;
        article.querySelector(".message-content").hidden = false;
        article.querySelector(".message-actions").hidden = false;
    }

    document.addEventListener("input", (event) => {
        if (event.target.matches("[data-message-input]")) {
            updateComposer(event.target);
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.target.matches("[data-message-input]") && event.key === "Enter" && (event.metaKey || event.ctrlKey)
                && !event.isComposing && !event.repeat) {
            event.preventDefault();
            if (event.target.value.trim()) {
                event.target.form.requestSubmit();
            }
        }
    });

    document.addEventListener("click", (event) => {
        const article = event.target.closest(".message");
        if (!article) {
            return;
        }
        if (event.target.closest("[data-message-edit]")) {
            const editor = article.querySelector(".message-edit");
            const input = editor.querySelector("[data-message-input]");
            article.querySelector(".message-content").hidden = true;
            article.querySelector(".message-actions").hidden = true;
            editor.hidden = false;
            updateComposer(input);
            input.focus();
            input.setSelectionRange(input.value.length, input.value.length);
        } else if (event.target.closest("[data-message-cancel]")) {
            closeEditor(article);
        }
    });

    // Runs before the page-wide partial submit handler, so a cancelled delete never reaches the server.
    document.addEventListener("submit", (event) => {
        if (event.target.matches("[data-message-delete]") && !confirm("이 한마디를 지울까요?")) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    async function refreshMessages() {
        const section = document.querySelector("[data-profile-messages]");
        if (!section || document.hidden || isEditing(section)) {
            return;
        }
        const response = await fetch(section.dataset.pollUrl, {
            credentials: "same-origin",
            headers: { Accept: "text/html" },
        });
        if (!response.ok || new URL(response.url).pathname === "/login") {
            return;
        }
        const next = new DOMParser().parseFromString(await response.text(), "text/html").querySelector("[data-message-list]");
        const current = section.querySelector("[data-message-list]");
        if (next && current && !isEditing(section) && next.innerHTML !== current.innerHTML) {
            current.replaceWith(next);
        }
    }

    setInterval(() => refreshMessages().catch(() => {}), POLL_INTERVAL_MS);
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden) {
            refreshMessages().catch(() => {});
        }
    });
})();
