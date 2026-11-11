function updateSupportDay(day) {
    const checked = [...day.querySelectorAll("[data-support-person]:checked")];
    const count = day.querySelector("[data-support-count]");
    const required = Number(count.dataset.supportRequired);
    count.textContent = checked.length + " / " + required + "명";
    count.classList.toggle("mismatch", checked.length !== required);

    const names = day.querySelector("[data-support-names]");
    const chips = checked.map((person) => {
        const chip = document.createElement("span");
        chip.className = "name-chip";
        chip.textContent = person.dataset.supportPerson;
        if (person.dataset.onLeave === "true") {
            chip.classList.add("is-conflict");
            chip.title = "휴가 겹침";
            chip.textContent += " · 휴가 겹침";
        }
        return chip;
    });
    if (!chips.length) {
        const empty = document.createElement("span");
        empty.className = "name-chip is-empty";
        empty.textContent = "미입력";
        chips.push(empty);
    }
    names.replaceChildren(...chips);
}

document.addEventListener("change", (event) => {
    if (!event.target.matches("[data-support-person]")) {
        return;
    }
    updateSupportDay(event.target.closest(".support-day"));
});
