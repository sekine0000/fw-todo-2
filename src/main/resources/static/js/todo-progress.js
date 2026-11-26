(() => {
    const panel = document.getElementById('todo-progress');
    if (!panel) return;
    const overall = panel.querySelector('[data-overall]');
    const categories = panel.querySelector('[data-categories]');
    const status = panel.querySelector('[data-status]');
    const rows = new Map();
    let loading = false;

    function createRow(container, name) {
        const row = document.createElement('div');
        row.className = 'progress-row';
        const label = document.createElement('div');
        label.className = 'progress-label';
        const title = document.createElement('span');
        title.textContent = name;
        const value = document.createElement('span');
        label.append(title, value);
        const track = document.createElement('div');
        track.className = 'progress-track';
        track.setAttribute('role', 'progressbar');
        track.setAttribute('aria-label', name);
        track.setAttribute('aria-valuemin', '0');
        track.setAttribute('aria-valuemax', '100');
        const fill = document.createElement('div');
        fill.className = 'progress-fill';
        track.append(fill);
        row.append(label, track);
        container.append(row);
        return { row, value, track, fill };
    }

    function updateRow(elements, data) {
        const percentage = data.percentage;
        const text = `${Number(percentage.toFixed(1))}%（${data.completed} / ${data.total}件）`;
        elements.value.textContent = text;
        elements.track.setAttribute('aria-valuenow', String(percentage));
        elements.track.setAttribute('aria-valuetext', text);
        elements.fill.dataset.level = percentage < 30 ? 'low' : percentage < 70 ? 'medium' : 'high';
        // Commit the initial zero width before starting the CSS transition.
        void elements.fill.offsetWidth;
        elements.fill.style.width = `${percentage}%`;
    }

    const overallRow = createRow(overall, '全体');
    async function refresh() {
        if (loading) return;
        loading = true;
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        try {
            const response = await fetch(panel.dataset.url, {
                cache: 'no-store', signal: controller.signal, headers: { Accept: 'application/json' }
            });
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) {
                throw new Error('Progress request failed');
            }
            const data = await response.json();
            updateRow(overallRow, data.overall);
            const names = new Set(data.categories.map(item => item.category));
            for (const [name, elements] of rows) {
                if (!names.has(name)) { elements.row.remove(); rows.delete(name); }
            }
            for (const item of data.categories) {
                if (!rows.has(item.category)) rows.set(item.category, createRow(categories, item.category));
                const elements = rows.get(item.category);
                categories.append(elements.row);
                updateRow(elements, item);
            }
            status.textContent = data.overall.total === 0
                ? 'タスクはありません。30秒ごとに更新します。'
                : `最終更新: ${new Date().toLocaleTimeString('ja-JP')}（30秒ごとに更新）`;
        } catch (error) {
            status.textContent = '進捗を取得できませんでした。30秒後に再試行します。';
        } finally {
            clearTimeout(timeout);
            loading = false;
        }
    }
    refresh();
    setInterval(refresh, 30000);
})();
