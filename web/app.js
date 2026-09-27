/* ===== SeguroChain — Shared Frontend Logic ===== */
/* Used by both index.html (Seguradora) and assegurado.html (Assegurado) */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);
let state = { policies: [], claims: [] };
let ledger = { valid: true, blocks: [] };
const isAssegurado = document.body.classList.contains('assegurado-page');
const POLL_MS = 4000;

// ===== Utilities =====
const money = (v) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(v) || 0);
const dateStr = (v) => { try { return new Date(`${v}T12:00:00`).toLocaleDateString('pt-BR'); } catch { return v; } };
const h = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const short = (hash) => (hash && hash.length > 20 ? `${hash.slice(0, 20)}…` : hash || '');
const api = async (url, opts = {}) => {
  const r = await fetch(url, opts);
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(d.error || 'Não foi possível concluir a operação.');
  return d;
};
const postForm = (url, body) => api(url, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body
});

let toastTimer;
function toast(msg, kind = '') {
  const el = $('#toast');
  if (!el) return;
  el.textContent = msg;
  el.className = `show ${kind}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3500);
}

function badge(status) {
  return `<span class="badge ${h(status)}">${h(status.replace(/_/g, ' '))}</span>`;
}

function parseBlock(b) {
  const sep = b.data.indexOf(' | ');
  if (sep < 0) return { type: null, data: null };
  const type = b.data.slice(0, sep);
  try { return { type, data: JSON.parse(b.data.slice(sep + 3)) }; } catch { return { type, data: null }; }
}

// ===== Modal Handling (fixes cancel/close bugs) =====
function setupModals() {
  $$('.modal-close-btn, .modal-cancel-btn').forEach(btn => {
    btn.addEventListener('click', () => btn.closest('dialog')?.close());
  });

  // Close on backdrop click
  $$('dialog').forEach(dialog => {
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });
  });

  $$('[data-open]').forEach(button => {
    button.addEventListener('click', () => {
      const targetId = button.dataset.open;
      if (targetId === 'claim-modal' || targetId === 'claim-modal-client') openClaimModal(targetId);
      else $(`#${targetId}`)?.showModal();
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
    overlay?.classList.toggle('show');
  });
  overlay?.addEventListener('click', () => {
    sidebar.classList.remove('open');
    overlay.classList.remove('show');
  });
}

// ===== Smooth nav highlighting =====
function setupNav() {
  $$('[data-nav]').forEach(a => {
    a.addEventListener('click', () => {
      $$('[data-nav]').forEach(n => n.classList.remove('active'));
      a.classList.add('active');
      // Close mobile menu
      $('#sidebar')?.classList.remove('open');
      $('#sidebar-overlay')?.classList.remove('show');
    });
  });
}

// ===== Scroll Controls =====
function setupScrollControls() {
  $$('.scroll-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const panel = $(`#${btn.dataset.target}`);
      if (!panel) return;
      panel.scrollBy({ top: btn.dataset.dir === 'down' ? 200 : -200, behavior: 'smooth' });
    });
  });
}

function updateScrollVisibility(panelId, scrollId) {
  const panel = $(`#${panelId}`);
  const controls = $(`#${scrollId}`);
  if (!panel || !controls) return;
  // Show scroll controls if content overflows
  setTimeout(() => {
    controls.style.display = panel.scrollHeight > panel.clientHeight + 10 ? 'flex' : 'none';
  }, 100);
}

const MONEY_MAX_DIGITS = 11;
const moneyDigits = (text) => String(text || '').replace(/\D/g, '').replace(/^0+(?=\d)/, '').slice(0, MONEY_MAX_DIGITS);
const moneyValue = (input) => {
  const d = moneyDigits(input?.value);
  return d ? (Number(d) / 100).toFixed(2) : '';
};
const moneyNumber = (input) => Number(moneyValue(input)) || 0;

function formatMoneyInput(input) {
  const d = moneyDigits(input.value);
  input.value = d ? money(Number(d) / 100) : '';
}

