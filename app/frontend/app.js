/*
  USI Foundry operator interface.

  Vanilla ES modules-free JS on purpose: the application ships as a jar with no build step, and a
  framework would add one for four screens. Every number rendered here comes from the API, which
  reads it back from the manufactured package, the run record or the verified registry index --
  nothing is computed in the browser, so the display cannot drift from the artefacts.
*/
'use strict';

const $ = (id) => document.getElementById(id);
const el = (tag, cls, text) => {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
};

async function api(path, options) {
  const response = await fetch(path, options);
  let body;
  try { body = await response.json(); } catch { body = { error: 'UNCLASSIFIED', message: 'Malformed response.' }; }
  if (!response.ok) { const e = new Error(body.message || 'Request failed'); e.body = body; throw e; }
  return body;
}

/* ── tabs ──────────────────────────────────────────────────────────────── */

$('tabs').addEventListener('click', (event) => {
  const tab = event.target.closest('.tab');
  if (!tab) return;
  document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-current', String(t === tab)));
  document.querySelectorAll('.view').forEach((v) => { v.hidden = v.id !== 'view-' + tab.dataset.view; });
  if (tab.dataset.view === 'runs') loadRuns();
  if (tab.dataset.view === 'status') loadStatus();
});

/* ── shared rendering ──────────────────────────────────────────────────── */

function readout(rows) {
  const table = el('table', 'readout');
  rows.forEach(([key, value, cls]) => {
    if (value === undefined || value === null || value === '') return;
    const tr = el('tr');
    tr.appendChild(el('th', null, key));
    const td = el('td');
    if (cls) { td.appendChild(el('span', cls, String(value))); } else { td.textContent = String(value); }
    tr.appendChild(td);
    table.appendChild(tr);
  });
  return table;
}

function counters(pairs) {
  const wrap = el('div', 'counters');
  pairs.forEach(([label, value]) => {
    const n = Number(value ?? 0);
    const box = el('div', n === 0 ? 'counter zero' : 'counter');
    box.appendChild(el('span', 'n', String(n)));
    box.appendChild(el('span', 'k', label));
    wrap.appendChild(box);
  });
  return wrap;
}

/*
  A USI identifier is shown with the scheme it is actually in. ADR-0005: no usi- string is minted,
  because an identifier an operator can copy must be one the registry will accept back.
*/
function usiIdCell(id, scheme) {
  return scheme === 'legacy-uao' ? `${id}   (legacy wire identifier)` : id;
}

function notice(code, message, guidance, isError) {
  const box = el('div', isError ? 'notice err' : 'notice');
  box.appendChild(el('strong', null, code));
  box.appendChild(document.createTextNode(' — ' + message));
  if (guidance) { box.appendChild(el('div', null, guidance)); }
  return box;
}

/* ── manufacture ───────────────────────────────────────────────────────── */

const STAGE_LABELS = {
  '01_JOB_INITIALISATION': 'Job initialisation', '02_SEED_NORMALISATION': 'Seed normalisation',
  '03_IDENTITY_INTERPRETATION': 'Identity interpretation', '04_SCOPE_RESOLUTION': 'Scope resolution',
  '05_MANUFACTURING_PLANNING': 'Manufacturing plan', '06_SOURCE_STRATEGY': 'Source strategy',
  '07_SOURCE_ACQUISITION': 'Source acquisition', '08_KNOWLEDGE_EXTRACTION': 'Evidence extraction',
  '09_CANDIDATE_VALIDATION': 'Candidate validation', '10_IDENTITY_RESOLUTION': 'Identity resolution',
  '11_RELATIONSHIP_CONSTRUCTION': 'Relationship construction', '12_CANONICAL_BUILD': 'Canonical build',
  '13_COMPLETENESS_ANALYSIS': 'Completeness analysis', '14_VERIFICATION': 'Verification',
  '15_PUBLICATION_DECISION': 'Publication decision', '16_PACKAGE_MANUFACTURE': 'Package manufacture'
};

