(() => {
    function updateServiceProgress() {
        const now = Date.now();
        for (const card of document.querySelectorAll("[data-service-progress]")) {
            const start = Number(card.dataset.start);
            const end = Number(card.dataset.end);
            const ratio = Math.min(1, Math.max(0, (now - start) / (end - start)));
            card.querySelector("[data-service-percent]").textContent = (ratio * 100).toFixed(7);
            card.querySelector("[data-service-fill]").style.width = ratio * 100 + "%";
        }
        requestAnimationFrame(updateServiceProgress);
    }

    requestAnimationFrame(updateServiceProgress);
})();
