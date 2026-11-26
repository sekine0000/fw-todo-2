(() => {
    const svg = document.getElementById('completion-heatmap');
    if (!svg) return;
    const days = Object.entries(window.completionHeatmapData || {}).sort(([a], [b]) => a.localeCompare(b));
    if (!days.length) return;
    const tooltip = document.getElementById('heatmap-tooltip');
    const namespace = 'http://www.w3.org/2000/svg';
    const first = new Date(`${days[0][0]}T00:00:00Z`).getUTCDay();
    const size = 12, step = 16, left = 28, top = 20;
    svg.setAttribute('viewBox', '0 0 878 132');
    svg.setAttribute('width', '878');
    svg.setAttribute('height', '132');
    const text = (x, y, label) => {
        const node = document.createElementNS(namespace, 'text');
        node.setAttribute('x', x);
        node.setAttribute('y', y);
        node.textContent = label;
        svg.append(node);
    };
    ['日', '月', '火', '水', '木', '金', '土'].forEach((label, row) => text(0, top + row * step + 10, label));
    // Always reserve 53 weeks x 7 days; dates outside the 365-day range stay blank.
    for (let cell = 0; cell < 371; cell++) {
        const index = cell - first;
        const day = days[index];
        const rect = document.createElementNS(namespace, 'rect');
        const x = left + Math.floor(cell / 7) * step;
        const y = top + (cell % 7) * step;
        rect.setAttribute('x', x);
        rect.setAttribute('y', y);
        rect.setAttribute('width', size);
        rect.setAttribute('height', size);
        rect.setAttribute('rx', '2');
        if (day) {
            const [date, count] = day;
            const level = count === 0 ? 0 : count <= 2 ? 1 : count <= 5 ? 2 : count <= 9 ? 3 : 4;
            rect.setAttribute('class', `heatmap-cell heatmap-level-${level}`);
            rect.setAttribute('tabindex', '0');
            rect.setAttribute('aria-label', `${date}: ${count}件完了`);
            const show = () => {
                tooltip.textContent = `${date}: ${count}件完了`;
                tooltip.hidden = false;
                const bounds = rect.getBoundingClientRect();
                const width = tooltip.getBoundingClientRect().width;
                tooltip.style.left = `${Math.max(8, Math.min(bounds.left, window.innerWidth - width - 8))}px`;
                tooltip.style.top = `${Math.max(8, bounds.top - 36)}px`;
                rect.setAttribute('aria-describedby', 'heatmap-tooltip');
            };
            const hide = () => { tooltip.hidden = true; rect.removeAttribute('aria-describedby'); };
            rect.addEventListener('mouseenter', show);
            rect.addEventListener('mouseleave', hide);
            rect.addEventListener('focus', show);
            rect.addEventListener('blur', hide);
            rect.addEventListener('keydown', event => { if (event.key === 'Escape') hide(); });
            const title = document.createElementNS(namespace, 'title');
            title.textContent = `${date}: ${count}件完了`;
            rect.append(title);
            if (date.endsWith('-01') || index === 0) text(x, 12, `${Number(date.slice(5, 7))}月`);
        } else {
            rect.setAttribute('class', 'heatmap-padding');
            rect.setAttribute('aria-hidden', 'true');
        }
        svg.append(rect);
    }
    window.addEventListener('scroll', () => { tooltip.hidden = true; }, true);
    window.addEventListener('resize', () => { tooltip.hidden = true; });
})();