$('f-provider').addEventListener('change', () => {
  $('fixture-row').hidden = $('f-provider').value !== 'fixture';
});

$('btn-manufacture').addEventListener('click', async () => {
  const identity = $('f-identity').value.trim();
  if (!identity) { $('manufacture-hint').className = 'hint err'; $('manufacture-hint').textContent = 'An identity or topic is required.'; return; }

  const provider = $('f-provider').value;
  const payload = {
    identity,
    context: $('f-context').value.trim() || null,
    provider,
    register: $('f-register').checked
  };
  if (provider === 'fixture') payload.fixture = $('f-fixture').value.trim();

  $('btn-manufacture').disabled = true;
  $('manufacture-hint').className = 'hint';
  $('manufacture-hint').textContent = '';
  $('result-panel').hidden = true;

  try {
    const started = await api('/api/manufacture', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    });
    await pollJob(started.jobToken);
  } catch (error) {
    renderFailure(error.body || { error: 'UNCLASSIFIED', message: error.message });
  } finally {
    $('btn-manufacture').disabled = false;
    refreshPlantState();
  }
});

async function pollJob(token) {
  for (;;) {
    const status = await api('/api/manufacture/' + encodeURIComponent(token));
    renderStages(status);
    if (status.state === 'COMPLETE') { renderResult(status.result); return; }
    if (status.state === 'FAILED') { renderFailure(status.failure); return; }
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
}

/* Stage statuses come from the pipeline's own checkpoint. Nothing here invents motion. */
function renderStages(status) {
  const list = $('stages');
  list.replaceChildren();
  (status.stages || []).forEach((entry) => {
    const done = entry.status === 'COMPLETE';
    const active = entry.status === 'ACTIVE' && status.state === 'RUNNING';
    const li = el('li', done ? 'complete' : active ? 'active' : '');
    li.appendChild(el('span', null, STAGE_LABELS[entry.stage] || entry.stage));
    li.appendChild(el('span', 'state', done ? 'COMPLETE' : active ? 'WORKING' : 'PENDING'));
    list.appendChild(li);
  });
  $('stage-hint').textContent =
    `${status.completedCount ?? 0} of ${status.totalCount ?? 16} stages recorded · ${status.elapsedSeconds ?? 0}s elapsed`;
}

function renderResult(result) {
  const target = $('result');
  target.replaceChildren();
  $('result-panel').hidden = false;

  const verificationPass = result.verification === 'PASS';
  const registered = result.registryAdmission === 'REGISTERED';

  target.appendChild(readout([
    ['USI manufactured', verificationPass ? 'PASS' : 'FAIL', verificationPass ? 'verdict pass' : 'verdict fail'],
    ['USI ID', usiIdCell(result.usiId, result.identifierScheme)],
    ['Canonical label', result.canonicalLabel],
    ['Verification', result.verification, verificationPass ? 'verdict pass' : 'verdict fail'],
    ['Publication status', result.publicationStatus],
    ['Registry admission', result.registryAdmission,
      registered ? 'verdict pass' : result.registryAdmission === 'REFUSED' ? 'verdict hold' : ''],
    ['Package', result.packageId],
    ['Run record', result.runId]
  ]));

  const c = result.counts || {};
  target.appendChild(counters([
    ['Existing identities reused', c.existingIdentitiesReused],
    ['New identities manufactured', c.newIdentitiesManufactured],
    ['Registry sources reused', c.registrySourcesReused],
    ['New sources acquired', c.newSourcesAcquired],
    ['Relationships discovered', c.relationshipsDiscovered],
    ['Relationships unresolved', c.relationshipsUnresolved],
    ['Semantic variants', c.semanticVariants]
  ]));

  if (result.registryAdmissionDetail) {
    target.appendChild(notice('ADMISSION REFUSED', result.registryAdmissionDetail, null, false));
  }
  if (result.relationshipAuthority) {
    target.appendChild(notice(result.relationshipAuthority, result.relationshipAuthorityNote, null, false));
  }

  const actions = el('div', 'row');
  actions.style.marginTop = '0.9rem';
  const inspect = el('button', null, 'INSPECT USI');
  inspect.addEventListener('click', () => { $('i-ref').value = result.usiId; showView('identity'); loadIdentity(); });
  const open = el('button', null, 'OPEN PACKAGE');
  open.addEventListener('click', () => { $('p-id').value = result.packageId; showView('package'); loadPackage(); });
  actions.appendChild(inspect);
  actions.appendChild(open);
  target.appendChild(actions);
}

function renderFailure(failure) {
  $('result-panel').hidden = false;
  const target = $('result');
  target.replaceChildren();
  target.appendChild(readout([['USI manufactured', 'FAIL', 'verdict fail']]));
  target.appendChild(notice(failure.error || 'UNCLASSIFIED', failure.message || 'No message.', failure.guidance, true));
}

function showView(name) {
  document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-current', String(t.dataset.view === name)));
  document.querySelectorAll('.view').forEach((v) => { v.hidden = v.id !== 'view-' + name; });
}

/* ── registry search ───────────────────────────────────────────────────── */

$('btn-search').addEventListener('click', loadSearch);
$('s-query').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadSearch(); });

