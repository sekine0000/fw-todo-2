(() => {
    'use strict';
    const root = document.getElementById('task-dependencies');
    if (!root) return;
    const id = Number(root.dataset.taskId);
    const api = root.dataset.apiUrl;
    const select = document.getElementById('prerequisite-select');
    const add = document.getElementById('dependency-add');
    const list = document.getElementById('prerequisite-list');
    const message = document.getElementById('dependency-message');
    const status = document.getElementById('dependency-status');
    const reload = document.getElementById('dependency-reload');
    const svg = document.getElementById('dependency-graph');
    let busy = false;

    async function request(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) {
            const messages = {409: '循環依存になるため追加できません。', 404: 'タスクが見つかりません。再読み込みしてください。',
                400: 'タスクの指定が不正です。'};
            throw new Error(messages[response.status] || '依存関係の取得・更新に失敗しました。');
        }
        return response.status === 204 ? null : response.json();
    }

    function element(name, attributes = {}, text) {
        const node = document.createElementNS('http://www.w3.org/2000/svg', name);
        Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function draw(tasks, allEdges, blockedIds) {
        svg.replaceChildren();
        // Limit the display to this task's connected component, including shared prerequisites.
        const included = new Set([id]);
        const neighbours = new Map(tasks.map(t => [t.id, []]));
        allEdges.forEach(e => {
            neighbours.get(e.prerequisiteId).push(e.successorId);
            neighbours.get(e.successorId).push(e.prerequisiteId);
        });
        const queue = [id];
        for (let i = 0; i < queue.length; i++) {
            for (const next of neighbours.get(queue[i]) || []) {
                if (!included.has(next)) { included.add(next); queue.push(next); }
            }
        }
        const nodes = tasks.filter(t => included.has(t.id));
        const edges = allEdges.filter(e => included.has(e.prerequisiteId) && included.has(e.successorId));
        const indegree = new Map(nodes.map(t => [t.id, 0]));
        const level = new Map(nodes.map(t => [t.id, 0]));
        const successors = new Map(nodes.map(t => [t.id, []]));
        edges.forEach(e => {
            indegree.set(e.successorId, indegree.get(e.successorId) + 1);
            successors.get(e.prerequisiteId).push(e.successorId);
        });
        const ready = nodes.filter(t => indegree.get(t.id) === 0).map(t => t.id);
        for (let i = 0; i < ready.length; i++) {
            const current = ready[i];
            for (const next of successors.get(current)) {
                level.set(next, Math.max(level.get(next), level.get(current) + 1));
                indegree.set(next, indegree.get(next) - 1);
                if (indegree.get(next) === 0) ready.push(next);
            }
        }
        const rows = new Map();
        const positions = new Map();
        nodes.forEach(t => {
            const column = level.get(t.id);
            const row = rows.get(column) || 0;
            rows.set(column, row + 1);
            positions.set(t.id, {x: 20 + column * 270, y: 20 + row * 100});
        });
        const width = Math.max(280, ...Array.from(positions.values(), p => p.x + 240));
        const height = Math.max(120, ...Array.from(positions.values(), p => p.y + 90));
        svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
        svg.setAttribute('width', width);
        svg.setAttribute('height', height);
        const defs = element('defs');
        const marker = element('marker', {id: 'dependency-arrow', viewBox: '0 0 10 10', refX: 9,
            refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto'});
        marker.append(element('path', {d: 'M 0 0 L 10 5 L 0 10 z', fill: '#475569'}));
        defs.append(marker);
        svg.append(defs);
        edges.forEach(e => {
            const from = positions.get(e.prerequisiteId), to = positions.get(e.successorId);
            svg.append(element('path', {d: `M ${from.x + 220} ${from.y + 32} C ${from.x + 245} ${from.y + 32}, ${to.x - 25} ${to.y + 32}, ${to.x} ${to.y + 32}`,
                fill: 'none', stroke: '#475569', 'stroke-width': 2, 'marker-end': 'url(#dependency-arrow)'}));
        });
        nodes.forEach(t => {
            const p = positions.get(t.id);
            const group = element('g');
            group.append(element('title', {}, t.title));
            group.append(element('rect', {x: p.x, y: p.y, width: 220, height: 64, rx: 8,
                fill: t.completed ? '#dcfce7' : blockedIds.includes(t.id) ? '#fef3c7' : '#f1f5f9',
                stroke: t.id === id ? '#2563eb' : '#64748b', 'stroke-width': t.id === id ? 3 : 1}));
            const title = Array.from(t.title);
            group.append(element('text', {x: p.x + 10, y: p.y + 25, fill: '#0f172a', 'font-size': 13},
                title.length > 15 ? title.slice(0, 15).join('') + '…' : t.title));
            group.append(element('text', {x: p.x + 10, y: p.y + 48, fill: '#334155', 'font-size': 12},
                `#${t.id} ${blockedIds.includes(t.id) ? 'ブロック中' : t.completed ? '完了' : '未完了'}`));
            svg.append(group);
        });
    }

    function render(graph) {
        const prerequisites = graph.edges.filter(e => e.successorId === id).map(e => e.prerequisiteId);
        const current = graph.tasks.find(t => t.id === id);
        status.textContent = graph.blockedIds.includes(id) ? 'ブロック中（未完了の前提タスクがあります）' : current.completed ? '完了' : '未完了';
        select.replaceChildren();
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = '前提タスクを選択';
        select.append(placeholder);
        graph.tasks.filter(t => t.id !== id && !prerequisites.includes(t.id)).forEach(t => {
            const option = document.createElement('option');
            option.value = t.id;
            option.textContent = `#${t.id} ${t.title}`;
            select.append(option);
        });
        list.replaceChildren();
        graph.tasks.filter(t => prerequisites.includes(t.id)).forEach(t => {
            const item = document.createElement('li');
            const label = document.createElement('span');
            label.textContent = `#${t.id} ${t.title}（${t.completed ? '完了' : '未完了'}） `;
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'btn';
            button.textContent = '削除';
            button.setAttribute('aria-label', `${t.title}を前提タスクから削除`);
            button.addEventListener('click', () => run(() => request(`${api}/${t.id}`, {method: 'DELETE'})));
            item.append(label, button);
            list.append(item);
        });
        if (!prerequisites.length) {
            const item = document.createElement('li');
            item.textContent = '前提タスクはありません。';
            list.append(item);
        }
        draw(graph.tasks, graph.edges, graph.blockedIds);
    }

    async function run(mutation) {
        if (busy) return;
        busy = true;
        root.querySelectorAll('button, select').forEach(el => { el.disabled = true; });
        message.textContent = '';
        try {
            if (mutation) await mutation();
            render(await request(api));
        } catch (error) {
            message.textContent = error.message;
        } finally {
            busy = false;
            root.querySelectorAll('button, select').forEach(el => { el.disabled = false; });
            select.disabled = add.disabled = select.options.length <= 1;
        }
    }
    document.getElementById('dependency-form').addEventListener('submit', event => {
        event.preventDefault();
        if (select.value) run(() => request(api, {method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({prerequisiteId: Number(select.value)})}));
    });
    reload.addEventListener('click', () => run());
    run();
})();
