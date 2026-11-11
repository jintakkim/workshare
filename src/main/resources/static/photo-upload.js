(() => {
    const MAX_SIDE = 1600;
    const MAX_UPLOAD_BYTES = 15 * 1024 * 1024;

    async function shrinkPhoto(file) {
        const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
        const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(bitmap.width * scale);
        canvas.height = Math.round(bitmap.height * scale);
        canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        bitmap.close();
        const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.88));
        if (!blob || blob.size >= file.size) {
            return file;
        }
        return new File([blob], file.name.replace(/\.[^.]+$/, "") + ".jpg", { type: "image/jpeg" });
    }

    function replaceSelectedFile(input, file) {
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
    }

    function showPreview(form, file) {
        const avatar = form.closest(".profile-summary")?.querySelector(".profile-avatar");
        if (!avatar || !file) {
            return;
        }
        let image = avatar.querySelector("img");
        if (!image) {
            image = document.createElement("img");
            image.alt = "";
            avatar.append(image);
        }
        image.src = URL.createObjectURL(file);
    }

    for (const form of document.querySelectorAll("[data-photo-form]")) {
        const input = form.querySelector('input[type="file"]');
        const saving = form.querySelector("[data-photo-saving]");
        const savingText = form.querySelector("[data-photo-saving-text]");
        let ready = false;

        input.addEventListener("change", () => showPreview(form, input.files[0]));

        form.addEventListener("submit", async (event) => {
            if (ready) {
                return;
            }
            event.preventDefault();
            const file = input.files[0];
            if (!file) {
                return;
            }
            saving.hidden = false;
            form.querySelectorAll("button, input").forEach((control) => control.setAttribute("aria-disabled", "true"));
            savingText.textContent = "사진을 준비하고 있어요";
            try {
                replaceSelectedFile(input, await shrinkPhoto(file));
            } catch (error) {
                // Browsers that cannot decode the photo (e.g. HEIC on Android) send the original instead.
            }
            if (input.files[0].size > MAX_UPLOAD_BYTES) {
                saving.hidden = true;
                alert("사진은 15MB 이하로 선택해주세요.");
                return;
            }
            savingText.textContent = "사진을 저장하고 있어요";
            ready = true;
            form.requestSubmit();
        });
    }

    window.addEventListener("pageshow", (event) => {
        if (event.persisted) {
            document.querySelectorAll("[data-photo-saving]").forEach((saving) => {
                saving.hidden = true;
            });
        }
    });
})();