async function loadSearch() {
  const target = $('search-results');
  const query = $('s-query').value.trim();
  if (!query) return;
  target.replaceChildren(el('div', 'empty', 'Searching…'));
  try {
    const data = await api('/api/registry/search?q=' + encodeURIComponent(query));
    target.replaceChildren();
    if (!data.matches.length) { target.appendChild(el('div', 'empty', 'No registered identity matched.')); return; }
    data.matches.forEach((m) => {
      const card = el('div', 'card');
      card.appendChild(el('h3', null, (m.canonicalLabels || [])[0] || m.resolutionKey));
      card.appendChild(el('p', 'sub', usiIdCell(m.usiId, m.identifierScheme) + '  ·  ' + m.resolutionKey));
      (m.matchedBy || []).forEach((k) => card.appendChild(el('span', 'tag', 'matched by ' + k)));
      card.appendChild(el('span', m.semanticVariantStatus === 'SINGLE_VARIANT' ? 'tag pass' : 'tag hold', m.semanticVariantStatus));
      card.appendChild(el('span', m.lifecycleState === 'ACTIVE' ? 'tag' : 'tag hold', m.lifecycleState || 'ACTIVE'));
      card.appendChild(el('span', 'tag', m.occurrenceCount + ' occurrence(s)'));
      const open = el('button', null, 'INSPECT');
      open.style.marginTop = '0.5rem';
      open.addEventListener('click', () => { $('i-ref').value = m.usiId; showView('identity'); loadIdentity(); });
      card.appendChild(el('div')).appendChild(open);
      target.appendChild(card);
    });
  } catch (error) {
    target.replaceChildren(notice((error.body || {}).error || 'ERROR', error.message, (error.body || {}).guidance, true));
  }
}

/* ── identity inspector ────────────────────────────────────────────────── */

$('btn-identity').addEventListener('click', loadIdentity);
$('i-ref').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadIdentity(); });

