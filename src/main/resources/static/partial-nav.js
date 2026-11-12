// Links and forms with data-swap replace only the listed regions instead of reloading the page,
// so the scroll position stays where the user tapped.
(() => {
    function replaceRegions(page, selectors) {
        for (const selector of selectors.split(",").map(value => value.trim())) {
            const currents = [...document.querySelectorAll(selector)];
            const nexts = [...page.querySelectorAll(selector)];
            if (!nexts.length) {
                currents.forEach(element => element.remove());
                continue;
            }
            if (currents.length !== nexts.length) {
                return false;
            }
            currents.forEach((element, index) => element.replaceWith(nexts[index]));
        }
        return true;
    }

    async function swap(url, selectors, init = {}) {
        const response = await fetch(url, {
            ...init,
            credentials: "same-origin",
            headers: { Accept: "text/html" },
        });
        if (new URL(response.url).pathname === "/login") {
            location.assign(response.url);
            return null;
        }
        const page = new DOMParser().parseFromString(await response.text(), "text/html");
        if (!response.ok) {
            document.documentElement.replaceWith(page.documentElement);
            return null;
        }
        let replaced = true;
        const update = () => {
            replaced = replaceRegions(page, selectors);
            document.title = page.title;
        };
        if (document.startViewTransition) {
            const transition = document.startViewTransition(update);
            // A skipped animation is harmless; the regions are already replaced once updateCallbackDone settles.
            transition.ready.catch(() => {});
            transition.finished.catch(() => {});
            await transition.updateCallbackDone;
        } else {
            update();
        }
        if (!replaced) {
            location.assign(response.url);
            return null;
        }
        document.dispatchEvent(new CustomEvent("partial:swapped"));
        fadeSuccessNotices();
        return response.url;
    }

    function fadeSuccessNotices() {
        document.querySelectorAll(".flash-messages .notice:not(.danger)").forEach(notice => {
            setTimeout(() => notice.classList.add("is-fading"), 3500);
            setTimeout(() => notice.remove(), 3900);
        });
    }

    function scrollToHash(url) {
        const hash = new URL(url).hash;
        if (hash) {
            document.querySelector(hash)?.scrollIntoView({ behavior: "smooth", block: "nearest" });
        }
    }

    function createFormBody(form, submitter) {
        const data = new FormData(form, submitter);
        return form.enctype === "multipart/form-data" ? data : new URLSearchParams(data);
    }

    document.addEventListener("click", async event => {
        const link = event.target.closest("a[data-swap]");
        if (!link || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        event.preventDefault();
        const selectors = link.dataset.swap;
        try {
            if (!await swap(link.href, selectors)) {
                return;
            }
            history.replaceState({ swap: selectors }, "");
            history.pushState({ swap: selectors }, "", link.href);
            scrollToHash(link.href);
        } catch (error) {
            location.assign(link.href);
        }
    });

    // A GET form with data-swap behaves like a data-swap link to the URL built from its fields.
    async function swapSearch(form) {
        const url = new URL(form.action);
        url.search = new URLSearchParams(new FormData(form)).toString();
        const selectors = form.dataset.swap;
        try {
            if (!await swap(url.href, selectors)) {
                return;
            }
            history.replaceState({ swap: selectors }, "");
            history.pushState({ swap: selectors }, "", url.href);
        } catch (error) {
            location.assign(url.href);
        }
    }

    document.addEventListener("change", event => {
        const field = event.target.closest("[data-submit-on-change]");
        if (field?.form) {
            field.form.requestSubmit();
        }
    });

    document.addEventListener("submit", async event => {
        const form = event.target.closest("form[data-swap]");
        if (form && form.method.toLowerCase() === "get") {
            event.preventDefault();
            await swapSearch(form);
            return;
        }
        if (!form || form.method.toLowerCase() !== "post") {
            return;
        }
        event.preventDefault();
        if (form.dataset.submitting) {
            return;
        }
        form.dataset.submitting = "true";
        const selectors = form.dataset.swap;
        const body = createFormBody(form, event.submitter);
        const buttons = [...form.querySelectorAll("button[type=submit]")];
        buttons.forEach(button => button.disabled = true);
        try {
            const resultUrl = await swap(form.action, selectors, { method: "POST", body });
            if (resultUrl) {
                history.replaceState({ swap: selectors }, "", resultUrl);
            }
        } catch (error) {
            // The request may already have been saved, so sending the form again could save it twice.
            // Reload instead and let the page show what was stored.
            location.reload();
        } finally {
            delete form.dataset.submitting;
            buttons.forEach(button => button.disabled = false);
        }
    });

    fadeSuccessNotices();

    window.addEventListener("popstate", event => {
        if (event.state?.swap) {
            swap(location.href, event.state.swap).catch(() => location.reload());
        }
    });
})();
