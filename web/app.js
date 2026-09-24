/* ===== SeguroChain — Shared Frontend Logic ===== */
/* Used by both index.html (Seguradora) and assegurado.html (Assegurado) */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);
let state = { policies: [], claims: [] };
const isAssegurado = document.body.classList.contains('assegurado-page');

// ===== Utilities =====
const money = (v) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v);
const dateStr = (v) => { try { return new Date(`${v}T12:00:00`).toLocaleDateString('pt-BR'); } catch { return v; } };
const api = async (url, opts = {}) => {
  const r = await fetch(url, opts);
  const d = await r.json();
  if (!r.ok) throw new Error(d.error || 'Não foi possível concluir a operação.');
  return d;
};

function toast(msg) {
  const el = $('#toast');
  if (!el) return;
  el.textContent = msg;
  el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 3500);
}

function badge(status) {
  return `<span class="badge ${status.replace(' ', '_')}">${status.replace(/_/g, ' ')}</span>`;
}

// ===== Modal Handling (fixes cancel/close bugs) =====
function setupModals() {
  // Close buttons (×)
  $$('.modal-close-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const dialog = btn.closest('dialog');
      if (dialog) dialog.close();
    });
  });

  // Cancel buttons
  $$('.modal-cancel-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const dialog = btn.closest('dialog');
      if (dialog) dialog.close();
    });
  });

  // Close on backdrop click
  $$('dialog').forEach(dialog => {
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) dialog.close();
    });
  });

  // Open buttons
  $$('[data-open]').forEach(button => {
    button.addEventListener('click', () => {
      const targetId = button.dataset.open;

      // For claim modals, check active policies first
      if (targetId === 'claim-modal' || targetId === 'claim-modal-client') {
        if (!state.policies.some(p => p.status === 'ATIVA')) {
          return toast('Emita uma apólice ativa antes de abrir um sinistro.');
        }
        populatePolicySelect(targetId);
      }

      const dialog = $(`#${targetId}`);
      if (dialog) dialog.showModal();
    });
  });
}

// ===== Mobile Menu =====
function setupMobileMenu() {
  const btn = $('#mobile-menu-btn');
  const sidebar = $('#sidebar');
  const overlay = $('#sidebar-overlay');
  if (!btn || !sidebar) return;

  btn.addEventListener('click', () => {
    sidebar.classList.toggle('open');
    overlay.classList.toggle('show');
  });
  if (overlay) {
    overlay.addEventListener('click', () => {
      sidebar.classList.remove('open');
      overlay.classList.remove('show');
    });
  }
}

// ===== Smooth nav highlighting =====
function setupNav() {
  $$('[data-nav]').forEach(a => {
    a.addEventListener('click', () => {
      $$('[data-nav]').forEach(n => n.classList.remove('active'));
      a.classList.add('active');
      // Close mobile menu
      const sidebar = $('#sidebar');
      const overlay = $('#sidebar-overlay');
      if (sidebar) sidebar.classList.remove('open');
      if (overlay) overlay.classList.remove('show');
    });
  });
}

// ===== Scroll Controls =====
function setupScrollControls() {
  $$('.scroll-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const panel = $(`#${btn.dataset.target}`);
      if (!panel) return;
      const amount = 200;
      panel.scrollBy({ top: btn.dataset.dir === 'down' ? amount : -amount, behavior: 'smooth' });
    });
  });
}

function updateScrollVisibility(panelId, scrollId, itemCount) {
  const panel = $(`#${panelId}`);
  const controls = $(`#${scrollId}`);
  if (!panel || !controls) return;
  // Show scroll controls if content overflows
  setTimeout(() => {
    controls.style.display = panel.scrollHeight > panel.clientHeight + 10 ? 'flex' : 'none';
  }, 100);
}

// ===== Populate policy select =====
function populatePolicySelect(modalId) {
  const selectId = modalId === 'claim-modal-client' ? '#c-claim-policy' : '#claim-policy';
  const sel = $(selectId);
  if (!sel) return;
  sel.innerHTML = state.policies
    .filter(p => p.status === 'ATIVA')
    .map(p => `<option value="${p.id}">#${p.id} · ${p.client} · ${p.vehicle}</option>`)
    .join('');
}

// ===== Difficulty Slider =====
function setupDifficultySlider() {
  const slider = $('#difficulty-slider');
  const display = $('#difficulty-value');
  if (!slider || !display) return;

  // Load current difficulty
  api('/api/difficulty').then(d => {
    slider.value = d.difficulty;
    display.textContent = d.difficulty;
  }).catch(() => {});

  slider.addEventListener('input', () => {
    display.textContent = slider.value;
  });

  slider.addEventListener('change', async () => {
    try {
      await api('/api/difficulty', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `difficulty=${slider.value}`
      });
      toast(`Dificuldade alterada para ${slider.value}.`);
    } catch (err) {
      toast(err.message);
    }
  });
}

