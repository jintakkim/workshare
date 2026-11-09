const SOCIAL_SERVICE_MONTHS = 21;

function parseDate(value) {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(Date.UTC(year, month - 1, day));
}

function formatDate(date) {
    return date.toISOString().slice(0, 10);
}

function calculateServiceEndDate(serviceStartDate) {
    const start = parseDate(serviceStartDate);
    const targetMonth = start.getUTCMonth() + SOCIAL_SERVICE_MONTHS;
    const lastDayOfTargetMonth = new Date(Date.UTC(start.getUTCFullYear(), targetMonth + 1, 0)).getUTCDate();
    const anniversary = start.getUTCDate() <= lastDayOfTargetMonth
        ? new Date(Date.UTC(start.getUTCFullYear(), targetMonth, start.getUTCDate()))
        : new Date(Date.UTC(start.getUTCFullYear(), targetMonth + 1, 1));
    anniversary.setUTCDate(anniversary.getUTCDate() - 1);
    return formatDate(anniversary);
}

document.querySelectorAll("[data-service-start]").forEach((startInput) => {
    const endInput = startInput.form.querySelector("[data-service-end]");
    if (!endInput) {
        return;
    }
    startInput.addEventListener("change", () => {
        if (startInput.value) {
            endInput.value = calculateServiceEndDate(startInput.value);
        }
    });
});