async function loadIdentity() {
  const target = $('identity-result');
  const ref = $('i-ref').value.trim();
  if (!ref) return;
  target.replaceChildren(el('div', 'empty', 'Resolving…'));
  try {
    const data = await api('/api/identity/' + encodeURIComponent(ref));
    target.replaceChildren();

    const resolved = data.decision === 'SAME';
    const panel = el('fieldset', 'panel');
    panel.appendChild(el('legend', null, 'Resolution'));
    panel.appendChild(readout([
      ['Decision', data.decision, resolved ? 'verdict pass' : 'verdict hold'],
      ['Reason codes', (data.reasonCodes || []).join(', ')]
    ]));
    target.appendChild(panel);

    if (!resolved) {
      const cands = el('fieldset', 'panel');
      cands.appendChild(el('legend', null, 'Candidates'));
      if (!(data.candidates || []).length) {
        cands.appendChild(el('div', 'empty', 'Nothing matched. That is not evidence the identity does not exist.'));
      }
      (data.candidates || []).forEach((c) => {
        const card = el('div', 'card');
        card.appendChild(el('h3', null, usiIdCell(c.usiId, c.identifierScheme)));
        card.appendChild(el('p', 'sub', c.resolutionKey));
        cands.appendChild(card);
      });
      target.appendChild(cands);
      return;
    }

    const i = data.identity;
    const detail = el('fieldset', 'panel');
    detail.appendChild(el('legend', null, 'Semantic identity'));
    detail.appendChild(readout([
      ['USI ID', usiIdCell(i.usiId, i.identifierScheme)],
      ['Resolution key', i.resolutionKey],
      ['Semantic type', i.semanticType === null ? '(none — declared by no namespace)' : i.semanticType],
      ['Canonical labels', (i.canonicalLabels || []).join(', ')],
      ['Aliases', (i.aliases || []).join(', ') || '(none)'],
      ['External identifiers', Object.entries(i.externalIdentifiers || {}).map(([k, v]) => k + ':' + v).join(', ') || '(none)'],
      ['Lifecycle', i.lifecycleState, i.lifecycleState === 'ACTIVE' ? '' : 'verdict hold'],
      ['Successors', (i.successorUids || []).join(', ')],
      ['Semantic variants', i.semanticVariantStatus, i.semanticVariantStatus === 'SINGLE_VARIANT' ? 'verdict pass' : 'verdict hold'],
      ['State versions', (i.stateVersions || []).length],
      ['Occurrences', (i.occurrences || []).length],
      ['Identity decisions', (i.identityDecisions || []).length],
      ['Relationship bindings', (i.relationshipBindings || []).length]
    ]));
    target.appendChild(detail);

    if (i.semanticVariantStatus !== 'SINGLE_VARIANT') {
      detail.appendChild(notice('MULTIPLE_UNRECONCILED_VARIANTS',
        'Every occurrence is preserved and none is chosen. Automatic reuse of this identity is blocked until a governed reconciliation exists. Unrelated identities are unaffected.',
        null, false));
    }

    if ((i.identityDecisions || []).length) {
      const hist = el('fieldset', 'panel');
      hist.appendChild(el('legend', null, 'Identity decision history'));
      i.identityDecisions.forEach((d) => {
        const card = el('div', 'card');
        card.appendChild(el('h3', null, d.decision));
        card.appendChild(el('p', 'sub', 'package ' + d.packageId));
        (d.reasonCodes || []).forEach((r) => card.appendChild(el('span', 'tag', r)));
        hist.appendChild(card);
      });
      target.appendChild(hist);
    }

    if ((i.relationshipBindings || []).length) {
      const rel = el('fieldset', 'panel');
      rel.appendChild(el('legend', null, 'Relationship candidates'));
      i.relationshipBindings.forEach((b) => {
        const card = el('div', 'card');
        card.appendChild(el('h3', null, b.typeVersion + '  ·  role: ' + b.role));
        card.appendChild(el('p', 'sub', 'package ' + b.packageId + '  ·  ' + b.identityBindingStatus));
        card.appendChild(el('span', 'tag hold', 'not published — ' + b.blockedBy));
        rel.appendChild(card);
      });
      rel.appendChild(notice('URO_TYPE_AUTHORITY_UNAVAILABLE',
        'Bound to persistent identities and retained as evidence. Canonical publication is fail-closed pending 17th2nd/ASA#29.', null, false));
      target.appendChild(rel);
    }

    await renderStagedNeighbourhood(target, i.usiId);

    const sig = el('fieldset', 'panel');
    sig.appendChild(el('legend', null, 'ASA input (A_x / R_x)'));
    const sigBtn = el('button', null, 'SHOW SIGNIFICANCE INPUTS');
    sigBtn.addEventListener('click', async () => {
      try {
        const inputs = await api('/api/significance/' + encodeURIComponent(i.usiId));
        sig.appendChild(el('pre', null, JSON.stringify(inputs, null, 1)));
        sigBtn.disabled = true;
      } catch (error) {
        sig.appendChild(notice((error.body || {}).error || 'ERROR', error.message, (error.body || {}).guidance, true));
      }
    });
    sig.appendChild(sigBtn);
    sig.appendChild(el('p', 'hint', 'Debugging and integration view. The Foundry supplies durable inputs to significance and never computes or stores it.'));
    target.appendChild(sig);
  } catch (error) {
    target.replaceChildren(notice((error.body || {}).error || 'ERROR', error.message, (error.body || {}).guidance, true));
  }
}

