/* GM Elcons Catalog - interface (HTML/CSS/JS puro, sem dependências). */
(function () {
  'use strict';

  // ------------------------------------------------------------ utilidades
  const $ = (sel, root) => (root || document).querySelector(sel);
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDateTime = (iso) => iso ? new Date(iso).toLocaleString('pt-BR') : '-';
  const fmtDate = (d) => d ? d.split('-').reverse().join('/') : '-';
  const safeUrl = (u) => /^https?:\/\/\S+$/i.test(u || '') ? u : null;

  const STATUS = {
    INCOMPLETO: ['bad', 'Incompleto'],
    AGUARDANDO_REVISAO: ['warn', 'Aguardando revisão'],
    PRONTO_PARA_ANUNCIO: ['ok', 'Pronto para anúncio']
  };
  const SOURCE = {
    FABRICANTE: ['ok', 'Fabricante (oficial)'],
    FORNECEDOR: ['info', 'Fornecedor'],
    ANUNCIO_CONCORRENTE: ['warn', 'Anúncio de concorrente'],
    OUTRA: ['neutral', 'Outra referência']
  };
  const OPS = {
    LOGIN: 'Login', LOGIN_FAILED: 'Falha de login', LOGOUT: 'Logout',
    PRODUCT_CREATE: 'Produto cadastrado', PRODUCT_UPDATE: 'Produto alterado', PRODUCT_DELETE: 'Produto excluído',
    PRODUCT_DUPLICATE: 'Produto duplicado', ATTRIBUTE_UPDATE: 'Atributos alterados', STATUS_CHANGE: 'Status alterado',
    SOURCE_CREATE: 'Fonte cadastrada', SOURCE_UPDATE: 'Fonte alterada', SOURCE_DELETE: 'Fonte excluída',
    LISTING_CREATE: 'Anúncio criado', LISTING_UPDATE: 'Anúncio alterado',
    USER_CREATE: 'Usuário criado', USER_UPDATE: 'Usuário alterado',
    CATEGORY_CREATE: 'Categoria criada', CATEGORY_UPDATE: 'Categoria alterada', CATEGORY_DELETE: 'Categoria excluída',
    ATTRIBUTE_DEFINITION_CREATE: 'Atributo definido', ATTRIBUTE_DEFINITION_UPDATE: 'Definição alterada',
    ATTRIBUTE_DEFINITION_DELETE: 'Definição excluída'
  };
  const TYPE_LABEL = { TEXT: 'Texto', NUMBER: 'Número', BOOLEAN: 'Sim/Não' };
  const ENTITIES = ['Product', 'InformationSource', 'AdListing', 'AppUser', 'Category', 'AttributeDefinition'];

  const badge = (map, key) => {
    const m = map[key] || ['neutral', key || '-'];
    return '<span class="badge ' + m[0] + '">' + esc(m[1]) + '</span>';
  };

  function csrf() {
    const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return m ? decodeURIComponent(m[1]) : '';
  }

  async function api(method, url, body) {
    const opt = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
    if (method !== 'GET') opt.headers['X-XSRF-TOKEN'] = csrf();
    if (body !== undefined) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    let res;
    try { res = await fetch(url, opt); } catch (e) { throw new Error('Falha de conexão com o servidor.'); }
    if (res.status === 401) { window.location.href = '/login.html'; throw new Error('Sessão expirada.'); }
    if (res.status === 204) return null;
    let data = null;
    try { data = await res.json(); } catch (e) { /* sem corpo */ }
    if (!res.ok) {
      let msg = (data && data.message) || ('Erro ' + res.status);
      if (data && data.fieldErrors) msg += ': ' + Object.values(data.fieldErrors).join('; ');
      const err = new Error(msg);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  const qs = (obj) => {
    const p = new URLSearchParams();
    Object.keys(obj).forEach((k) => { if (obj[k] !== '' && obj[k] != null) p.set(k, obj[k]); });
    const s = p.toString();
    return s ? '?' + s : '';
  };

  function toast(msg, kind) {
    const el = document.createElement('div');
    el.className = 'toast ' + (kind || 'ok');
    el.textContent = msg;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), 4500);
  }

  const view = $('#view');
  const skeleton = () => '<div class="card"><div class="skeleton" style="width:40%"></div><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton" style="width:70%"></div></div>';
  const alertBox = (kind, msg) => '<div class="alert ' + kind + '">' + esc(msg) + '</div>';
  const empty = (icon, text) => '<div class="empty"><div class="big">' + icon + '</div><p>' + esc(text) + '</p></div>';

  // Um único ouvinte de cliques na área principal; cada tela registra seus handlers com bind().
  let currentHandlers = {};
  function bind(handlers) { currentHandlers = handlers; }
  view.addEventListener('click', (ev) => {
    const el = ev.target.closest('[data-action]');
    if (!el || !view.contains(el)) return;
    const fn = currentHandlers[el.dataset.action];
    if (fn) { ev.preventDefault(); fn(el); }
  });

  // ------------------------------------------------------------ formulários e modal
  function fInput(label, name, value, o) {
    o = o || {};
    return '<div class="field' + (o.full ? ' full' : '') + '"><label for="f_' + name + '">' + esc(label) + '</label>' +
      '<input id="f_' + name + '" name="' + name + '" type="' + (o.type || 'text') + '" value="' + esc(value) + '"' +
      (o.required ? ' required' : '') + (o.maxlength ? ' maxlength="' + o.maxlength + '"' : '') +
      (o.minlength ? ' minlength="' + o.minlength + '"' : '') +
      (o.pattern ? ' pattern="' + o.pattern + '"' : '') +
      (o.autocomplete ? ' autocomplete="' + o.autocomplete + '"' : '') + '></div>';
  }
  function fText(label, name, value, o) {
    o = o || {};
    return '<div class="field full"><label for="f_' + name + '">' + esc(label) + '</label><textarea id="f_' + name + '" name="' + name + '"' +
      (o.required ? ' required' : '') + (o.maxlength ? ' maxlength="' + o.maxlength + '"' : '') + '>' + esc(value) + '</textarea></div>';
  }
  function fSelect(label, name, options, selected, o) {
    o = o || {};
    return '<div class="field' + (o.full ? ' full' : '') + '"><label for="f_' + name + '">' + esc(label) + '</label><select id="f_' + name + '" name="' + name + '"' +
      (o.required ? ' required' : '') + '>' +
      options.map((x) => '<option value="' + esc(x[0]) + '"' + (String(x[0]) === String(selected) ? ' selected' : '') + '>' + esc(x[1]) + '</option>').join('') +
      '</select></div>';
  }

  function openModal(cfg) {
    const dlg = $('#modal');
    dlg.innerHTML = '<form class="modal-form"><header><h3>' + esc(cfg.title) + '</h3>' +
      '<button type="button" class="icon-btn" data-close aria-label="Fechar">×</button></header>' +
      '<div class="modal-body">' + cfg.body + '<div class="form-error" role="alert"></div></div>' +
      '<footer><button type="button" class="btn" data-close>Cancelar</button>' +
      '<button type="submit" class="btn ' + (cfg.danger ? 'danger' : 'primary') + '">' + esc(cfg.submit || 'Salvar') + '</button></footer></form>';
    const form = $('form', dlg);
    dlg.querySelectorAll('[data-close]').forEach((b) => b.addEventListener('click', () => dlg.close()));
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const errBox = $('.form-error', form);
      const btn = $('button[type=submit]', form);
      errBox.textContent = '';
      btn.disabled = true;
      try {
        await cfg.onSubmit(new FormData(form), form);
        dlg.close();
      } catch (e) {
        errBox.textContent = e.message;
        btn.disabled = false;
      }
    });
    dlg.showModal();
  }

  const pager = (p) => p.totalPages <= 1 ? '' :
    '<div class="pager"><button class="btn sm" data-action="prev"' + (p.page <= 0 ? ' disabled' : '') + '>Anterior</button>' +
    '<span class="muted small">Página ' + (p.page + 1) + ' de ' + p.totalPages + ' (' + p.totalElements + ' registros)</span>' +
    '<button class="btn sm" data-action="next"' + (p.page + 1 >= p.totalPages ? ' disabled' : '') + '>Próxima</button></div>';

  function pageHead(title, sub, actionsHtml) {
    return '<div class="page-head"><div><h1>' + esc(title) + '</h1>' + (sub ? '<p>' + esc(sub) + '</p>' : '') + '</div>' +
      '<div class="actions">' + (actionsHtml || '') + '</div></div>';
  }

  let me = null;
  const isAdmin = () => me && me.role === 'ADMIN';
  const adminOnly = () => { view.innerHTML = alertBox('warn', 'Esta área é restrita a administradores.'); };

  // ------------------------------------------------------------ dashboard
  async function vDashboard() {
    view.innerHTML = skeleton();
    const d = await api('GET', '/api/dashboard');
    const listings = d.recentListings.length ? '<div class="table-wrap"><table class="tbl"><thead><tr><th>Anúncio</th><th>Produto</th><th>Atualizado</th></tr></thead><tbody>' +
      d.recentListings.map((l) => '<tr><td>' + esc(l.title) + '</td><td><a href="#/products/' + l.productId + '">' + esc(l.productSku) + '</a></td><td>' + fmtDateTime(l.updatedAt) + '</td></tr>').join('') +
      '</tbody></table></div>' : empty('📝', 'Nenhum anúncio preparado ainda.');
    const acts = d.recentActivity.length ? '<div class="table-wrap"><table class="tbl"><tbody>' +
      d.recentActivity.map((a) => '<tr><td class="small muted">' + fmtDateTime(a.occurredAt) + '</td><td>' + esc(a.username) + '</td><td>' + esc(OPS[a.operation] || a.operation) + '</td></tr>').join('') +
      '</tbody></table></div>' : empty('🕑', 'Sem atividades registradas.');
    view.innerHTML = pageHead('Dashboard', 'Visão geral do cadastro técnico', '<a class="btn primary" href="#/products">Ver produtos</a>') +
      '<div class="stats">' +
      '<div class="stat s-info"><div class="n">' + d.total + '</div><div class="l">Produtos cadastrados</div></div>' +
      '<div class="stat s-bad"><div class="n">' + d.incompleto + '</div><div class="l">Incompletos</div></div>' +
      '<div class="stat s-warn"><div class="n">' + d.aguardandoRevisao + '</div><div class="l">Aguardando revisão</div></div>' +
      '<div class="stat s-ok"><div class="n">' + d.pronto + '</div><div class="l">Prontos para anúncio</div></div></div>' +
      '<div class="grid2"><div class="card"><h2>Anúncios preparados recentemente</h2>' + listings + '</div>' +
      '<div class="card"><h2>Atividades recentes</h2>' + acts + '</div></div>';
  }

  // ------------------------------------------------------------ produtos (lista)
  const pf = { q: '', brand: '', categoryId: '', status: '', page: 0 };

  async function vProducts() {
    view.innerHTML = skeleton();
    const cats = await api('GET', '/api/categories');
    async function load() {
      const box = $('#plist');
      box.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
      try {
        const p = await api('GET', '/api/products' + qs({ q: pf.q, brand: pf.brand, categoryId: pf.categoryId, status: pf.status, page: pf.page, size: 15 }));
        if (!p.content.length) { box.innerHTML = empty('📦', 'Nenhum produto encontrado com esses filtros.'); return; }
        box.innerHTML = '<div class="table-wrap"><table class="tbl"><thead><tr><th>SKU</th><th>Produto</th><th>Marca</th><th>Categoria</th><th>Status</th><th>Responsável</th><th>Atualizado</th></tr></thead><tbody>' +
          p.content.map((x) => '<tr><td class="mono">' + esc(x.sku) + '</td><td><a href="#/products/' + x.id + '">' + esc(x.name) + '</a>' +
            (x.duplicatedFromSku ? '<div class="small muted">duplicado de ' + esc(x.duplicatedFromSku) + '</div>' : '') + '</td><td>' + esc(x.brand || '-') +
            '</td><td>' + esc(x.categoryName) + '</td><td>' + badge(STATUS, x.status) + '</td><td>' + esc(x.createdBy) + '</td><td class="small">' + fmtDateTime(x.updatedAt) + '</td></tr>').join('') +
          '</tbody></table></div>' + pager(p);
      } catch (e) { box.innerHTML = alertBox('bad', e.message); }
    }
    view.innerHTML = pageHead('Produtos', 'Cadastro técnico e status de preparação', '<button class="btn primary" data-action="new">+ Novo produto</button>') +
      '<div class="card"><form class="toolbar" id="pfilter">' +
      '<div class="field"><label for="pq">Nome ou código</label><input id="pq" value="' + esc(pf.q) + '"></div>' +
      '<div class="field"><label for="pb">Marca</label><input id="pb" value="' + esc(pf.brand) + '"></div>' +
      fSelect('Categoria', 'pc', [['', 'Todas']].concat(cats.map((c) => [c.id, c.name])), pf.categoryId) +
      fSelect('Status', 'ps', [['', 'Todos']].concat(Object.keys(STATUS).map((k) => [k, STATUS[k][1]])), pf.status) +
      '<button class="btn" type="submit">Filtrar</button><button class="btn ghost" type="button" data-action="clear">Limpar</button></form>' +
      '<div id="plist"></div></div>';
    $('#pfilter').addEventListener('submit', (ev) => {
      ev.preventDefault();
      pf.q = $('#pq').value.trim(); pf.brand = $('#pb').value.trim();
      pf.categoryId = $('#f_pc').value; pf.status = $('#f_ps').value; pf.page = 0;
      load();
    });
    bind({
      clear: () => { pf.q = ''; pf.brand = ''; pf.categoryId = ''; pf.status = ''; pf.page = 0; vProducts(); },
      prev: () => { pf.page = Math.max(0, pf.page - 1); load(); },
      next: () => { pf.page += 1; load(); },
      new: () => {
        if (!cats.length) { toast('Cadastre uma categoria antes de criar produtos.', 'bad'); return; }
        openModal({
          title: 'Novo produto', submit: 'Cadastrar',
          body: '<div class="form-grid">' + fInput('Nome', 'name', '', { required: true, maxlength: 200, full: true }) +
            fInput('SKU / código interno', 'sku', '', { required: true, maxlength: 60 }) + fInput('Marca', 'brand', '', { maxlength: 100 }) +
            fSelect('Categoria', 'categoryId', cats.map((c) => [c.id, c.name]), '', { required: true, full: true }) +
            fText('Descrição geral / observações', 'description', '', { maxlength: 5000 }) + '</div>',
          onSubmit: async (fd) => {
            const created = await api('POST', '/api/products', {
              name: fd.get('name'), sku: fd.get('sku'), brand: fd.get('brand'),
              categoryId: Number(fd.get('categoryId')), description: fd.get('description')
            });
            toast('Produto cadastrado.');
            window.location.hash = '#/products/' + created.id;
          }
        });
      }
    });
    await load();
  }

  // ------------------------------------------------------------ produto (detalhe)
  async function vProduct(id) {
    view.innerHTML = skeleton();
    const d = await api('GET', '/api/products/' + id);
    const srcLabel = (s) => (SOURCE[s.type] ? SOURCE[s.type][1] : s.type) + ' - ' + s.name;

    const acts = [];
    acts.push('<button class="btn" data-action="edit">Editar</button>');
    acts.push('<button class="btn" data-action="dup">Duplicar</button>');
    if (d.status === 'INCOMPLETO') acts.push('<button class="btn primary" data-action="submit">Enviar para revisão</button>');
    if (d.status === 'AGUARDANDO_REVISAO') acts.push('<button class="btn primary" data-action="approve">Aprovar revisão</button>');
    if (d.status !== 'INCOMPLETO') acts.push('<button class="btn" data-action="reopen">Reabrir edição</button>');
    if (d.status === 'PRONTO_PARA_ANUNCIO') acts.push('<a class="btn primary" href="#/products/' + d.id + '/briefing">Briefing e anúncio</a>');
    if (isAdmin()) acts.push('<button class="btn danger" data-action="delete">Excluir</button>');

    const v = d.validation;
    let valHtml = '';
    if (d.status !== 'PRONTO_PARA_ANUNCIO' && v.blockers.length) {
      valHtml += '<div class="alert bad"><strong>Pendências para avançar:</strong><ul>' + v.blockers.map((b) => '<li>' + esc(b) + '</li>').join('') + '</ul></div>';
    } else if (d.status === 'INCOMPLETO') {
      valHtml += alertBox('ok', 'Sem pendências: o produto já pode ser enviado para revisão.');
    }
    if (v.warnings.length) {
      valHtml += '<div class="alert warn"><strong>Atenção:</strong><ul>' + v.warnings.map((w) => '<li>' + esc(w) + '</li>').join('') + '</ul></div>';
    }
    if (d.duplicatedFromSku) {
      valHtml += alertBox('info', 'Este produto foi criado por duplicação de ' + d.duplicatedFromSku + '. Os atributos copiados exigem revisão (marque "Revisado" após conferir).');
    }

    const attrRows = d.attributes.map((a) => {
      const i = a.definitionId;
      const input = a.dataType === 'BOOLEAN'
        ? '<select class="cell-input" name="v_' + i + '"><option value="">-</option><option value="true"' + (a.value === 'true' ? ' selected' : '') + '>Sim</option><option value="false"' + (a.value === 'false' ? ' selected' : '') + '>Não</option></select>'
        : '<input class="cell-input" name="v_' + i + '" value="' + esc(a.value) + '" maxlength="1000"' + (a.dataType === 'NUMBER' ? ' inputmode="decimal"' : '') + '>';
      const src = '<select class="cell-input" name="s_' + i + '"><option value="">sem fonte</option>' +
        d.sources.map((s) => '<option value="' + s.id + '"' + (a.sourceId === s.id ? ' selected' : '') + '>' + esc(srcLabel(s)) + '</option>').join('') + '</select>';
      return '<tr><td>' + esc(a.name) + (a.required ? ' <span class="muted">*</span>' : '') + (a.unit ? ' <span class="muted small">(' + esc(a.unit) + ')</span>' : '') +
        (a.needsReview ? '<div><span class="badge warn">copiado: revisar</span></div>' : '') + '</td><td>' + input + '</td><td>' + src +
        '</td><td><label class="small"><input type="checkbox" name="r_' + i + '"' + (a.needsReview ? '' : ' checked') + '> Revisado</label></td></tr>';
    }).join('');
    const attrCard = d.attributes.length
      ? '<form id="attr-form"><div class="table-wrap"><table class="tbl"><thead><tr><th>Atributo</th><th>Valor verificado</th><th>Fonte</th><th></th></tr></thead><tbody>' + attrRows + '</tbody></table></div>' +
        '<p class="muted small">Preencha somente informações verificadas. * = obrigatório. Deixe em branco o que não foi confirmado.</p>' +
        '<div class="form-error" id="attr-error" role="alert"></div><button class="btn primary" type="submit">Salvar atributos</button></form>'
      : empty('🧩', 'A categoria deste produto ainda não possui atributos. Um administrador pode defini-los em Categorias.');

    const srcRows = d.sources.length ? '<div class="table-wrap"><table class="tbl"><thead><tr><th>Tipo</th><th>Nome</th><th>URL</th><th>Consulta</th><th>Observações</th><th></th></tr></thead><tbody>' +
      d.sources.map((s) => '<tr><td>' + badge(SOURCE, s.type) + '</td><td>' + esc(s.name) + '</td><td>' +
        (safeUrl(s.url) ? '<a href="' + esc(s.url) + '" target="_blank" rel="noopener noreferrer">abrir</a>' : '-') + '</td><td>' + fmtDate(s.consultedAt) +
        '</td><td class="small">' + esc(s.notes || '') + '</td><td class="right"><button class="btn sm" data-action="src-edit" data-id="' + s.id + '">Editar</button> ' +
        '<button class="btn sm" data-action="src-del" data-id="' + s.id + '">Excluir</button></td></tr>').join('') + '</tbody></table></div>'
      : empty('🔗', 'Nenhuma fonte cadastrada. Registre de onde vieram as informações.');

    view.innerHTML = '<p class="small"><a href="#/products">← Produtos</a></p>' +
      pageHead(d.name, 'SKU ' + d.sku, acts.join('')) +
      '<div class="card"><dl class="kv"><dt>Status</dt><dd>' + badge(STATUS, d.status) + '</dd><dt>Categoria</dt><dd>' + esc(d.categoryName) + '</dd>' +
      '<dt>Marca</dt><dd>' + esc(d.brand || '-') + '</dd><dt>Responsável</dt><dd>' + esc(d.createdBy) + '</dd>' +
      '<dt>Criado em</dt><dd>' + fmtDateTime(d.createdAt) + '</dd><dt>Atualizado em</dt><dd>' + fmtDateTime(d.updatedAt) + '</dd>' +
      (d.reviewedBy ? '<dt>Revisado por</dt><dd>' + esc(d.reviewedBy) + ' em ' + fmtDateTime(d.reviewedAt) + '</dd>' : '') +
      '<dt>Descrição</dt><dd>' + esc(d.description || '-') + '</dd></dl></div>' + valHtml +
      '<div class="card"><h2>Atributos técnicos</h2>' + attrCard + '</div>' +
      '<div class="card"><div class="card-head"><h2>Fontes de informação</h2><button class="btn" data-action="src-add">+ Adicionar fonte</button></div>' + srcRows + '</div>';

    const reload = () => vProduct(id);
    const srcForm = (s) => '<div class="form-grid">' +
      fSelect('Tipo da fonte', 'type', Object.keys(SOURCE).map((k) => [k, SOURCE[k][1]]), s ? s.type : 'FABRICANTE', { required: true }) +
      fInput('Data da consulta', 'consultedAt', s && s.consultedAt || '', { type: 'date' }) +
      fInput('Nome / identificação', 'name', s ? s.name : '', { required: true, maxlength: 200, full: true }) +
      fInput('URL (opcional)', 'url', s && s.url || '', { type: 'url', maxlength: 1000, full: true }) +
      fText('Observações', 'notes', s && s.notes || '', { maxlength: 2000 }) + '</div>';
    const srcBody = (fd) => ({ type: fd.get('type'), name: fd.get('name'), url: fd.get('url'), consultedAt: fd.get('consultedAt') || null, notes: fd.get('notes') });

    const attrForm = $('#attr-form');
    if (attrForm) {
      attrForm.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const errBox = $('#attr-error'); errBox.textContent = '';
        const items = d.attributes.map((a) => {
          const val = attrForm.elements['v_' + a.definitionId].value;
          const s = attrForm.elements['s_' + a.definitionId].value;
          return { definitionId: a.definitionId, value: val, sourceId: s ? Number(s) : null, reviewed: attrForm.elements['r_' + a.definitionId].checked };
        });
        try { await api('PUT', '/api/products/' + id + '/attributes', { items }); toast('Atributos salvos.'); reload(); }
        catch (e) { errBox.textContent = e.message; }
      });
    }

    bind({
      edit: () => openModal({
        title: 'Editar produto', body: '<div class="form-grid">' + fInput('Nome', 'name', d.name, { required: true, maxlength: 200, full: true }) +
          fInput('SKU / código interno', 'sku', d.sku, { required: true, maxlength: 60 }) + fInput('Marca', 'brand', d.brand || '', { maxlength: 100 }) +
          fText('Descrição geral / observações', 'description', d.description || '', { maxlength: 5000 }) + '</div>',
        onSubmit: async (fd) => {
          await api('PUT', '/api/products/' + id, { name: fd.get('name'), sku: fd.get('sku'), brand: fd.get('brand'), description: fd.get('description') });
          toast('Produto atualizado.'); reload();
        }
      }),
      dup: () => openModal({
        title: 'Duplicar produto', submit: 'Duplicar',
        body: alertBox('info', 'O novo produto nascerá como INCOMPLETO e todos os atributos copiados exigirão revisão.') +
          '<div class="form-grid">' + fInput('Novo SKU', 'sku', '', { required: true, maxlength: 60 }) + fInput('Novo nome', 'name', 'Cópia de ' + d.name, { maxlength: 200, full: true }) + '</div>',
        onSubmit: async (fd) => {
          const c = await api('POST', '/api/products/' + id + '/duplicate', { sku: fd.get('sku'), name: fd.get('name') });
          toast('Produto duplicado. Revise os atributos copiados.'); window.location.hash = '#/products/' + c.id;
        }
      }),
      submit: async () => { try { await api('POST', '/api/products/' + id + '/submit-review'); toast('Enviado para revisão.'); reload(); } catch (e) { toast(e.message, 'bad'); } },
      approve: () => openModal({
        title: 'Aprovar revisão', submit: 'Aprovar',
        body: '<p>Ao aprovar, você confirma que conferiu os atributos e as fontes deste produto. O produto ficará PRONTO PARA ANÚNCIO e sua aprovação será registrada.</p>' +
          '<label><input type="checkbox" name="confirm" required> Confirmo que revisei as informações.</label>',
        onSubmit: async (fd) => { await api('POST', '/api/products/' + id + '/approve', { confirmed: fd.get('confirm') === 'on' }); toast('Revisão aprovada.'); reload(); }
      }),
      reopen: async () => { try { await api('POST', '/api/products/' + id + '/reopen'); toast('Produto reaberto para edição.'); reload(); } catch (e) { toast(e.message, 'bad'); } },
      delete: () => openModal({
        title: 'Excluir produto', submit: 'Excluir definitivamente', danger: true,
        body: alertBox('bad', 'Esta ação remove o produto, seus atributos, fontes e anúncio. Ela ficará registrada na auditoria.') +
          fInput('Digite o SKU (' + d.sku + ') para confirmar', 'confirmSku', '', { required: true, full: true }),
        onSubmit: async (fd) => { await api('DELETE', '/api/products/' + id + '?confirmSku=' + encodeURIComponent(fd.get('confirmSku'))); toast('Produto excluído.'); window.location.hash = '#/products'; }
      }),
      'src-add': () => openModal({
        title: 'Nova fonte de informação', submit: 'Adicionar', body: srcForm(null),
        onSubmit: async (fd) => { await api('POST', '/api/products/' + id + '/sources', srcBody(fd)); toast('Fonte adicionada.'); reload(); }
      }),
      'src-edit': (el) => {
        const s = d.sources.find((x) => String(x.id) === el.dataset.id);
        openModal({ title: 'Editar fonte', body: srcForm(s),
          onSubmit: async (fd) => { await api('PUT', '/api/sources/' + s.id, srcBody(fd)); toast('Fonte atualizada.'); reload(); } });
      },
      'src-del': (el) => {
        const s = d.sources.find((x) => String(x.id) === el.dataset.id);
        openModal({ title: 'Excluir fonte', submit: 'Excluir', danger: true,
          body: '<p>Excluir a fonte <strong>' + esc(s.name) + '</strong>? Atributos que a usavam ficarão sem fonte.</p>',
          onSubmit: async () => { await api('DELETE', '/api/sources/' + s.id); toast('Fonte excluída.'); reload(); } });
      }
    });
  }

  // ------------------------------------------------------------ briefing + anúncio
  async function vBriefing(id) {
    view.innerHTML = skeleton();
    const d = await api('GET', '/api/products/' + id);
    let brief = null; let problem = null;
    try { brief = await api('GET', '/api/products/' + id + '/briefing'); } catch (e) { problem = e.message; }
    let listing = null;
    if (d.hasListing) { try { listing = await api('GET', '/api/products/' + id + '/listing'); } catch (e) { /* sem anúncio */ } }

    view.innerHTML = '<p class="small"><a href="#/products/' + id + '">← ' + esc(d.name) + '</a></p>' +
      pageHead('Gerador de briefing', d.name + ' (' + d.sku + ')') +
      (problem ? alertBox('warn', problem) :
        '<div class="card"><div class="card-head"><h2>1. Briefing para o ChatGPT</h2><button class="btn primary" data-action="copy">Copiar briefing</button></div>' +
        '<p class="muted small">Copie o texto abaixo, cole no ChatGPT e peça o título, a descrição e as palavras-chave. Confira tudo antes de usar.</p>' +
        '<textarea class="briefing" id="brief-text" readonly>' + esc(brief.text) + '</textarea></div>' +
        '<div class="card"><h2>2. Resultado final do anúncio</h2>' +
        (listing ? '<p class="muted small">Preparado em ' + fmtDateTime(listing.preparedAt) + ' · última revisão por ' + esc(listing.reviewedBy) + ' em ' + fmtDateTime(listing.updatedAt) + '</p>' : '') +
        '<form id="listing-form"><div class="form-grid">' + fInput('Título do anúncio', 'title', listing ? listing.title : '', { required: true, maxlength: 255, full: true }) +
        fText('Descrição', 'description', listing ? listing.description : '', { required: true, maxlength: 10000 }) +
        fInput('Palavras-chave (separadas por vírgula)', 'keywords', listing ? listing.keywords || '' : '', { maxlength: 1000, full: true }) + '</div>' +
        '<div class="form-error" id="listing-error" role="alert" style="margin-top:10px"></div>' +
        '<button class="btn primary" type="submit" style="margin-top:12px">Salvar anúncio</button></form></div>');

    bind({
      copy: async () => {
        const ta = $('#brief-text');
        try { await navigator.clipboard.writeText(ta.value); } catch (e) { ta.select(); document.execCommand('copy'); }
        toast('Briefing copiado.');
      }
    });
    const lf = $('#listing-form');
    if (lf) {
      lf.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const errBox = $('#listing-error'); errBox.textContent = '';
        try {
          await api('PUT', '/api/products/' + id + '/listing', { title: lf.title.value, description: lf.description.value, keywords: lf.keywords.value });
          toast('Anúncio salvo.'); vBriefing(id);
        } catch (e) { errBox.textContent = e.message; }
      });
    }
  }

  // ------------------------------------------------------------ categorias
  async function vCategories() {
    view.innerHTML = skeleton();
    const cats = await api('GET', '/api/categories');
    const admin = isAdmin();
    const reload = () => vCategories();
    const attrForm = (a) => '<div class="form-grid">' + fInput('Nome do atributo', 'name', a ? a.name : '', { required: true, maxlength: 100, full: true }) +
      fSelect('Tipo de dado', 'dataType', Object.keys(TYPE_LABEL).map((k) => [k, TYPE_LABEL[k]]), a ? a.dataType : 'TEXT', { required: true }) +
      fInput('Unidade (opcional)', 'unit', a && a.unit || '', { maxlength: 30 }) +
      fSelect('Obrigatório?', 'required', [['false', 'Não'], ['true', 'Sim']], a ? String(a.required) : 'false') +
      fInput('Ordem', 'displayOrder', a ? a.displayOrder : '', { type: 'number' }) + '</div>';
    const attrBody = (fd) => ({ name: fd.get('name'), dataType: fd.get('dataType'), required: fd.get('required') === 'true', unit: fd.get('unit'),
      displayOrder: fd.get('displayOrder') === '' ? null : Number(fd.get('displayOrder')) });

    view.innerHTML = pageHead('Categorias e atributos', 'Cada categoria define quais atributos técnicos um produto precisa informar',
      admin ? '<button class="btn primary" data-action="cat-new">+ Nova categoria</button>' : '') +
      (admin ? '' : alertBox('info', 'Somente administradores podem alterar categorias e atributos.')) +
      (cats.length ? cats.map((c) => '<div class="card"><div class="card-head"><div><h2>' + esc(c.name) + '</h2><span class="muted small">' + esc(c.description || '') +
        ' · ' + c.productCount + ' produto(s)</span></div>' +
        (admin ? '<div class="actions"><button class="btn sm" data-action="attr-add" data-id="' + c.id + '">+ Atributo</button> <button class="btn sm" data-action="cat-edit" data-id="' + c.id + '">Editar</button> <button class="btn sm" data-action="cat-del" data-id="' + c.id + '">Excluir</button></div>' : '') + '</div>' +
        (c.attributes.length ? '<div class="table-wrap"><table class="tbl cat-attrs"><thead><tr><th>Atributo</th><th>Tipo</th><th>Unidade</th><th>Obrigatório</th><th></th></tr></thead><tbody>' +
          c.attributes.map((a) => '<tr><td>' + esc(a.name) + '</td><td>' + esc(TYPE_LABEL[a.dataType]) + '</td><td>' + esc(a.unit || '-') + '</td><td>' + (a.required ? 'Sim' : 'Não') + '</td><td class="right">' +
            (admin ? '<button class="btn sm" data-action="attr-edit" data-id="' + a.id + '">Editar</button> <button class="btn sm" data-action="attr-del" data-id="' + a.id + '">Excluir</button>' : '') + '</td></tr>').join('') + '</tbody></table></div>'
          : empty('🧩', 'Sem atributos definidos.')) + '</div>').join('') : empty('🗂️', 'Nenhuma categoria cadastrada.'));

    const findCat = (cid) => cats.find((c) => String(c.id) === cid);
    const findAttr = (aid) => { for (const c of cats) { const a = c.attributes.find((x) => String(x.id) === aid); if (a) return a; } return null; };
    const catForm = (c) => '<div class="form-grid">' + fInput('Nome', 'name', c ? c.name : '', { required: true, maxlength: 100, full: true }) + fText('Descrição', 'description', c && c.description || '', { maxlength: 500 }) + '</div>';

    bind({
      'cat-new': () => openModal({ title: 'Nova categoria', submit: 'Criar', body: catForm(null),
        onSubmit: async (fd) => { await api('POST', '/api/categories', { name: fd.get('name'), description: fd.get('description') }); toast('Categoria criada.'); reload(); } }),
      'cat-edit': (el) => { const c = findCat(el.dataset.id); openModal({ title: 'Editar categoria', body: catForm(c),
        onSubmit: async (fd) => { await api('PUT', '/api/categories/' + c.id, { name: fd.get('name'), description: fd.get('description') }); toast('Categoria atualizada.'); reload(); } }); },
      'cat-del': (el) => { const c = findCat(el.dataset.id); openModal({ title: 'Excluir categoria', submit: 'Excluir', danger: true,
        body: '<p>Excluir <strong>' + esc(c.name) + '</strong> e suas definições de atributos? Só é possível se não houver produtos nela.</p>',
        onSubmit: async () => { await api('DELETE', '/api/categories/' + c.id); toast('Categoria excluída.'); reload(); } }); },
      'attr-add': (el) => { const c = findCat(el.dataset.id); openModal({ title: 'Novo atributo em ' + c.name, submit: 'Adicionar', body: attrForm(null),
        onSubmit: async (fd) => { await api('POST', '/api/categories/' + c.id + '/attributes', attrBody(fd)); toast('Atributo adicionado.'); reload(); } }); },
      'attr-edit': (el) => { const a = findAttr(el.dataset.id); openModal({ title: 'Editar atributo', body: attrForm(a),
        onSubmit: async (fd) => { await api('PUT', '/api/attribute-definitions/' + a.id, attrBody(fd)); toast('Atributo atualizado.'); reload(); } }); },
      'attr-del': (el) => { const a = findAttr(el.dataset.id); openModal({ title: 'Excluir atributo', submit: 'Excluir', danger: true,
        body: '<p>Excluir o atributo <strong>' + esc(a.name) + '</strong>? Só é possível se nenhum produto o tiver preenchido.</p>',
        onSubmit: async () => { await api('DELETE', '/api/attribute-definitions/' + a.id); toast('Atributo excluído.'); reload(); } }); }
    });
  }

  // ------------------------------------------------------------ fontes (visão geral)
  const srcState = { type: '', page: 0 };
  async function vSources() {
    view.innerHTML = pageHead('Fontes de informação', 'Todas as fontes registradas. Para adicionar, abra um produto.') +
      '<div class="card"><form class="toolbar" id="sfilter">' +
      fSelect('Tipo', 'st', [['', 'Todos']].concat(Object.keys(SOURCE).map((k) => [k, SOURCE[k][1]])), srcState.type) +
      '<button class="btn" type="submit">Filtrar</button></form><div id="slist"></div></div>';
    async function load() {
      const box = $('#slist'); box.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div>';
      try {
        const p = await api('GET', '/api/sources' + qs({ type: srcState.type, page: srcState.page, size: 15 }));
        if (!p.content.length) { box.innerHTML = empty('🔗', 'Nenhuma fonte encontrada.'); return; }
        box.innerHTML = '<div class="table-wrap"><table class="tbl"><thead><tr><th>Produto</th><th>Tipo</th><th>Nome</th><th>URL</th><th>Consulta</th><th>Observações</th></tr></thead><tbody>' +
          p.content.map((s) => '<tr><td><a href="#/products/' + s.productId + '">' + esc(s.productSku) + '</a></td><td>' + badge(SOURCE, s.type) + '</td><td>' + esc(s.name) + '</td><td>' +
            (safeUrl(s.url) ? '<a href="' + esc(s.url) + '" target="_blank" rel="noopener noreferrer">abrir</a>' : '-') + '</td><td>' + fmtDate(s.consultedAt) + '</td><td class="small">' + esc(s.notes || '') + '</td></tr>').join('') +
          '</tbody></table></div>' + pager(p);
      } catch (e) { box.innerHTML = alertBox('bad', e.message); }
    }
    $('#sfilter').addEventListener('submit', (ev) => { ev.preventDefault(); srcState.type = $('#f_st').value; srcState.page = 0; load(); });
    bind({ prev: () => { srcState.page = Math.max(0, srcState.page - 1); load(); }, next: () => { srcState.page += 1; load(); } });
    await load();
  }

  // ------------------------------------------------------------ anúncios preparados
  let lstPage = 0;
  async function vListings() {
    view.innerHTML = pageHead('Anúncios preparados', 'Resultados finais salvos a partir do briefing') + '<div class="card" id="llist">' + skeleton() + '</div>';
    async function load() {
      const box = $('#llist');
      try {
        const p = await api('GET', '/api/listings' + qs({ page: lstPage, size: 15 }));
        if (!p.content.length) { box.innerHTML = empty('📝', 'Nenhum anúncio preparado. Aprove um produto e use "Briefing e anúncio".'); return; }
        box.innerHTML = '<div class="table-wrap"><table class="tbl"><thead><tr><th>Título</th><th>Produto</th><th>Palavras-chave</th><th>Preparado</th><th>Revisão</th></tr></thead><tbody>' +
          p.content.map((l) => '<tr><td><a href="#/products/' + l.productId + '/briefing">' + esc(l.title) + '</a></td><td>' + esc(l.productSku) + '</td><td class="small">' + esc(l.keywords || '-') +
            '</td><td class="small">' + fmtDateTime(l.preparedAt) + '</td><td class="small">' + esc(l.reviewedBy) + '<br>' + fmtDateTime(l.updatedAt) + '</td></tr>').join('') + '</tbody></table></div>' + pager(p);
      } catch (e) { box.innerHTML = alertBox('bad', e.message); }
    }
    bind({ prev: () => { lstPage = Math.max(0, lstPage - 1); load(); }, next: () => { lstPage += 1; load(); } });
    await load();
  }

  // ------------------------------------------------------------ auditoria (ADMIN)
  const aud = { username: '', operation: '', entityType: '', page: 0 };
  async function vAudit() {
    if (!isAdmin()) { adminOnly(); return; }
    view.innerHTML = pageHead('Auditoria', 'Registro de operações críticas (acesso restrito a administradores)') +
      alertBox('info', 'Os registros são protegidos por controle de acesso no sistema, mas não são à prova de adulteração: quem tem acesso direto ao banco de dados pode alterá-los.') +
      '<div class="card"><form class="toolbar" id="afilter"><div class="field"><label for="au">Usuário</label><input id="au" value="' + esc(aud.username) + '"></div>' +
      fSelect('Operação', 'ao', [['', 'Todas']].concat(Object.keys(OPS).map((k) => [k, OPS[k]])), aud.operation) +
      fSelect('Entidade', 'ae', [['', 'Todas']].concat(ENTITIES.map((k) => [k, k])), aud.entityType) +
      '<button class="btn" type="submit">Filtrar</button></form><div id="alist"></div></div>';
    const pretty = (s) => { if (!s) return ''; try { return JSON.stringify(JSON.parse(s), null, 2); } catch (e) { return s; } };
    async function load() {
      const box = $('#alist'); box.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div>';
      try {
        const p = await api('GET', '/api/audit' + qs({ username: aud.username, operation: aud.operation, entityType: aud.entityType, page: aud.page, size: 20 }));
        if (!p.content.length) { box.innerHTML = empty('🔍', 'Nenhum registro encontrado.'); return; }
        box.innerHTML = '<div class="table-wrap"><table class="tbl"><thead><tr><th>Data/hora</th><th>Usuário</th><th>Operação</th><th>Entidade</th><th>Detalhes</th></tr></thead><tbody>' +
          p.content.map((a) => '<tr><td class="small">' + fmtDateTime(a.occurredAt) + '</td><td>' + esc(a.username) + '</td><td>' + esc(OPS[a.operation] || a.operation) + '</td><td>' +
            esc(a.entityType) + (a.entityId != null ? ' #' + a.entityId : '') + '</td><td>' +
            ((a.oldValue || a.newValue) ? '<details class="json"><summary>ver valores</summary>' +
              (a.oldValue ? '<strong class="small">Antes</strong><pre>' + esc(pretty(a.oldValue)) + '</pre>' : '') +
              (a.newValue ? '<strong class="small">Depois</strong><pre>' + esc(pretty(a.newValue)) + '</pre>' : '') + '</details>' : '-') + '</td></tr>').join('') +
          '</tbody></table></div>' + pager(p);
      } catch (e) { box.innerHTML = alertBox('bad', e.message); }
    }
    $('#afilter').addEventListener('submit', (ev) => {
      ev.preventDefault(); aud.username = $('#au').value.trim(); aud.operation = $('#f_ao').value; aud.entityType = $('#f_ae').value; aud.page = 0; load();
    });
    bind({ prev: () => { aud.page = Math.max(0, aud.page - 1); load(); }, next: () => { aud.page += 1; load(); } });
    await load();
  }

  // ------------------------------------------------------------ usuários (ADMIN)
  async function vUsers() {
    if (!isAdmin()) { adminOnly(); return; }
    view.innerHTML = skeleton();
    const users = await api('GET', '/api/users');
    const roles = [['OPERADOR', 'Operador'], ['ADMIN', 'Administrador']];
    const reload = () => vUsers();
    view.innerHTML = pageHead('Usuários', 'Gerenciamento de acesso (ADMIN e OPERADOR)', '<button class="btn primary" data-action="u-new">+ Novo usuário</button>') +
      '<div class="card"><div class="table-wrap"><table class="tbl"><thead><tr><th>Login</th><th>Nome</th><th>Papel</th><th>Situação</th><th>Criado em</th><th></th></tr></thead><tbody>' +
      users.map((u) => '<tr><td class="mono">' + esc(u.username) + '</td><td>' + esc(u.fullName) + '</td><td>' + (u.role === 'ADMIN' ? '<span class="badge info">Administrador</span>' : '<span class="badge neutral">Operador</span>') +
        '</td><td>' + (u.enabled ? '<span class="badge ok">Ativo</span>' : '<span class="badge bad">Inativo</span>') + '</td><td class="small">' + fmtDateTime(u.createdAt) +
        '</td><td class="right"><button class="btn sm" data-action="u-edit" data-id="' + u.id + '">Editar</button></td></tr>').join('') + '</tbody></table></div></div>';
    bind({
      'u-new': () => openModal({ title: 'Novo usuário', submit: 'Criar',
        body: '<div class="form-grid">' + fInput('Login', 'username', '', { required: true, maxlength: 50, pattern: '[a-z0-9._-]{3,50}', autocomplete: 'off' }) +
          fInput('Nome completo', 'fullName', '', { required: true, maxlength: 120 }) +
          fInput('Senha (8 a 72 caracteres)', 'password', '', { type: 'password', required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password' }) +
          fSelect('Papel', 'role', roles, 'OPERADOR', { required: true }) + '</div>' +
          '<p class="muted small">Login em letras minúsculas, números, ponto, hífen ou sublinhado.</p>',
        onSubmit: async (fd) => { await api('POST', '/api/users', { username: fd.get('username'), fullName: fd.get('fullName'), password: fd.get('password'), role: fd.get('role') }); toast('Usuário criado.'); reload(); } }),
      'u-edit': (el) => {
        const u = users.find((x) => String(x.id) === el.dataset.id);
        openModal({ title: 'Editar ' + u.username,
          body: '<div class="form-grid">' + fInput('Nome completo', 'fullName', u.fullName, { required: true, maxlength: 120, full: true }) +
            fSelect('Papel', 'role', roles, u.role) + fSelect('Situação', 'enabled', [['true', 'Ativo'], ['false', 'Inativo']], String(u.enabled)) +
            fInput('Nova senha (deixe em branco para manter)', 'password', '', { type: 'password', minlength: 8, maxlength: 72, autocomplete: 'new-password', full: true }) + '</div>',
          onSubmit: async (fd) => {
            await api('PUT', '/api/users/' + u.id, { fullName: fd.get('fullName'), role: fd.get('role'), enabled: fd.get('enabled') === 'true', password: fd.get('password') || null });
            toast('Usuário atualizado.'); reload();
          } });
      }
    });
  }

  // ------------------------------------------------------------ navegação
  const NAV = [
    ['dashboard', 'Dashboard'], ['products', 'Produtos'], ['categories', 'Categorias e atributos'],
    ['sources', 'Fontes de informação'], ['listings', 'Anúncios preparados'], ['audit', 'Auditoria', true], ['users', 'Usuários', true]
  ];

  function renderNav() {
    $('#nav').innerHTML = NAV.filter((n) => !n[2] || isAdmin()).map((n) => '<a href="#/' + n[0] + '" data-route="' + n[0] + '">' + esc(n[1]) + '</a>').join('');
  }

  async function dispatch(parts) {
    switch (parts[0]) {
      case 'dashboard': return vDashboard();
      case 'products':
        if (parts[1] && parts[2] === 'briefing') return vBriefing(Number(parts[1]));
        if (parts[1]) return vProduct(Number(parts[1]));
        return vProducts();
      case 'categories': return vCategories();
      case 'sources': return vSources();
      case 'listings': return vListings();
      case 'audit': return vAudit();
      case 'users': return vUsers();
      default: view.innerHTML = alertBox('warn', 'Página não encontrada.');
    }
  }

  async function route() {
    const parts = (window.location.hash.replace(/^#\/?/, '') || 'dashboard').split('/');
    document.querySelectorAll('#nav a').forEach((a) => a.classList.toggle('active', a.dataset.route === parts[0]));
    $('#sidebar').classList.remove('open');
    const dlg = $('#modal'); if (dlg.open) dlg.close();
    currentHandlers = {};
    try {
      await dispatch(parts);
    } catch (e) {
      view.innerHTML = alertBox('bad', e.message);
    }
    view.focus({ preventScroll: true });
  }

  async function init() {
    try { me = await api('GET', '/api/auth/me'); } catch (e) { return; }
    $('#who').textContent = me.fullName;
    $('#role').textContent = me.role === 'ADMIN' ? 'Administrador' : 'Operador';
    renderNav();
    $('#logout').addEventListener('click', async () => {
      try { await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrf() } }); } catch (e) { /* segue */ }
      window.location.href = '/login.html';
    });
    $('#toggle-nav').addEventListener('click', () => $('#sidebar').classList.toggle('open'));
    window.addEventListener('hashchange', route);
    route();
  }

  init();
})();