function formatPlateInput(input) {
  const raw = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 7);
  const oldPattern = raw.length > 4 && /^[A-Z]{3}\d{2}/.test(raw) && !/^[A-Z]{3}\d[A-Z]/.test(raw);
  input.value = oldPattern ? `${raw.slice(0, 3)}-${raw.slice(3)}` : raw;
}

const PLATE_RE = /^[A-Z]{3}-?\d[A-Z0-9]\d{2}$/;

function setupMasks() {
  $$('input[data-money]').forEach(input => {
    input.addEventListener('input', () => formatMoneyInput(input));
    input.addEventListener('focus', () => setTimeout(() => input.setSelectionRange(input.value.length, input.value.length)));
    formatMoneyInput(input);
  });
  $$('input[data-digits]').forEach(input => {
    input.addEventListener('input', () => { input.value = input.value.replace(/\D/g, '').slice(0, Number(input.dataset.digits) || 12); });
  });
  $$('input[data-plate]').forEach(input => input.addEventListener('input', () => formatPlateInput(input)));
}

const claimIds = (modalId) => modalId === 'claim-modal-client'
  ? { modal: '#claim-modal-client', select: '#c-claim-policy', info: '#c-claim-policy-info', form: '#claim-form-client' }
  : { modal: '#claim-modal', select: '#claim-policy', info: '#claim-policy-info', form: '#claim-form' };

function openClaimModal(modalId, policyId) {
  const active = state.policies.filter(p => p.status === 'ATIVA');
  if (!active.length) {
    toast('Emita uma apólice ativa antes de abrir um sinistro.');
    return false;
  }
  const ids = claimIds(modalId);
  const sel = $(ids.select);
  sel.innerHTML = active
    .map(p => `<option value="${h(p.id)}">#${h(p.id)} · ${h(p.client)} · ${h(p.vehicle)}</option>`)
    .join('');
  if (policyId) sel.value = policyId;
  updateClaimInfo(modalId);
  $(ids.modal).showModal();
  return true;
}

