document.querySelectorAll('[data-smart-date]').forEach(input => {
    const form = input.closest('form');
    const date = form.querySelector('[name="dueDate"]');
    const preview = form.querySelector('[data-date-preview]');
    let timer;
    let version = 0;
    let controller;
    const reset = () => {
        clearTimeout(timer);
        controller?.abort();
        version++;
        input.setCustomValidity('');
    };
    input.addEventListener('input', () => {
        reset();
        const value = input.value.trim();
        preview.textContent = value ? '解析中…' : '';
        if (!value) return;
        input.setCustomValidity('日付の解析が完了するまでお待ちください。');
        const current = version;
        timer = setTimeout(async () => {
            controller = new AbortController();
            try {
                const response = await fetch(`${input.dataset.parseUrl}?${new URLSearchParams({input: value})}`, {
                    signal: controller.signal
                });
                const result = await response.json();
                if (current !== version) return;
                if (!response.ok || !result.date) throw new Error(result.error || '日付を解析できません。');
                date.value = result.date;
                preview.textContent = `期限：${result.date}`;
                input.setCustomValidity('');
            } catch (error) {
                if (current !== version || error.name === 'AbortError') return;
                const message = error instanceof TypeError ? '通信に失敗しました。再入力してください。' : error.message;
                preview.textContent = message;
                input.setCustomValidity(message);
            }
        }, 300);
    });
    date.addEventListener('input', () => {
        reset();
        input.value = '';
        preview.textContent = '';
    });
});
