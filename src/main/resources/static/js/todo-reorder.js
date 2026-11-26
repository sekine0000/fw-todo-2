(() => {
    const body = document.getElementById('todo-rows');
    if (body?.dataset.reorderEnabled !== 'true') return;
    const status = document.getElementById('reorder-status');
    let dragged = null;
    let original = [];
    let saving = false;
    const restore = () => original.forEach(row => body.appendChild(row));
    body.addEventListener('dragstart', event => {
        const row = event.target.closest('tr[data-id]');
        if (saving || !row || event.target.closest('a, button, input, select, textarea')) {
            event.preventDefault();
            return;
        }
        dragged = row;
        original = [...body.children];
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', row.dataset.id);
        row.style.opacity = '0.5';
    });
    body.addEventListener('dragover', event => {
        if (!dragged || saving) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        const target = event.target.closest('tr[data-id]');
        if (!target || target === dragged) return;
        const bounds = target.getBoundingClientRect();
        body.insertBefore(dragged, event.clientY < bounds.top + bounds.height / 2 ? target : target.nextSibling);
    });
    body.addEventListener('drop', async event => {
        if (!dragged || saving) return;
        event.preventDefault();
        dragged.style.opacity = '';
        dragged = null;
        const rows = [...body.children];
        if (rows.every((row, index) => row === original[index])) return;
        saving = true;
        body.setAttribute('aria-busy', 'true');
        status.textContent = '並び順を保存中…';
        try {
            const response = await fetch(body.dataset.reorderUrl, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ids: rows.map(row => Number(row.dataset.id)) })
            });
            if (!response.ok) throw new Error('Save failed');
            status.textContent = '並び順を保存しました。';
        } catch {
            restore();
            status.textContent = '保存できませんでした。並び順を元に戻しました。再読み込みしてからお試しください。';
        } finally {
            saving = false;
            body.removeAttribute('aria-busy');
        }
    });
    body.addEventListener('dragend', () => {
        if (dragged) {
            dragged.style.opacity = '';
            restore();
            dragged = null;
        }
    });
})();