function updateClaimInfo(modalId) {
  const ids = claimIds(modalId);
  const form = $(ids.form);
  const info = $(ids.info);
  if (!form || !info) return;
  const p = state.policies.find(x => x.id === form.policy.value);
  if (!p) { info.innerHTML = ''; return; }

  const amount = moneyNumber(form.amount);
  const totalLossPct = state.rules?.totalLossPct ?? 75;
  const isTheft = form.type?.value === 'ROUBO';
  const isTotalLoss = amount >= p.coverageValue * totalLossPct / 100;
  const franchise = isTheft || isTotalLoss ? 0 : p.franchise;
  const indemnity = amount - franchise;
  let estimate = '';
  if (amount > 0) {
    const reason = isTheft ? 'roubo: sem franquia'
      : isTotalLoss ? `perda total (≥ ${totalLossPct}% do valor segurado): sem franquia`
      : `${money(amount)} − ${money(franchise)} de franquia`;
    if (amount <= franchise) estimate = `<p class="warn">Dano parcial abaixo da franquia (${money(franchise)}): o valor fica por conta do segurado e não haverá indenização.</p>`;
    else if (indemnity > p.available) estimate = `<p class="warn">A indenização estimada (${money(indemnity)}) excede o saldo da apólice e será rejeitada.</p>`;
    else estimate = `<p>Indenização estimada: <strong>${money(indemnity)}</strong> (${reason})</p>`;
  }
  const covered = form.type && !p.covers.includes(form.type.value)
    ? `<p class="warn">Esta apólice não cobre ${h(form.type.value)}.</p>` : '';

  info.innerHTML = `
    <div class="claim-info-grid">
      <div><span>Franquia</span><strong>${money(p.franchise)}</strong></div>
      <div><span>Saldo disponível</span><strong>${money(p.available)}</strong></div>
      <div><span>Valor segurado</span><strong>${money(p.coverageValue)}</strong></div>
    </div>${covered}${estimate}`;
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

  slider.addEventListener('input', () => { display.textContent = slider.value; });
  slider.addEventListener('change', async () => {
    try {
      await postForm('/api/difficulty', `difficulty=${slider.value}`);
      toast(`Dificuldade alterada para ${slider.value}.`, 'ok');
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

// ===== Data Refresh =====
let refreshing = null;
let serverDown = false;
function refresh() {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      const [s, l] = await Promise.all([api('/api/state'), api('/api/ledger')]);
      state = s;
      ledger = l;
      render();
      renderLedger();
      if (serverDown) toast('Conexão com o servidor restabelecida.', 'ok');
      serverDown = false;
    } catch (error) {
      if (!serverDown) toast(`Servidor indisponível: ${error.message}`, 'error');
      serverDown = true;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

function startAutoRefresh() {
  setInterval(() => { if (!document.hidden) refresh(); }, POLL_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
}

// ===== SEGURADORA RENDER =====
function renderSeguradora() {
  const { policies, claims } = state;

  $('#metric-policies').textContent = policies.filter(p => p.status === 'ATIVA').length;
  $('#metric-pending').textContent = claims.filter(c => c.status === 'EM_ANALISE').length;
  $('#metric-paid').textContent = money(claims.filter(c => c.status === 'PAGO').reduce((s, c) => s + c.indemnity, 0));

  $('#policies-empty').hidden = policies.length > 0;
  $('#policies-body').innerHTML = policies.map(p => `
    <tr>
      <td><strong>#${h(p.id)}</strong><small>${h(p.wallet)}</small></td>
      <td><strong>${h(p.client)}</strong><small>${h(p.vehicle)} · ${h(p.plate)}</small></td>
      <td class="cover">${p.covers.map(h).join(' · ')}</td>
      <td>${dateStr(p.start)}<small>até ${dateStr(p.end)}</small></td>
      <td><strong>${money(p.available)}</strong><small>de ${money(p.coverageValue)} · franquia ${money(p.franchise)}</small></td>
      <td>${badge(p.status)}</td>
      <td><button class="button secondary table-action" data-claim-for="${h(p.id)}"><i class="ri-alarm-warning-line"></i> Abrir sinistro</button></td>
    </tr>`).join('');

  $('#claims-empty').hidden = claims.length > 0;
  $('#claims-body').innerHTML = claims.map(c => `
    <tr>
      <td><strong>#${h(c.id)}</strong><small>${h(c.client)}</small></td>
      <td>#${h(c.policyId)}</td>
      <td><strong>${h(c.type)}</strong><small>${dateStr(c.date)}</small></td>
      <td>${money(c.amount)}${c.status === 'APROVADO' || c.status === 'PAGO' ? `<small>indenização ${money(c.indemnity)}</small>` : ''}</td>
      <td>${badge(c.status)}</td>
      <td>${c.status === 'EM_ANALISE'
        ? `<button class="button primary table-action" data-action="analyse" data-id="${h(c.id)}"><i class="ri-cpu-line"></i> Analisar</button>`
        : c.status === 'APROVADO'
          ? `<button class="button primary table-action" data-action="pay" data-id="${h(c.id)}"><i class="ri-money-dollar-circle-line"></i> Pagar</button>`
          : `<button class="button secondary table-action" data-action="details" data-id="${h(c.id)}"><i class="ri-eye-line"></i> Ver</button>`
      }</td>
    </tr>`).join('');

  const pending = claims.find(c => c.status === 'EM_ANALISE');
  $('#action-title').textContent = pending ? `Sinistro #${pending.id} aguarda análise` : 'Nenhuma pendência';
  $('#action-description').textContent = pending
    ? 'Execute o Smart Contract Java para aplicar as regras da apólice.'
    : 'Abra um sinistro para iniciar uma análise automatizada.';
  $('#action-link').textContent = pending ? 'Analisar agora' : 'Ver sinistros';
  $('#action-link').onclick = pending ? (e) => { e.preventDefault(); analyse(pending.id); } : null;

  updateScrollVisibility('policies-panel', 'policies-scroll');
  updateScrollVisibility('claims-panel', 'claims-scroll');
}

// ===== ASSEGURADO RENDER =====
function renderAssegurado() {
  const { policies, claims } = state;

  $('#c-metric-policies').textContent = policies.filter(p => p.status === 'ATIVA').length;
  $('#c-metric-pending').textContent = claims.filter(c => c.status === 'EM_ANALISE').length;
  $('#c-metric-resolved').textContent = claims.filter(c => c.status !== 'EM_ANALISE').length;

  $('#c-policies-empty').hidden = policies.length > 0;
  $('#c-policies-body').innerHTML = policies.map(p => `
    <tr>
      <td><strong>#${h(p.id)}</strong></td>
      <td><strong>${h(p.vehicle)}</strong><small>${h(p.plate)}</small></td>
      <td class="cover">${p.covers.map(h).join(' · ')}</td>
      <td>${dateStr(p.start)}<small>até ${dateStr(p.end)}</small></td>
      <td>${money(p.coverageValue)}<small>saldo ${money(p.available)}</small></td>
      <td>${money(p.franchise)}</td>
      <td>${badge(p.status)}</td>
    </tr>`).join('');

  $('#c-claims-empty').hidden = claims.length > 0;
  $('#c-claims-body').innerHTML = claims.map(c => `
    <tr>
      <td><strong>#${h(c.id)}</strong></td>
      <td>#${h(c.policyId)}</td>
      <td>${h(c.type)}</td>
      <td>${dateStr(c.date)}</td>
      <td>${money(c.amount)}${c.status === 'APROVADO' || c.status === 'PAGO' ? `<small>você recebe ${money(c.indemnity)}</small>` : ''}</td>
      <td>${badge(c.status)}</td>
      <td>${c.status !== 'EM_ANALISE'
        ? `<button class="button secondary table-action" data-action="details" data-id="${h(c.id)}"><i class="ri-eye-line"></i> Ver decisão</button>`
        : '<span class="muted" style="font-size:12px">Aguardando...</span>'
      }</td>
    </tr>`).join('');

  updateScrollVisibility('c-policies-panel', 'c-policies-scroll');
  updateScrollVisibility('c-claims-panel', 'c-claims-scroll');
}

function render() {
  if (state.rules) $$('[data-max-franchise]').forEach(el => { el.textContent = state.rules.maxFranchisePct; });
  if (isAssegurado) renderAssegurado();
  else renderSeguradora();
}

// ===== Ledger / Blockchain Render =====
function describeBlock(b) {
  if (b.index === 0) return { title: 'Bloco gênesis', desc: 'Origem da cadeia local' };
  const { type, data: d } = parseBlock(b);
  const title = (type || 'Bloco').replace(/_/g, ' ');
  if (!d) return { title, desc: h(b.data) };

  switch (type) {
    case 'APOLICE_EMITIDA':
      return { title, desc: `Apólice #${h(d.idApolice)} · ${h(d.cliente)} · ${h(d.modelo)} (${h(d.placa)}) · segurado ${money(d.valorSegurado)} · franquia ${money(d.franquia)}` };
    case 'SINISTRO_ABERTO':
      return { title, desc: `Sinistro #${h(d.idSinistro)} · apólice #${h(d.idApolice)} · ${h(d.tipo)} · ${money(d.valorSolicitado)} em ${dateStr(d.data)}` };
    case 'ANALISE_SINISTRO':
      return { title, desc: `Sinistro #${h(d.idSinistro)} · ${d.aprovado ? 'APROVADO' : 'REJEITADO'}${d.aprovado ? ` · indenização ${money(d.valorIndenizacao)}` : ''} — ${h(d.motivo)}` };
    case 'PAGAMENTO_SINISTRO':
      return { title, desc: `Sinistro #${h(d.idSinistro)} · ${money(d.valorPago)} · ${h(d.walletOrigem)} → ${h(d.walletDestino)}` };
    default:
      return { title, desc: h(b.data) };
  }
}

function renderLedger() {
  const invalid = ledger.blocks.filter(b => !b.valid);
  const chainState = $(isAssegurado ? '#c-chain-state' : '#chain-state');
  if (chainState) {
    chainState.classList.toggle('invalid', !ledger.valid);
    chainState.innerHTML = ledger.valid
      ? '<span class="dot"></span> Cadeia íntegra'
      : `<i class="ri-error-warning-fill"></i> Cadeia inválida (${invalid.length} bloco${invalid.length > 1 ? 's' : ''})`;
  }
  const repairBtn = $('#repair-chain');
  if (repairBtn) repairBtn.hidden = ledger.valid;

  const container = $(isAssegurado ? '#c-ledger' : '#ledger');
  if (!container) return;

  if (!ledger.blocks.length) {
    container.innerHTML = '<div class="empty">Nenhum bloco na cadeia.</div>';
    return;
  }

  container.innerHTML = ledger.blocks.slice().reverse().map(b => {
    const isGenesis = b.index === 0;
    const { title, desc } = describeBlock(b);
    const prevHash = b.previousHash === '0' ? '0 (gênesis)' : short(b.previousHash);

    return `<article class="event ${b.valid ? '' : 'invalid'}">
      <div class="event-icon ${isGenesis ? 'genesis' : ''}">${isGenesis ? '<i class="ri-shield-keyhole-fill"></i>' : '#' + b.index}</div>
      <div>
        <strong>${h(title)}</strong>
        <p>${desc}</p>
        ${b.valid ? '' : `<p class="block-error"><i class="ri-error-warning-line"></i> ${h(b.error)}</p>`}
        <div class="event-meta">
          <span title="Hash anterior">⬅ ${h(prevHash)}</span>
          <span title="Nonce (Proof of Work)">⛏ Nonce: ${b.nonce}</span>
          <span title="Timestamp">🕐 ${new Date(b.timestamp).toLocaleString('pt-BR')}</span>
        </div>
      </div>
      <span class="hash" title="${h(b.hash)}">${h(short(b.hash))}</span>
    </article>`;
  }).join('');

  updateScrollVisibility(isAssegurado ? 'c-ledger-panel' : 'ledger-panel', isAssegurado ? 'c-ledger-scroll' : 'ledger-scroll');
}

const busy = new Set();
async function runOnce(key, fn) {
  if (busy.has(key)) return;
  busy.add(key);
  document.body.classList.add('is-busy');
  try { await fn(); } finally {
    busy.delete(key);
    if (!busy.size) document.body.classList.remove('is-busy');
  }
}

function analyse(id) {
  return runOnce(`analyse-${id}`, async () => {
    try {
      await api(`/api/claims/${encodeURIComponent(id)}/analyse`, { method: 'POST' });
      await refresh();
      const claim = state.claims.find(c => c.id === id);
      if (claim) showAnalysis(claim);
      toast(claim?.status === 'REJEITADO'
        ? 'Sinistro rejeitado pelo Smart Contract. Decisão registrada na blockchain.'
        : 'Sinistro aprovado pelo Smart Contract. Decisão registrada na blockchain.', 'ok');
    } catch (error) { toast(error.message, 'error'); }
  });
}

function pay(id) {
  return runOnce(`pay-${id}`, async () => {
    try {
      await api(`/api/claims/${encodeURIComponent(id)}/pay`, { method: 'POST' });
      await refresh();
      // Close analysis modal if open
      const modal = $('#analysis-modal');
      if (modal?.open) modal.close();
      toast('Pagamento registrado em um novo bloco.', 'ok');
    } catch (error) { toast(error.message, 'error'); }
  });
}

function repairChain() {
  const invalid = ledger.blocks.filter(b => !b.valid).length;
  const difficulty = ledger.difficulty;
  const ok = confirm(
    `A cadeia tem ${invalid} bloco(s) inválido(s).\n\n` +
    `Reparar vai reencadear e reminerar (dificuldade ${difficulty}) o primeiro bloco inválido e todos os seguintes, ` +
    `mantendo os dados de cada bloco. Os hashes desses blocos vão mudar.\n\nContinuar?`);
  if (!ok) return;

  return runOnce('repair', async () => {
    try {
      const r = await api('/api/ledger/repair', { method: 'POST' });
      await refresh();
      toast(`Cadeia reparada: ${r.repaired} bloco(s) reminerado(s).`, 'ok');
    } catch (error) { toast(error.message, 'error'); }
  });
}

function claimHistory(claim) {
  return ledger.blocks
    .map(b => ({ b, ...parseBlock(b) }))
    .filter(({ type, data }) => data && (data.idSinistro === claim.id || (type === 'APOLICE_EMITIDA' && data.idApolice === claim.policyId)));
}

function showAnalysis(claim) {
  const content = $(isAssegurado ? '#c-analysis-content' : '#analysis-content');
  const modal = $(isAssegurado ? '#c-analysis-modal' : '#analysis-modal');
  if (!content || !modal) return;

  const decided = claim.status !== 'EM_ANALISE';
  const approved = claim.status === 'APROVADO' || claim.status === 'PAGO';
  const rules = claim.rules.length
    ? claim.rules.map(r => `<li class="${r.startsWith('✗') ? 'fail' : 'pass'}">${h(r)}</li>`).join('')
    : '<li>Regras ainda não executadas.</li>';
  const history = claimHistory(claim).map(({ b, type }) => `
    <li class="${b.valid ? '' : 'invalid'}">
      <span class="history-index">#${b.index}</span>
      <span>${h(type.replace(/_/g, ' '))}<small>${new Date(b.timestamp).toLocaleString('pt-BR')}</small></span>
      <code title="${h(b.hash)}">${h(short(b.hash))}</code>
    </li>`).join('');

  content.innerHTML = `
    <div class="modal-head">
      <div>
        <p class="eyebrow">DECISÃO DO SMART CONTRACT</p>
        <h2>Sinistro #${h(claim.id)}</h2>
      </div>
      <button type="button" class="icon-button" data-close aria-label="Fechar">×</button>
    </div>
    <div class="analysis-result ${!decided ? 'pending' : approved ? 'ok' : 'no'}">
      <strong>${!decided ? 'EM ANÁLISE' : approved ? `✓ ${claim.status}` : '✗ REJEITADO'}</strong>
      <p>${h(claim.reason || 'Aguardando execução das regras.')}</p>
    </div>
    ${approved ? `
    <table class="breakdown">
      <tr><td>Valor solicitado</td><td>${money(claim.amount)}</td></tr>
      <tr><td>(−) Franquia</td><td>${claim.franchise > 0 ? money(claim.franchise) : 'não se aplica'}</td></tr>
      <tr class="total"><td>Indenização${claim.status === 'PAGO' ? ' paga' : ''}</td><td>${money(claim.indemnity)}</td></tr>
    </table>` : ''}
    <h3>Regras executadas no servidor</h3>
    <ul class="rules">${rules}</ul>
    <h3>Histórico na blockchain</h3>
    <ul class="claim-history">${history || '<li>Nenhum bloco encontrado para este sinistro.</li>'}</ul>
    <div class="modal-actions">
      <button type="button" class="button secondary" data-close>Fechar</button>
      ${!isAssegurado && claim.status === 'APROVADO' ? `<button type="button" class="button primary" data-action="pay" data-id="${h(claim.id)}"><i class="ri-money-dollar-circle-line"></i> Registrar pagamento de ${money(claim.indemnity)}</button>` : ''}
    </div>`;
  if (!modal.open) modal.showModal();
}

function setupDelegatedActions() {
  document.addEventListener('click', (e) => {
    const closer = e.target.closest('[data-close]');
    if (closer) { closer.closest('dialog')?.close(); return; }

    const claimFor = e.target.closest('[data-claim-for]');
    if (claimFor) { openClaimModal('claim-modal', claimFor.dataset.claimFor); return; }

    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    const id = btn.dataset.id;
    if (btn.dataset.action === 'analyse') analyse(id);
    else if (btn.dataset.action === 'pay') pay(id);
    else if (btn.dataset.action === 'details') {
      const claim = state.claims.find(c => c.id === id);
      if (claim) showAnalysis(claim);
    }
  });

  $('#repair-chain')?.addEventListener('click', repairChain);
}

// ===== Form Handling =====
function setupForms() {
  // Policy form (Seguradora only)
  const policyForm = $('#policy-form');
  if (policyForm) {
    policyForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const form = e.currentTarget;
      const coverage = moneyNumber(form.coverageValue);
      const maxPct = state.rules?.maxFranchisePct ?? 20;
      const year = Number(form.year.value);
      const maxYear = new Date().getFullYear() + 1;
      if (!PLATE_RE.test(form.plate.value)) return toast('Placa inválida: use ABC-1234 ou ABC1D23.', 'error');
      if (!/^\d{4}$/.test(form.year.value) || year < 1950 || year > maxYear) return toast(`Ano inválido: use 4 dígitos entre 1950 e ${maxYear}.`, 'error');
      if (coverage <= 0) return toast('Informe o valor segurado.', 'error');
      if (moneyNumber(form.franchise) > coverage * maxPct / 100) return toast(`A franquia pode ser no máximo ${maxPct}% do valor segurado (${money(coverage * maxPct / 100)}).`, 'error');
      if (form.end.value < form.start.value) return toast('A data final deve ser posterior à inicial.', 'error');
      const body = new URLSearchParams(new FormData(form));
      body.set('coverageValue', moneyValue(form.coverageValue));
      body.set('franchise', moneyValue(form.franchise) || '0.00');
      // Manually add checkbox states
      $$('#policy-form input[name="cover"]').forEach(input => body.set(`cover_${input.value}`, input.checked));
      await runOnce('policy', async () => {
        try {
          const r = await postForm('/api/policies', body);
          form.reset();
          setDatesOnForm(form);
          $('#policy-modal').close();
          await refresh();
          toast(`Apólice #${r.id} emitida e registrada na blockchain.`, 'ok');
        } catch (error) { toast(error.message, 'error'); }
      });
    });
  }

  ['claim-modal', 'claim-modal-client'].forEach(modalId => {
    const ids = claimIds(modalId);
    const form = $(ids.form);
    if (!form) return;

    form.addEventListener('input', () => updateClaimInfo(modalId));
    form.addEventListener('change', () => updateClaimInfo(modalId));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (moneyNumber(form.amount) <= 0) return toast('Informe o valor do prejuízo.', 'error');
      const body = new URLSearchParams(new FormData(form));
      body.set('amount', moneyValue(form.amount));
      await runOnce(`claim-${modalId}`, async () => {
        try {
          const r = await postForm('/api/claims', body);
          form.reset();
          setDatesOnForm(form);
          $(ids.modal).close();
          await refresh();
          toast(`Sinistro #${r.id} aberto e encaminhado para análise.`, 'ok');
        } catch (error) { toast(error.message, 'error'); }
      });
    });
  });
}

function setDatesOnForm(form) {
  const today = new Date().toISOString().slice(0, 10);
  form.querySelectorAll('input[type=date]').forEach(input => { if (!input.value) input.value = today; });
}

// ===== Initialize =====
document.addEventListener('DOMContentLoaded', () => {
  setupModals();
  setupMobileMenu();
  setupNav();
  setupScrollControls();
  setupMasks();
  setupForms();
  setupDelegatedActions();
  if (!isAssegurado) setupDifficultySlider();

  // Set default dates
  $$('form').forEach(setDatesOnForm);

  // Initial load
  refresh();
  startAutoRefresh();
});