/*
  Staged relationship candidates are memory, not authority. The panel exists so an operator can see
  what has accumulated for an identity, and its wording is part of the contract: nothing here may
  read as a canonical URO, because none exists (17th2nd/ASA#29).
*/
async function renderStagedNeighbourhood(target, usiId) {
  const panel = el('fieldset', 'panel');
  panel.appendChild(el('legend', null, 'Staged relationship candidates (non-canonical)'));
  try {
    const n = await api('/api/staged-relationships/' + encodeURIComponent(usiId));
    const edges = n.edges || [];
    if (!edges.length) {
      panel.appendChild(el('div', 'empty',
        'No staged relationship candidates mention this identity. That is not evidence none exist.'));
      target.appendChild(panel);
      return;
    }
    edges.forEach((e) => {
      const card = el('div', 'card');
      card.appendChild(el('h3', null, e.typeVersion));
      card.appendChild(el('p', 'sub',
        e.stagedId + '  ·  from package ' + e.packageId + '  ·  recorded ' + e.recordedAt));
      (e.participants || []).forEach((pt) => {
        card.appendChild(el('span', pt.binding === 'RESOLVED' ? 'tag pass' : 'tag hold',
          pt.role + ' → ' + (pt.uaoId || 'unresolved')));
      });
      card.appendChild(el('span', 'tag hold', e.identityBindingStatus));
      card.appendChild(el('span', 'tag hold', 'certifying: false'));
      (e.sourceRefs || []).forEach((s) => card.appendChild(el('span', 'tag', 'evidence ' + s)));
      panel.appendChild(card);
    });
    if ((n.neighbourUids || []).length) {
      panel.appendChild(el('p', 'hint', 'Candidate neighbours: ' + n.neighbourUids.join(', ')));
    }
    panel.appendChild(notice(n.authorityStatus,
      'Candidate relationship memory only — asserted, not governed. No canonical URO exists and '
        + 'none is implied; publication remains fail-closed pending 17th2nd/ASA#29.', null, false));
  } catch (error) {
    panel.appendChild(notice((error.body || {}).error || 'ERROR', error.message,
      (error.body || {}).guidance, true));
  }
  target.appendChild(panel);
}

/* ── package inspector ─────────────────────────────────────────────────── */

$('btn-package').addEventListener('click', loadPackage);
$('p-id').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadPackage(); });

