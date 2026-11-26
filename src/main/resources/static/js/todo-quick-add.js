(() => {
    const form = document.getElementById('quick-add-form');
    if (!form) return;
    const input = document.getElementById('quick-title');
    const error = document.getElementById('quick-add-error');
    const status = document.getElementById('quick-add-status');
    const button = form.querySelector('button');
    const rows = document.getElementById('todo-rows');
    let saving = false;
    input.addEventListener('input', () => {
        error.hidden = true;
        error.textContent = '';
        input.removeAttribute('aria-invalid');
        status.textContent = '';
    });
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && (event.isComposing || event.keyCode === 229)) event.preventDefault();
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (saving) return;
        saving = true;
        button.disabled = true;
        input.readOnly = true;
        form.setAttribute('aria-busy', 'true');
        error.hidden = true;
        status.textContent = '追加中…';
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    [document.querySelector('meta[name="_csrf_header"]').content]:
                        document.querySelector('meta[name="_csrf"]').content
                },
                body: JSON.stringify({ title: input.value })
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(response.status === 400
                    ? data.errors?.find(item => item.field === 'title')?.message || 'タイトルを確認してください。'
                    : response.status === 403 ? '有効期限が切れました。ページを再読み込みしてください。'
                    : '追加できませんでした。もう一度お試しください。');
            }
            const row = document.createElement('tr');
            row.dataset.id = data.id;
            row.dataset.completed = 'false';
            row.draggable = rows.dataset.reorderEnabled === 'true';
            row.className = 'todo-slide-in';
            const cell = text => {
                const td = document.createElement('td');
                td.textContent = text ?? '';
                row.append(td);
                return td;
            };
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.className = 'todo-select';
            checkbox.name = 'ids';
            checkbox.value = data.id;
            checkbox.setAttribute('form', 'bulk-delete-form');
            checkbox.setAttribute('aria-label', `${data.title}を選択`);
            cell('').append(checkbox);
            const base = form.action.replace(/\/api\/todo$/, '/todos/');
            const link = (target, text, container) => {
                const a = document.createElement('a');
                a.href = base + data.id + target;
                a.textContent = text;
                a.className = 'link';
                container.append(a);
            };
            link('', data.title, cell(''));
            cell(data.category);
            cell('中');
            cell('');
            cell('未完了');
            const actions = cell('');
            link('/edit', '編集', actions);
            link('/delete', '削除', actions);
            rows.prepend(row);
            rows.closest('table').hidden = false;
            document.getElementById('todo-empty')?.remove();
            document.dispatchEvent(new Event('todo:added'));
            input.value = '';
            input.removeAttribute('aria-invalid');
            status.textContent = 'タスクを追加しました。';
        } catch (failure) {
            status.textContent = '';
            error.textContent = failure.message || '通信に失敗しました。';
            error.hidden = false;
            input.setAttribute('aria-invalid', 'true');
        } finally {
            saving = false;
            button.disabled = false;
            input.readOnly = false;
            form.removeAttribute('aria-busy');
            input.focus();
        }
    });
})();
