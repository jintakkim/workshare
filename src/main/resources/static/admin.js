const trigger = document.querySelector(".menu-trigger");
const menu = trigger
    ? document.getElementById(trigger.getAttribute("aria-controls"))
    : null;
if (menu && trigger) {
    trigger.addEventListener("click", () => {
        menu.showModal();
        trigger.setAttribute("aria-expanded", "true");
    });
    menu.querySelector(".menu-close").addEventListener("click", () =>
        menu.close(),
    );
    menu.addEventListener("click", (event) => {
        const box = menu.getBoundingClientRect();
        if (
            event.target === menu &&
            (event.clientX < box.left ||
                event.clientX > box.right ||
                event.clientY < box.top ||
                event.clientY > box.bottom)
        )
            menu.close();
    });
    menu.addEventListener("close", () =>
        trigger.setAttribute("aria-expanded", "false"),
    );
}
