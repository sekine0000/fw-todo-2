'use strict';
const items = document.getElementById('template-items');
const addButton = document.getElementById('add-item');

function updateItems() {
    const rows = [...items.children];
    rows.forEach((row, index) => {
        const id = `item-${index}`;
        row.querySelector('label').htmlFor = id;
        row.querySelector('label').textContent = `項目 ${index + 1}`;
        row.querySelector('input').id = id;
        row.querySelector('button').disabled = rows.length === 1;
    });
    addButton.disabled = rows.length >= 100;
}

addButton.addEventListener('click', () => {
    if (items.children.length >= 100) return;
    const row = items.firstElementChild.cloneNode(true);
    row.querySelector('input').value = '';
    items.appendChild(row);
    updateItems();
    row.querySelector('input').focus();
});

items.addEventListener('click', event => {
    if (event.target.matches('.remove-item') && items.children.length > 1) {
        event.target.closest('.template-item').remove();
        updateItems();
    }
});
updateItems();