async function loadPackage() {
  const target = $('package-result');
  const id = $('p-id').value.trim();
  if (!id) return;
  target.replaceChildren(el('div', 'empty', 'Opening…'));
  try {
    const p = await api('/api/package/' + encodeURIComponent(id));
    target.replaceChildren();

    const head = el('fieldset', 'panel');
    head.appendChild(el('legend', null, 'Package'));
    head.appendChild(readout([
      ['Package ID', p.packageId],
      ['Content digest', p.contentDigest],
      ['Root USI', p.rootUsiId],
      ['Publication status', p.publicationStatus],
      ['Verification', p.verificationPassed ? 'PASS' : 'FAIL', p.verificationPassed ? 'verdict pass' : 'verdict fail'],
      ['Legacy embedded reuse report', p.legacyEmbeddedReuseReport ? 'yes (pre-ADR-0006 package)' : 'no']
    ]));
    (p.verificationChecks || []).forEach((c) => head.appendChild(el('span', 'tag pass', c)));
    (p.verificationWarnings || []).forEach((w) => head.appendChild(notice('WARNING', w, null, false)));
    (p.publicationReasons || []).forEach((r) => head.appendChild(el('p', 'hint', r)));
    target.appendChild(head);

    const ids = el('fieldset', 'panel');
    ids.appendChild(el('legend', null, 'Canonical identities'));
    p.identities.forEach((i) => {
      const card = el('div', 'card');
      card.appendChild(el('h3', null, i.canonicalLabel));
      card.appendChild(el('p', 'sub', i.usiId + '  ·  ' + (i.semanticType || 'no declared type')));
      card.appendChild(el('span', 'tag', i.lifecycleStatus));
      card.appendChild(el('span', 'tag', i.assertionCount + ' assertion(s)'));
      Object.entries(i.externalIdentifiers || {}).forEach(([k, v]) => card.appendChild(el('span', 'tag', k + ':' + v)));
      ids.appendChild(card);
    });
    target.appendChild(ids);

    const src = el('fieldset', 'panel');
    src.appendChild(el('legend', null, 'Evidence sources'));
    (p.sources || []).forEach((s) => {
      const card = el('div', 'card');
      card.appendChild(el('h3', null, s.sourceId));
      card.appendChild(el('p', 'sub', s.locator + '  ·  ' + s.sourceClass));
      card.appendChild(el('span', 'tag', 'sha256 ' + String(s.sha256).slice(0, 16) + '…'));
      src.appendChild(card);
    });
    target.appendChild(src);

    if ((p.unresolvedRelationships || []).length) {
      const rel = el('fieldset', 'panel');
      rel.appendChild(el('legend', null, 'Unresolved relationship candidates'));
      p.unresolvedRelationships.forEach((r) => {
        const card = el('div', 'card');
        card.appendChild(el('h3', null, r.typeVersion));
        card.appendChild(el('p', 'sub', r.candidateId + '  ·  ' + r.identityBindingStatus));
        (r.participants || []).forEach((pt) => {
          card.appendChild(el('span', pt.binding === 'RESOLVED' ? 'tag pass' : 'tag hold',
            pt.role + ' → ' + (pt.uaoId || 'unresolved')));
        });
        rel.appendChild(card);
      });
      rel.appendChild(notice('URO_TYPE_AUTHORITY_UNAVAILABLE',
        'Canonical URO count is zero. Retained as evidence pending 17th2nd/ASA#29.', null, false));
      target.appendChild(rel);
    }
  } catch (error) {
    target.replaceChildren(notice((error.body || {}).error || 'ERROR', error.message, (error.body || {}).guidance, true));
  }
}

/* ── runs ──────────────────────────────────────────────────────────────── */

$('btn-runs').addEventListener('click', loadRuns);

async function loadRuns() {
  const target = $('runs-result');
  target.replaceChildren(el('div', 'empty', 'Loading…'));
  try {
    const data = await api('/api/runs');
    target.replaceChildren();
    if (!data.runs.length) { target.appendChild(el('div', 'empty', 'No manufacturing runs recorded yet.')); return; }
    data.runs.forEach((r) => {
      const card = el('div', 'card');
      card.appendChild(el('h3', null, r.identity));
      card.appendChild(el('p', 'sub', r.runId + '  ·  ' + r.completedAt + '  ·  provider: ' + r.provider));
      card.appendChild(el('span', r.status === 'COMPLETED' ? 'tag pass' : 'tag hold', r.status));
      if (r.packageId) card.appendChild(el('span', 'tag', r.packageId));
      card.appendChild(el('span', 'tag', r.usiCount + ' USI(s)'));
      if (r.counts) {
        card.appendChild(el('span', 'tag', 'reused ' + r.counts.reusedUaoCount));
        card.appendChild(el('span', 'tag', 'new ' + r.counts.newUaoCount));
      }
      if (r.note) card.appendChild(el('p', 'hint', r.note));
      target.appendChild(card);
    });
    target.appendChild(el('p', 'hint', 'Run store: ' + data.runStore));
  } catch (error) {
    target.replaceChildren(notice((error.body || {}).error || 'ERROR', error.message, null, true));
  }
}

/* ── status ────────────────────────────────────────────────────────────── */

