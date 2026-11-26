(() => {
    const colors = ['#38bdf8', '#a78bfa', '#34d399', '#fbbf24', '#fb7185', '#22d3ee'];
    const totalPlugin = {
        id: 'taskTotal',
        afterDraw(chart) {
            const {ctx, chartArea} = chart;
            if (!chartArea) return;
            const total = chart.data.datasets[0].data.reduce((sum, count) => sum + count, 0);
            const x = (chartArea.left + chartArea.right) / 2;
            const y = (chartArea.top + chartArea.bottom) / 2;
            ctx.save();
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillStyle = '#cbd5e1';
            ctx.font = 'bold 28px sans-serif';
            ctx.fillText(String(total), x, y - 10);
            ctx.font = '14px sans-serif';
            ctx.fillText('タスク総数', x, y + 20);
            ctx.restore();
        }
    };

    async function render(kind, type, labelKey, palette) {
        const canvas = document.getElementById(kind === 'categories' ? 'category-chart' : 'priority-chart');
        const message = document.createElement('p');
        message.setAttribute('role', 'status');
        canvas.parentElement.after(message);
        try {
            if (typeof Chart === 'undefined') throw new Error('Chart.js unavailable');
            const response = await fetch(window.distributionUrls[kind], {headers: {Accept: 'application/json'}});
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const rows = await response.json();
            const total = rows.reduce((sum, row) => sum + row.taskCount, 0);
            message.textContent = total ? '' : '集計対象のタスクがありません。';
            canvas.setAttribute('aria-label', rows.map(row => `${row[labelKey]}: ${row.taskCount}件`).join('、') || 'タスク総数: 0件');
            if (kind === 'priorities') {
                document.getElementById('priority-summary').textContent = rows.map(row => `${row.priority}: ${row.taskCount}件`).join(' / ');
            }
            new Chart(canvas, {
                type,
                data: {labels: rows.map(row => row[labelKey]), datasets: [{
                    data: rows.map(row => row.taskCount),
                    backgroundColor: rows.map((_, i) => palette[i % palette.length]), borderWidth: 0
                }]},
                options: {responsive: true, maintainAspectRatio: false,
                    ...(type === 'doughnut' ? {cutout: '65%'} : {}),
                    plugins: {legend: {position: 'bottom'}}},
                plugins: type === 'doughnut' ? [totalPlugin] : []
            });
        } catch (error) {
            message.textContent = 'グラフを読み込めませんでした。ページを再読み込みしてください。';
            if (kind === 'priorities') document.getElementById('priority-summary').textContent = '';
        }
    }
    render('categories', 'doughnut', 'category', colors);
    render('priorities', 'pie', 'priority', ['#fb7185', '#fbbf24', '#34d399']);
})();
