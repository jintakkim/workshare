document.querySelectorAll("[data-password-toggle]").forEach((button) => {
    button.addEventListener("click", () => {
        const input = document.getElementById(
            button.getAttribute("aria-controls"),
        );
        if (!input) return;
        const visible = input.type === "password";
        input.type = visible ? "text" : "password";
        button.title = visible ? "비밀번호 숨기기" : "비밀번호 보기";
        button.setAttribute("aria-pressed", String(visible));
        button.setAttribute(
            "aria-label",
            visible ? "비밀번호 숨기기" : "비밀번호 보기",
        );
    });
});