$('btn-status').addEventListener('click', loadStatus);
$('btn-verify').addEventListener('click', async () => {
  const target = $('status-result');
  try {
    const v = await api('/api/registry/verify', { method: 'POST' });
    target.prepend(notice(v.passed ? 'REGISTRY VERIFIED' : 'REGISTRY VERIFICATION FAILED',
      (v.packageCount || 0) + ' package(s), ' + (v.identityCount || 0) + ' identity(ies).',
      (v.errors || []).join(' · ') || null, !v.passed));
  } catch (error) {
    target.prepend(notice('ERROR', error.message, null, true));
  }
});

async function loadStatus() {
  const target = $('status-result');
  target.replaceChildren(el('div', 'empty', 'Loading…'));
  try {
    const s = await api('/api/status');
    target.replaceChildren();
    const panel = el('fieldset', 'panel');
    panel.appendChild(el('legend', null, 'Plant'));
    panel.appendChild(readout([
      ['Application', s.applicationName + ' ' + s.applicationVersion],
      ['Home', s.home],
      ['Registry', s.registry],
      ['Run store', s.runs],
      ['Staged relationships (non-canonical)', s.stagedRelationships],
      ['Package output', s.packages],
      ['Schema directory', s.schemaDir],
      ['Registry verification', s.registryVerification, s.registryVerification === 'PASS' ? 'verdict pass' : 'verdict fail'],
      ['Provider configured', s.claudeCommandConfigured ? 'yes' : 'no — fixture manufacture only'],
      ['Relationship authority', s.relationshipAuthority + ' (' + s.relationshipAuthorityIssue + ')', 'verdict hold']
    ]));
    panel.appendChild(counters([
      ['Packages', s.packageCount], ['Identities', s.identityCount],
      ['Unreconciled', s.unreconciledIdentities], ['Non-active', s.nonActiveIdentities],
      ['Identity operations', (s.identityOperations || []).length], ['Runs', s.runCount],
      ['Staged candidates', s.stagedRelationshipCount]
    ]));
    (s.registryErrors || []).forEach((e) => panel.appendChild(notice('REGISTRY ERROR', e, null, true)));
    if (s.stagedRelationshipStoreError) {
      panel.appendChild(notice('STAGING STORE FAIL-CLOSED', s.stagedRelationshipStoreError, null, true));
    }
    target.appendChild(panel);

    if ((s.identityOperations || []).length) {
      const ops = el('fieldset', 'panel');
      ops.appendChild(el('legend', null, 'Identity lifecycle operations'));
      s.identityOperations.forEach((o) => {
        const card = el('div', 'card');
        card.appendChild(el('h3', null, o.operation));
        card.appendChild(el('p', 'sub', o.operationId + '  ·  ' + o.recordedAt));
        (o.subjects || []).forEach((x) => card.appendChild(el('span', 'tag', 'subject ' + x)));
        (o.targets || []).forEach((x) => card.appendChild(el('span', 'tag pass', 'target ' + x)));
        card.appendChild(el('p', 'hint', o.justification));
        ops.appendChild(card);
      });
      target.appendChild(ops);
    }
  } catch (error) {
    target.replaceChildren(notice('ERROR', error.message, null, true));
  }
}

/* ── masthead state ────────────────────────────────────────────────────── */

async function refreshPlantState() {
  try {
    const s = await api('/api/status');
    const registry = $('chip-registry');
    registry.textContent = 'registry ' + s.registryVerification;
    registry.className = 'chip ' + (s.registryVerification === 'PASS' ? 'pass' : 'fail');

    const identities = $('chip-identities');
    identities.textContent = s.identityCount + ' identities · ' + s.packageCount + ' packages';
    identities.className = 'chip';

    const authority = $('chip-authority');
    authority.textContent = 'relationship authority unavailable';
    authority.className = 'chip hold';

    $('footer-version').textContent = s.applicationName + ' ' + s.applicationVersion + ' · ' + s.home;
  } catch {
    $('chip-registry').textContent = 'registry unreachable';
    $('chip-registry').className = 'chip fail';
  }
}

refreshPlantState();
