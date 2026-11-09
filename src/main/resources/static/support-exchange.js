const exchangeSourceForm = document.querySelector("[data-exchange-source-form]");
if (exchangeSourceForm) {
    exchangeSourceForm.querySelector("[data-exchange-source-submit]").hidden = true;
    exchangeSourceForm.querySelector("[data-exchange-source]").addEventListener("change", () => {
        exchangeSourceForm.requestSubmit();
    });
}

const supportPicker = document.getElementById("support-picker");
if (supportPicker) {
    const form = document.querySelector("[data-exchange-form]");
    const openPicker = form.querySelector("[data-open-support-picker]");
    const submitRequest = form.querySelector('button[type="submit"]');
    const nameFilter = supportPicker.querySelector("[data-support-name]");
    const dateFilter = supportPicker.querySelector("[data-support-date]");
    const count = supportPicker.querySelector("[data-support-count]");
    const candidates = supportPicker.querySelectorAll("[data-support-candidate]");

    function filterSupportCandidates() {
        const name = nameFilter.value.trim().normalize();
        const date = dateFilter.value;
        let visible = 0;
        for (const candidate of candidates) {
            const matches = candidate.dataset.supportCandidate.normalize().includes(name)
                && (!date || candidate.dataset.date === date);
            candidate.hidden = !matches;
            if (matches) {
                visible++;
            }
        }
        count.textContent = visible ? "교환 가능한 일정 " + visible + "개" : "검색 조건에 맞는 일정이 없어요.";
    }

    openPicker.addEventListener("click", () => {
        filterSupportCandidates();
        supportPicker.showModal();
    });
    supportPicker.querySelector("[data-close-support-picker]").addEventListener("click", () => {
        supportPicker.close();
    });
    nameFilter.addEventListener("input", filterSupportCandidates);
    dateFilter.addEventListener("input", filterSupportCandidates);
    supportPicker.querySelector(".support-picker-list").addEventListener("click", event => {
        const selected = event.target.closest(".support-picker-option");
        if (!selected) {
            return;
        }
        const name = selected.querySelector("strong").textContent;
        const date = selected.querySelector("span").textContent;
        form.elements.targetId.value = selected.value;
        openPicker.textContent = name + " · " + date + " 변경";
        submitRequest.disabled = false;
        supportPicker.close();
    });
    form.hidden = false;
}
