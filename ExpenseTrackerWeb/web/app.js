(() => {
  const $ = (s) => document.querySelector(s);
  const state = { meta: null, expenses: [], sortKey: 'date', sortAsc: false, editingId: null };

  const money = (v) => '₹' + Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const fmtDate = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  const fmtMonth = (ym) => new Date(ym + '-01T00:00:00').toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const catOf = (key) => state.meta.categories.find((c) => c.key === key);

  async function api(path, opts = {}) {
    const res = await fetch(path, { headers: { 'Content-Type': 'application/json' }, ...opts });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || 'Request failed');
    return data;
  }

  let toastTimer;
  function toast(msg, isErr) {
    const t = $('#toast');
    t.textContent = msg; t.className = 'toast' + (isErr ? ' err' : ''); t.hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 2600);
  }

  // ---------- filters ----------
  function filterParams() {
    const p = new URLSearchParams();
    if ($('#search').value.trim()) p.set('q', $('#search').value.trim());
    if ($('#categoryFilter').value) p.set('category', $('#categoryFilter').value);
    if ($('#monthFilter').value) p.set('month', $('#monthFilter').value);
    return p.toString();
  }

  function fillSelects() {
    const cf = $('#categoryFilter'), mf = $('#monthFilter'), form = $('#expenseForm').category;
    const cv = cf.value, mv = mf.value;
    cf.innerHTML = '<option value="">All categories</option>' + state.meta.categories.map((c) => `<option value="${c.key}">${esc(c.label)}</option>`).join('');
    mf.innerHTML = '<option value="">All months</option>' + state.meta.months.map((m) => `<option value="${m}">${fmtMonth(m)}</option>`).join('');
    cf.value = cv; mf.value = mv;
    if (!form.options.length) form.innerHTML = state.meta.categories.map((c) => `<option value="${c.key}">${esc(c.label)}</option>`).join('');
  }

  // ---------- render ----------
  async function loadMeta() {
    state.meta = await api('/api/meta');
    fillSelects();
    renderBudget();
  }

  async function loadList() {
    const data = await api('/api/expenses?' + filterParams());
    state.expenses = data.expenses;
    $('#totalLbl').textContent = money(data.total);
    $('#countLbl').textContent = data.count;
    renderTable();
    renderChart(data.byCategory, data.total);
    $('#exportBtn').href = '/api/export?' + filterParams();
  }

  async function refresh() { await loadMeta(); await loadList(); }

  function renderBudget() {
    const { budget, spentThisMonth } = state.meta;
    $('#monthLbl').textContent = money(spentThisMonth);
    const fill = $('#budgetFill');
    if (budget > 0) {
      const pct = (spentThisMonth / budget) * 100;
      $('#budgetLbl').textContent = `${money(spentThisMonth)} / ${money(budget)}`;
      fill.style.width = Math.min(pct, 100) + '%';
      fill.style.background = pct >= 100 ? 'var(--danger)' : pct >= 80 ? 'var(--warning)' : 'var(--success)';
    } else {
      $('#budgetLbl').textContent = 'Not set';
      fill.style.width = '0';
    }
  }

  function renderTable() {
    const k = state.sortKey, dir = state.sortAsc ? 1 : -1;
    const rows = [...state.expenses].sort((a, b) => {
      const x = k === 'amount' ? a.amount : String(a[k]).toLowerCase();
      const y = k === 'amount' ? b.amount : String(b[k]).toLowerCase();
      return (x < y ? -1 : x > y ? 1 : b.id - a.id) * dir;
    });
    $('#rows').innerHTML = rows.map((e) => {
      const c = catOf(e.category);
      return `<tr>
        <td>${fmtDate(e.date)}</td>
        <td>${esc(e.title)}</td>
        <td><span class="chip" style="--c:${c.color}">${esc(c.label)}</span></td>
        <td class="num">${money(e.amount)}</td>
        <td class="note" title="${esc(e.note)}">${esc(e.note)}</td>
        <td><button class="icon-btn" data-edit="${e.id}" title="Edit">✏️</button><button class="icon-btn" data-del="${e.id}" title="Delete">🗑️</button></td>
      </tr>`;
    }).join('');
    $('#empty').hidden = rows.length > 0;
    document.querySelectorAll('th[data-sort]').forEach((th) => {
      th.classList.toggle('sorted', th.dataset.sort === k);
      th.classList.toggle('asc', th.dataset.sort === k && state.sortAsc);
    });
  }

  function arc(cx, cy, r0, r1, a0, a1) {
    const p = (r, a) => [cx + r * Math.cos(a), cy + r * Math.sin(a)];
    const large = a1 - a0 > Math.PI ? 1 : 0;
    const [x0, y0] = p(r1, a0), [x1, y1] = p(r1, a1), [x2, y2] = p(r0, a1), [x3, y3] = p(r0, a0);
    return `M${x0} ${y0}A${r1} ${r1} 0 ${large} 1 ${x1} ${y1}L${x2} ${y2}A${r0} ${r0} 0 ${large} 0 ${x3} ${y3}Z`;
  }

  function renderChart(byCat, total) {
    $('#donutTotal').textContent = '₹' + Math.round(total).toLocaleString('en-IN');
    const items = Object.entries(byCat).sort((a, b) => b[1] - a[1]);
    const svg = $('#donut');
    if (!items.length || total <= 0) {
      svg.innerHTML = '<circle cx="100" cy="100" r="75" fill="none" stroke="#e2e8f0" stroke-width="30"/>';
      $('#legend').innerHTML = '<li class="empty" style="display:block">No data</li>';
      return;
    }
    let a = 0;
    svg.innerHTML = items.map(([key, val]) => {
      const c = catOf(key), sweep = (val / total) * Math.PI * 2;
      if (items.length === 1) return `<circle cx="100" cy="100" r="75" fill="none" stroke="${c.color}" stroke-width="30"><title>${esc(c.label)}: ${money(val)}</title></circle>`;
      const d = arc(100, 100, 60, 90, a, a + sweep - 0.012); a += sweep;
      return `<path d="${d}" fill="${c.color}"><title>${esc(c.label)}: ${money(val)}</title></path>`;
    }).join('');
    $('#legend').innerHTML = items.map(([key, val]) => {
      const c = catOf(key);
      return `<li style="--c:${c.color}"><i></i><span class="name">${esc(c.label)}</span><span class="amt">${money(val)}</span><span class="pct">${(val / total * 100).toFixed(0)}%</span></li>`;
    }).join('');
  }

  // ---------- add / edit ----------
  const dlg = $('#expenseDlg'), form = $('#expenseForm');
  const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);

  function openForm(exp) {
    state.editingId = exp ? exp.id : null;
    $('#dlgTitle').textContent = exp ? 'Edit Expense' : 'Add Expense';
    form.elements.title.value = exp ? exp.title : '';
    form.amount.value = exp ? exp.amount : '';
    form.date.value = exp ? exp.date : today();
    form.date.max = today();
    form.category.value = exp ? exp.category : state.meta.categories[0].key;
    form.note.value = exp ? exp.note : '';
    $('#formError').hidden = true;
    dlg.showModal();
    form.elements.title.focus();
  }

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const body = JSON.stringify({ title: form.elements.title.value, amount: form.amount.value, date: form.date.value, category: form.category.value, note: form.note.value });
    try {
      if (state.editingId) await api('/api/expenses/' + state.editingId, { method: 'PUT', body });
      else await api('/api/expenses', { method: 'POST', body });
      dlg.close();
      toast(state.editingId ? 'Expense updated' : 'Expense added');
      await refresh();
    } catch (e) {
      const el = $('#formError'); el.textContent = e.message; el.hidden = false;
    }
  });

  $('#addBtn').onclick = () => openForm(null);
  $('#cancelBtn').onclick = () => dlg.close();

  $('#rows').addEventListener('click', async (ev) => {
    const edit = ev.target.closest('[data-edit]'), del = ev.target.closest('[data-del]');
    if (edit) openForm(state.expenses.find((e) => e.id === +edit.dataset.edit));
    if (del) {
      const e = state.expenses.find((x) => x.id === +del.dataset.del);
      if (!confirm(`Delete "${e.title}" (${money(e.amount)})?`)) return;
      try { await api('/api/expenses/' + e.id, { method: 'DELETE' }); toast('Expense deleted'); await refresh(); }
      catch (err) { toast(err.message, true); }
    }
  });

  // ---------- budget ----------
  const bdlg = $('#budgetDlg'), bform = $('#budgetForm');
  $('#budgetBtn').onclick = () => { bform.amount.value = state.meta.budget || ''; bdlg.showModal(); bform.amount.focus(); };
  $('#budgetCancel').onclick = () => bdlg.close();
  bform.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    try {
      await api('/api/budget', { method: 'PUT', body: JSON.stringify({ amount: bform.amount.value }) });
      bdlg.close(); toast('Budget saved'); await loadMeta();
    } catch (e) { toast(e.message, true); }
  });

  // ---------- events ----------
  let debounce;
  $('#search').addEventListener('input', () => { clearTimeout(debounce); debounce = setTimeout(loadList, 200); });
  $('#categoryFilter').addEventListener('change', loadList);
  $('#monthFilter').addEventListener('change', loadList);
  document.querySelectorAll('th[data-sort]').forEach((th) => th.addEventListener('click', () => {
    const k = th.dataset.sort;
    state.sortAsc = state.sortKey === k ? !state.sortAsc : k !== 'date' && k !== 'amount';
    state.sortKey = k; renderTable();
  }));
  [dlg, bdlg].forEach((d) => d.addEventListener('click', (e) => { if (e.target === d) d.close(); }));

  refresh().catch((e) => toast(e.message, true));
})();
