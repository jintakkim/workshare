function showWorkGroupStep(form, step) {
    for (const panel of form.querySelectorAll("[data-work-group-step]")) {
        panel.hidden = panel.dataset.workGroupStep !== step;
    }
}

function findWorkGroupImpact(form) {
    const selected = form.elements.workGroupCode.value;
    return [...form.querySelectorAll("[data-work-group-impact]")]
        .find((impact) => impact.dataset.workGroupImpact === selected);
}

document.addEventListener("click", (event) => {
    const dialog = document.getElementById("work-group-dialog");
    if (!dialog) {
        return;
    }
    const form = dialog.querySelector("[data-work-group-form]");
    if (event.target.closest("[data-work-group-open]")) {
        form.reset();
        showWorkGroupStep(form, "select");
        dialog.showModal();
    } else if (event.target.closest("[data-work-group-close]")) {
        dialog.close();
    } else if (event.target.closest("[data-work-group-back]")) {
        showWorkGroupStep(form, "select");
    }
});

document.addEventListener("submit", (event) => {
    const form = event.target.closest("[data-work-group-form]");
    if (!form || form.querySelector('[data-work-group-step="select"]').hidden) {
        return;
    }
    const impact = findWorkGroupImpact(form);
    if (!impact) {
        return;
    }
    event.preventDefault();
    for (const message of form.querySelectorAll("[data-work-group-impact]")) {
        message.hidden = message !== impact;
    }
    showWorkGroupStep(form, "confirm");
});
