document.querySelector('[data-menu-toggle]')?.addEventListener('click', () => {
    document.querySelector('#sidebar')?.classList.toggle('open');
});

document.querySelectorAll('form[data-confirm]').forEach((form) => {
    form.addEventListener('submit', (event) => {
        if (!window.confirm(form.dataset.confirm)) {
            event.preventDefault();
            return;
        }

        if (form.dataset.confirmText) {
            const answer = window.prompt(`Tape ${form.dataset.confirmText} pour confirmer :`);
            if (answer !== form.dataset.confirmText) {
                event.preventDefault();
            }
        }
    });
});
