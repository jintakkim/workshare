function scrollToToday() {
    const today = document.querySelector("[data-scroll-today]");
    if (!today) {
        return;
    }
    const smooth = !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    // Wait one frame so the page is laid out before measuring where today sits.
    requestAnimationFrame(() => today.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "center" }));
}

// Only on first load: after a save the page is swapped in place and should stay where the user was.
scrollToToday();