// ===== Data Refresh =====
async function refresh() {
  try {
    state = await api('/api/state');
    render();
    await renderLedger();
  } catch (error) {
    toast(`Servidor indisponível: ${error.message}`);
  }
}

// ===== SEGURADORA RENDER =====
function renderSeguradora() {
  const { policies, claims } = state;

  const el = (id) => $(id);

  el('#metric-policies').textContent = policies.filter(p => p.status === 'ATIVA').length;
  el('#metric-pending').textContent = claims.filter(c => c.status === 'EM_ANALISE').length;
  el('#metric-paid').textContent = money(claims.filter(c => c.status === 'PAGO').reduce((s, c) => s + c.amount, 0));

  el('#policies-empty').hidden = policies.length > 0;
  el('#policies-body').innerHTML = policies.map(p => `
    <tr>
      <td><strong>#${p.id}</strong><small>Emitida no servidor local</small></td>
      <td><strong>${p.client}</strong><small>${p.vehicle} · ${p.plate}</small></td>
      <td class="cover">${p.covers.join(' · ')}</td>
      <td>${dateStr(p.start)}<small>até ${dateStr(p.end)}</small></td>
      <td>${badge(p.status)}</td>
      <td><button class="button secondary table-action" onclick="openClaimFor('${p.id}')"><i class="ri-alarm-warning-line"></i> Abrir sinistro</button></td>
    </tr>`).join('');

  el('#claims-empty').hidden = claims.length > 0;
  el('#claims-body').innerHTML = claims.map(c => `
    <tr>
      <td><strong>#${c.id}</strong><small>${c.client}</small></td>
      <td>#${c.policyId}</td>
      <td><strong>${c.type}</strong><small>${dateStr(c.date)}</small></td>
      <td>${money(c.amount)}</td>
      <td>${badge(c.status)}</td>
      <td>${c.status === 'EM_ANALISE'
        ? `<button class="button primary table-action" onclick="analyse('${c.id}')"><i class="ri-cpu-line"></i> Analisar</button>`
        : c.status === 'APROVADO'
          ? `<button class="button primary table-action" onclick="pay('${c.id}')"><i class="ri-money-dollar-circle-line"></i> Pagar</button>`
          : `<button class="button secondary table-action" onclick="details('${c.id}')"><i class="ri-eye-line"></i> Ver</button>`
      }</td>
    </tr>`).join('');

  const pending = claims.find(c => c.status === 'EM_ANALISE');
  el('#action-title').textContent = pending ? `Sinistro #${pending.id} aguarda análise` : 'Nenhuma pendência';
  el('#action-description').textContent = pending
    ? 'Execute o Smart Contract Java para aplicar as regras da apólice.'
    : 'Abra um sinistro para iniciar uma análise automatizada.';
  el('#action-link').textContent = pending ? 'Analisar agora' : 'Ver sinistros';
  el('#action-link').onclick = pending ? (e) => { e.preventDefault(); analyse(pending.id); } : null;

  updateScrollVisibility('policies-panel', 'policies-scroll', policies.length);
  updateScrollVisibility('claims-panel', 'claims-scroll', claims.length);
}

