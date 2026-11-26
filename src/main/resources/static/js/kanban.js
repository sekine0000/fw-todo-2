(() => {
    'use strict';
    const board = document.querySelector('.kanban-board');
    if (!board) return;
    const message = document.getElementById('kanban-message');
    let dragged = null;
    let pending = false;
    async function move(card, status) {
        const select = card.querySelector('select');
        const previousStatus = card.dataset.status;
        select.value = previousStatus;
        if (pending || status === previousStatus) return;
        const destination = [...board.querySelectorAll('.kanban-column')]
            .find(column => column.dataset.status === status);
        if (!destination) return;
        const parent = card.parentNode;
        const next = card.nextSibling;
        pending = true;
        board.querySelectorAll('select').forEach(input => { input.disabled = true; });
        card.setAttribute('aria-busy', 'true');
        card.draggable = false;
        destination.querySelector('.kanban-cards').append(card);
        select.value = status;
        message.textContent = '保存中…';
        try {
            const token = document.querySelector('meta[name="_csrf"]').content;
            const header = document.querySelector('meta[name="_csrf_header"]').content;
            const response = await fetch(card.dataset.url, {
                method: 'PATCH', credentials: 'same-origin', redirect: 'error',
                headers: { 'Content-Type': 'application/json', [header]: token },
                body: JSON.stringify({ status })
            });
            if (!response.ok) {
                const error = new Error('更新に失敗しました');
                error.status = response.status;
                throw error;
            }
            card.dataset.status = status;
            message.textContent = 'ステータスを更新しました。';
        } catch (error) {
            parent.insertBefore(card, next);
            select.value = previousStatus;
            message.textContent = error.status === 409
                ? 'サブタスクの完了状態と一致しないため、元の位置に戻しました。再読み込みして確認してください。'
                : '保存できなかったため、元の位置に戻しました。通信状態を確認し、再読み込みしてください。';
        } finally {
            pending = false;
            card.removeAttribute('aria-busy');
            card.draggable = true;
            board.querySelectorAll('select').forEach(input => { input.disabled = false; });
        }
    }
    board.addEventListener('change', event => {
        if (event.target.matches('.kanban-status')) move(event.target.closest('.kanban-card'), event.target.value);
    });
    board.addEventListener('dragstart', event => {
        const card = event.target.closest('.kanban-card');
        if (!card || pending || event.target.closest('a, select')) { event.preventDefault(); return; }
        dragged = card;
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', card.dataset.url);
        card.classList.add('dragging');
    });
    board.querySelectorAll('.kanban-column').forEach(column => {
        column.addEventListener('dragover', event => {
            if (!dragged || pending) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
            column.classList.add('drop-target');
        });
        column.addEventListener('dragleave', event => {
            if (!column.contains(event.relatedTarget)) column.classList.remove('drop-target');
        });
        column.addEventListener('drop', event => {
            event.preventDefault();
            column.classList.remove('drop-target');
            if (dragged) move(dragged, column.dataset.status);
        });
    });
    board.addEventListener('dragend', () => {
        if (dragged) dragged.classList.remove('dragging');
        dragged = null;
        board.querySelectorAll('.drop-target').forEach(column => column.classList.remove('drop-target'));
    });
})();
