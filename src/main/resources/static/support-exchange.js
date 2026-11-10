function bindCandidateFilter(exchangeForm) {
    if (!exchangeForm) {
        return;
    }
    const search = exchangeForm.querySelector("[data-support-search]");
    const nameFilter = exchangeForm.querySelector("[data-support-name]");
    const count = exchangeForm.querySelector("[data-support-count]");
    const candidates = exchangeForm.querySelectorAll("[data-support-candidate]");

    function filterSupportCandidates() {
        const name = nameFilter.value.trim().normalize();
        let visible = 0;
        for (const candidate of candidates) {
            const matches = candidate.dataset.supportCandidate.normalize().includes(name);
            candidate.hidden = !matches;
            if (matches) {
                visible++;
            }
        }
        count.textContent = visible ? "바꿀 수 있는 일정 " + visible + "개" : "검색한 이름의 일정이 없어요.";
    }

    if (candidates.length > 5) {
        search.hidden = false;
        nameFilter.addEventListener("input", filterSupportCandidates);
        filterSupportCandidates();
    }
}

bindCandidateFilter(document.querySelector("[data-exchange-form]"));
document.addEventListener("partial:swapped", () => bindCandidateFilter(document.querySelector("[data-exchange-form]")));