// ===== ASSEGURADO RENDER =====
function renderAssegurado() {
  const { policies, claims } = state;

  const el = (id) => { const e = $(id); return e || { textContent: '', hidden: false, innerHTML: '' }; };

  el('#c-metric-policies').textContent = policies.filter(p => p.status === 'ATIVA').length;
  el('#c-metric-pending').textContent = claims.filter(c => c.status === 'EM_ANALISE').length;
  el('#c-metric-resolved').textContent = claims.filter(c => c.status === 'PAGO' || c.status === 'APROVADO' || c.status === 'REJEITADO').length;

  const pEmpty = $('#c-policies-empty');
  if (pEmpty) pEmpty.hidden = policies.length > 0;
  const pBody = $('#c-policies-body');
  if (pBody) pBody.innerHTML = policies.map(p => `
    <tr>
      <td><strong>#${p.id}</strong></td>
      <td><strong>${p.vehicle}</strong><small>${p.plate}</small></td>
      <td class="cover">${p.covers.join(' · ')}</td>
      <td>${dateStr(p.start)}<small>até ${dateStr(p.end)}</small></td>
      <td>${money(p.coverageValue)}</td>
      <td>${badge(p.status)}</td>
    </tr>`).join('');

  const cEmpty = $('#c-claims-empty');
  if (cEmpty) cEmpty.hidden = claims.length > 0;
  const cBody = $('#c-claims-body');
  if (cBody) cBody.innerHTML = claims.map(c => `
    <tr>
      <td><strong>#${c.id}</strong></td>
      <td>#${c.policyId}</td>
      <td>${c.type}</td>
      <td>${dateStr(c.date)}</td>
      <td>${money(c.amount)}</td>
      <td>${badge(c.status)}</td>
      <td>${c.status !== 'EM_ANALISE'
        ? `<button class="button secondary table-action" onclick="detailsClient('${c.id}')"><i class="ri-eye-line"></i> Ver decisão</button>`
        : '<span class="muted" style="font-size:12px">Aguardando...</span>'
      }</td>
    </tr>`).join('');

  updateScrollVisibility('c-policies-panel', 'c-policies-scroll', policies.length);
  updateScrollVisibility('c-claims-panel', 'c-claims-scroll', claims.length);
}

function render() {
  if (isAssegurado) renderAssegurado();
  else renderSeguradora();
}

// ===== Ledger / Blockchain Render =====
async function renderLedger() {
  try {
    const ledger = await api('/api/ledger');

    // Seguradora chain state
    const chainState = isAssegurado ? $('#c-chain-state') : $('#chain-state');
    if (chainState) {
      chainState.innerHTML = `<span class="dot"></span> ${ledger.valid ? 'Cadeia íntegra' : '<span style="color:var(--red)">⚠ Cadeia inválida</span>'}`;
    }

    const container = isAssegurado ? $('#c-ledger') : $('#ledger');
    if (!container) return;

    if (ledger.blocks.length === 0) {
      container.innerHTML = '<div class="empty">Nenhum bloco na cadeia.</div>';
      return;
    }

    container.innerHTML = ledger.blocks.slice().reverse().map(b => {
      const isGenesis = b.index === 0;
      const title = isGenesis ? 'Bloco gênesis' : (b.data.split(' | ')[0] || '').replace(/_/g, ' ');
      const desc = isGenesis ? 'Origem da cadeia local' : b.data;
      const prevHash = b.previousHash === '0' ? '0 (gênesis)' : (b.previousHash.length > 20 ? b.previousHash.slice(0, 20) + '…' : b.previousHash);
      const hashDisplay = b.hash.length > 20 ? b.hash.slice(0, 20) + '…' : b.hash;

      return `<article class="event">
        <div class="event-icon ${isGenesis ? 'genesis' : ''}">${isGenesis ? '<i class="ri-shield-keyhole-fill"></i>' : '#' + b.index}</div>
        <div>
          <strong>${title}</strong>
          <p>${desc}</p>
          <div class="event-meta">
            <span title="Hash anterior">⬅ ${prevHash}</span>
            <span title="Nonce (Proof of Work)">⛏ Nonce: ${b.nonce}</span>
            <span title="Timestamp">${isGenesis ? '🕐 Gênesis' : '🕐 ' + new Date(b.timestamp).toLocaleString('pt-BR')}</span>
          </div>
        </div>
        <span class="hash" title="${b.hash}">${hashDisplay}</span>
      </article>`;
    }).join('');

    const panelId = isAssegurado ? 'c-ledger-panel' : 'ledger-panel';
    const scrollId = isAssegurado ? 'c-ledger-scroll' : 'ledger-scroll';
    updateScrollVisibility(panelId, scrollId, ledger.blocks.length);
  } catch (err) {
    // silently ignore ledger errors on initial load
  }
}

// ===== Actions (Seguradora) =====
window.openClaimFor = (id) => {
  populatePolicySelect('claim-modal');
  const sel = $('#claim-policy');
  if (sel) {
    for (const opt of sel.options) {
      if (opt.value === id) { opt.selected = true; break; }
    }
  }
  const modal = $('#claim-modal');
  if (modal) modal.showModal();
};

window.analyse = async (id) => {
  try {
    await api(`/api/claims/${id}/analyse`, { method: 'POST' });
    await refresh();
    const claim = state.claims.find(c => c.id === id);
    if (claim) showAnalysis(claim, false);
    toast('Decisão registrada pelo Smart Contract e na blockchain.');
  } catch (error) { toast(error.message); }
};

window.pay = async (id) => {
  try {
    await api(`/api/claims/${id}/pay`, { method: 'POST' });
    await refresh();
    // Close analysis modal if open
    const modal = $('#analysis-modal');
    if (modal && modal.open) modal.close();
    toast('Pagamento registrado em um novo bloco local.');
  } catch (error) { toast(error.message); }
};

window.details = (id) => {
  const claim = state.claims.find(c => c.id === id);
  if (claim) showAnalysis(claim, false);
};

window.detailsClient = (id) => {
  const claim = state.claims.find(c => c.id === id);
  if (claim) showAnalysis(claim, true);
};

function showAnalysis(claim, clientView) {
  const approved = claim.status !== 'REJEITADO';
  const contentId = clientView ? '#c-analysis-content' : '#analysis-content';
  const modalId = clientView ? '#c-analysis-modal' : '#analysis-modal';
  const content = $(contentId);
  const modal = $(modalId);
  if (!content || !modal) return;

  content.innerHTML = `
    <div class="modal-head">
      <div>
        <p class="eyebrow">DECISÃO DO SMART CONTRACT</p>
        <h2>Sinistro #${claim.id}</h2>
      </div>
      <button type="button" class="icon-button" onclick="this.closest('dialog').close()" aria-label="Fechar">×</button>
    </div>
    <div class="analysis-result ${approved ? 'ok' : 'no'}">
      <strong>${approved ? '✓ APROVADO' : '✗ REJEITADO'}</strong>
      <p>${claim.reason || 'Aguardando execução das regras.'}</p>
    </div>
    <h3>Regras executadas no servidor</h3>
    <ul class="rules">
      <li class="pass">✓ Existência e vigência da apólice</li>
      <li class="pass">✓ Cobertura para a ocorrência</li>
      <li class="pass">✓ Limite do valor segurado</li>
    </ul>
    <p class="hint">A decisão é gravada pela classe Blockchain do projeto e pode ser auditada na seção de auditoria.</p>
    <div class="modal-actions">
      <button type="button" class="button secondary" onclick="this.closest('dialog').close()">Fechar</button>
      ${!clientView && claim.status === 'APROVADO' ? `<button class="button primary" onclick="pay('${claim.id}')"><i class="ri-money-dollar-circle-line"></i> Registrar pagamento</button>` : ''}
    </div>`;
  modal.showModal();
}

// ===== Form Handling =====
function setupForms() {
  // Policy form (Seguradora only)
  const policyForm = $('#policy-form');
  if (policyForm) {
    policyForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const form = e.currentTarget;
      if (form.end.value < form.start.value) return toast('A data final deve ser posterior à inicial.');
      try {
        const body = new URLSearchParams(new FormData(form));
        // Manually add checkbox states
        $$('#policy-form input[name="cover"]').forEach(input => {
          body.set(`cover_${input.value}`, input.checked);
        });
        await api('/api/policies', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body
        });
        form.reset();
        // Restore today's date after reset
        setDatesOnForm(form);
        $('#policy-modal').close();
        await refresh();
        toast('Apólice emitida com sucesso.');
      } catch (error) { toast(error.message); }
    });
  }

  // Claim form (Seguradora)
  const claimForm = $('#claim-form');
  if (claimForm) {
    claimForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await api('/api/claims', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams(new FormData(e.currentTarget))
        });
        e.currentTarget.reset();
        setDatesOnForm(e.currentTarget);
        $('#claim-modal').close();
        await refresh();
        toast('Sinistro aberto e encaminhado para análise.');
      } catch (error) { toast(error.message); }
    });
  }

  // Claim form (Assegurado)
  const claimFormClient = $('#claim-form-client');
  if (claimFormClient) {
    claimFormClient.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await api('/api/claims', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams(new FormData(e.currentTarget))
        });
        e.currentTarget.reset();
        setDatesOnForm(e.currentTarget);
        const modal = $('#claim-modal-client');
        if (modal) modal.close();
        await refresh();
        toast('Sinistro aberto e encaminhado para análise.');
      } catch (error) { toast(error.message); }
    });
  }
}

function setDatesOnForm(form) {
  const today = new Date().toISOString().slice(0, 10);
  form.querySelectorAll('input[type=date]').forEach(input => {
    if (!input.value) input.value = today;
  });
}

// ===== Initialize =====
document.addEventListener('DOMContentLoaded', () => {
  setupModals();
  setupMobileMenu();
  setupNav();
  setupScrollControls();
  setupForms();
  if (!isAssegurado) setupDifficultySlider();

  // Set default dates
  const today = new Date().toISOString().slice(0, 10);
  $$('input[type=date]').forEach(input => input.value = today);

  // Initial load
  refresh();
});
