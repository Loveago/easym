import { Router } from 'express';

const router = Router();

const ADMIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>EasyMoMo Admin Panel</title>
  <style>
    :root {
      --bg: #f4f7f6;
      --surface: #ffffff;
      --border: #dce5e1;
      --primary: #0b6e4f;
      --primary-dark: #08543c;
      --primary-soft: #e6f4ef;
      --amber: #d97706;
      --danger: #dc2626;
      --text: #102a22;
      --muted: #5a7068;
      --radius: 12px;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
    }
    .hidden { display: none !important; }
    .login-wrap {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .login-card {
      width: 100%;
      max-width: 420px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 28px;
      box-shadow: 0 10px 30px rgba(11, 110, 79, 0.08);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 18px;
    }
    .brand-badge {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: var(--primary);
      color: #fff;
      display: grid;
      place-items: center;
      font-weight: 800;
      font-size: 16px;
    }
    .brand h1 { margin: 0; font-size: 20px; color: var(--primary); }
    .brand p { margin: 2px 0 0; font-size: 12px; color: var(--muted); }
    label {
      display: block;
      font-size: 12px;
      font-weight: 600;
      color: var(--muted);
      margin: 12px 0 6px;
    }
    input, select, textarea {
      width: 100%;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: 8px;
      font-size: 14px;
      color: var(--text);
      background: #fff;
    }
    input:focus, select:focus {
      outline: 2px solid rgba(11, 110, 79, 0.2);
      border-color: var(--primary);
    }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 10px 16px;
      border: none;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      background: var(--primary);
      color: #fff;
    }
    .btn:hover { background: var(--primary-dark); }
    .btn-sm { padding: 6px 10px; font-size: 12px; }
    .btn-outline {
      background: #fff;
      color: var(--text);
      border: 1px solid var(--border);
    }
    .btn-outline:hover { background: var(--bg); }
    .btn-danger { background: var(--danger); }
    .btn-amber { background: var(--amber); }
    .topbar {
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      padding: 12px 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: sticky;
      top: 0;
      z-index: 20;
    }
    .shell {
      max-width: 1280px;
      margin: 0 auto;
      padding: 20px 24px 48px;
    }
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 20px;
    }
    .tab-btn {
      padding: 9px 14px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--muted);
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
    }
    .tab-btn.active {
      background: var(--primary);
      color: #fff;
      border-color: var(--primary);
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      gap: 14px;
      margin-bottom: 20px;
    }
    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 18px;
      margin-bottom: 16px;
    }
    .card h2 {
      margin: 0 0 6px;
      font-size: 16px;
    }
    .card-sub {
      margin: 0 0 14px;
      font-size: 13px;
      color: var(--muted);
    }
    .kpi-label { font-size: 12px; color: var(--muted); font-weight: 600; }
    .kpi-val { font-size: 24px; font-weight: 800; margin-top: 6px; color: var(--primary); }
    .grid-2 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
      gap: 16px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }
    th, td {
      text-align: left;
      padding: 10px 8px;
      border-bottom: 1px solid var(--border);
      vertical-align: middle;
    }
    th { font-size: 11px; text-transform: uppercase; color: var(--muted); letter-spacing: 0.04em; }
    .badge {
      display: inline-block;
      padding: 3px 8px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 700;
      background: var(--primary-soft);
      color: var(--primary);
    }
    .badge.warn { background: #fef3c7; color: #92400e; }
    .badge.danger { background: #fee2e2; color: #991b1b; }
    .badge.neutral { background: #e5e7eb; color: #374151; }
    .row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .toast {
      position: fixed;
      bottom: 20px;
      right: 20px;
      background: var(--text);
      color: #fff;
      padding: 12px 18px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 600;
      z-index: 100;
      box-shadow: 0 8px 24px rgba(0,0,0,0.2);
    }
    .error-box {
      background: #fee2e2;
      color: #991b1b;
      padding: 10px 12px;
      border-radius: 8px;
      font-size: 13px;
      margin-bottom: 12px;
    }
  </style>
</head>
<body>
  <div id="loginView" class="login-wrap">
    <form id="loginForm" class="login-card">
      <div class="brand">
        <div class="brand-badge">EM</div>
        <div>
          <h1>EasyMoMo Admin</h1>
          <p>Control Center • Paystack, Agents &amp; USSD</p>
        </div>
      </div>
      <div id="loginError" class="error-box hidden"></div>
      <label for="loginEmail">Admin Email</label>
      <input id="loginEmail" type="email" required placeholder="admin@easymomo.app" />
      <label for="loginPassword">Password</label>
      <input id="loginPassword" type="password" required placeholder="••••••••" />
      <div style="margin-top:18px">
        <button class="btn" style="width:100%" type="submit">Sign In to Admin Panel</button>
      </div>
    </form>
  </div>

  <div id="appView" class="hidden">
    <header class="topbar">
      <div class="brand" style="margin:0">
        <div class="brand-badge">EM</div>
        <div>
          <h1>EasyMoMo Admin Panel</h1>
          <p id="adminIdentity">Signed in</p>
        </div>
      </div>
      <div class="row">
        <button id="refreshBtn" class="btn btn-outline btn-sm" type="button">Refresh</button>
        <button id="logoutBtn" class="btn btn-outline btn-sm" type="button">Sign Out</button>
      </div>
    </header>

    <main class="shell">
      <nav class="tabs" id="tabNav">
        <button class="tab-btn active" data-tab="overview">Overview</button>
        <button class="tab-btn" data-tab="users">Agents &amp; Trials</button>
        <button class="tab-btn" data-tab="settings">Paystack &amp; Settings</button>
        <button class="tab-btn" data-tab="plans">Plans &amp; Payments</button>
        <button class="tab-btn" data-tab="transactions">Transactions</button>
        <button class="tab-btn" data-tab="ussd">USSD Flows &amp; Bundles</button>
      </nav>

      <section id="tab-overview"></section>
      <section id="tab-users" class="hidden"></section>
      <section id="tab-settings" class="hidden"></section>
      <section id="tab-plans" class="hidden"></section>
      <section id="tab-transactions" class="hidden"></section>
      <section id="tab-ussd" class="hidden"></section>
    </main>
  </div>

  <div id="toast" class="toast hidden"></div>
  <script src="/admin/app.js"></script>
</body>
</html>`;

const ADMIN_JS = `(() => {
  const TOKEN_KEY = 'easymomo_admin_token';
  const USER_KEY = 'easymomo_admin_user';
  let activeTab = 'overview';

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmtMoney = (n) => 'GHS ' + Number(n || 0).toFixed(2);
  const fmtDate = (d) => d ? new Date(d).toLocaleString() : '—';

  function showToast(msg) {
    const el = $('toast');
    el.textContent = msg;
    el.classList.remove('hidden');
    setTimeout(() => el.classList.add('hidden'), 3200);
  }

  async function api(path, opts = {}) {
    const token = localStorage.getItem(TOKEN_KEY);
    const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const res = await fetch('/api' + path, Object.assign({}, opts, { headers }));
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (res.status === 401 || res.status === 403) {
        localStorage.removeItem(TOKEN_KEY);
      }
      throw new Error(data?.error?.message || 'Request failed (' + res.status + ')');
    }
    return data;
  }

  function switchTab(tab) {
    activeTab = tab;
    document.querySelectorAll('.tab-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.tab === tab);
    });
    ['overview', 'users', 'settings', 'plans', 'transactions', 'ussd'].forEach((t) => {
      $('tab-' + t).classList.toggle('hidden', t !== tab);
    });
    loadTab(tab);
  }

  async function loadTab(tab) {
    try {
      if (tab === 'overview') await renderOverview();
      else if (tab === 'users') await renderUsers();
      else if (tab === 'settings') await renderSettings();
      else if (tab === 'plans') await renderPlans();
      else if (tab === 'transactions') await renderTransactions();
      else if (tab === 'ussd') await renderUssd();
    } catch (err) {
      showToast(err.message);
    }
  }

  async function renderOverview() {
    const { kpis, recentUsers, recentTransactions } = await api('/admin/overview');
    $('tab-overview').innerHTML = \`
      <div class="kpi-grid">
        <div class="card"><div class="kpi-label">Total Agents</div><div class="kpi-val">\${kpis.totalAgents}</div></div>
        <div class="card"><div class="kpi-label">Active Paid</div><div class="kpi-val">\${kpis.paidCount}</div></div>
        <div class="card"><div class="kpi-label">Active Trials</div><div class="kpi-val">\${kpis.trialCount}</div></div>
        <div class="card"><div class="kpi-label">Suspended</div><div class="kpi-val" style="color:var(--danger)">\${kpis.suspendedAgents}</div></div>
        <div class="card"><div class="kpi-label">Successful Tx Volume</div><div class="kpi-val">\${fmtMoney(kpis.successfulTransactionVolumeGhs)}</div></div>
        <div class="card"><div class="kpi-label">Subscription Revenue</div><div class="kpi-val">\${fmtMoney(kpis.subscriptionRevenueGhs)}</div></div>
      </div>
      <div class="grid-2">
        <div class="card">
          <h2>Recent Agents</h2>
          <p class="card-sub">Latest registered mobile money agents</p>
          <table>
            <thead><tr><th>Agent</th><th>Account</th><th>Plan</th></tr></thead>
            <tbody>
              \${recentUsers.map(u => \`
                <tr>
                  <td><strong>\${esc(u.fullName)}</strong><br><small style="color:var(--muted)">\${esc(u.phone)} • \${esc(u.email)}</small></td>
                  <td><span class="badge \${u.accountStatus === 'ACTIVE' ? '' : 'danger'}">\${esc(u.accountStatus)}</span></td>
                  <td><span class="badge \${u.entitlement?.status === 'ACTIVE' ? '' : 'warn'}">\${esc(u.entitlement?.status || 'NONE')}</span></td>
                </tr>
              \`).join('') || '<tr><td colspan="3">No agents yet</td></tr>'}
            </tbody>
          </table>
        </div>
        <div class="card">
          <h2>Recent Transactions</h2>
          <p class="card-sub">Latest synced terminal activity</p>
          <table>
            <thead><tr><th>Agent</th><th>Details</th><th>Status</th></tr></thead>
            <tbody>
              \${recentTransactions.map(t => \`
                <tr>
                  <td>\${esc(t.user?.fullName || 'Agent')}</td>
                  <td><strong>\${esc(t.network)} \${esc(t.transactionType)}</strong><br><small>\${fmtMoney(t.amount)} → \${esc(t.recipientNumber)}</small></td>
                  <td><span class="badge \${t.status === 'SUCCESSFUL' ? '' : t.status === 'FAILED' ? 'danger' : 'warn'}">\${esc(t.status)}</span></td>
                </tr>
              \`).join('') || '<tr><td colspan="3">No transactions yet</td></tr>'}
            </tbody>
          </table>
        </div>
      </div>
    \`;
  }

  async function renderUsers(q = '', status = '') {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (status) params.set('status', status);
    const { users } = await api('/admin/users?' + params.toString());
    $('tab-users').innerHTML = \`
      <div class="card">
        <h2>Agents, Trials &amp; Access Control</h2>
        <p class="card-sub">Search agents, suspend/activate accounts, extend free trials, or promote admins.</p>
        <div class="row" style="margin-bottom:14px">
          <input id="userSearchInput" value="\${esc(q)}" placeholder="Search by name, phone, or email..." style="max-width:320px" />
          <select id="userStatusSelect" style="max-width:170px">
            <option value="">All Statuses</option>
            <option value="ACTIVE" \${status==='ACTIVE'?'selected':''}>ACTIVE</option>
            <option value="SUSPENDED" \${status==='SUSPENDED'?'selected':''}>SUSPENDED</option>
          </select>
          <button id="userFilterBtn" class="btn btn-sm" type="button">Filter</button>
        </div>
        <table>
          <thead><tr><th>Agent</th><th>Role</th><th>Status</th><th>Entitlement</th><th>Activity</th><th>Actions</th></tr></thead>
          <tbody>
            \${users.map(u => \`
              <tr>
                <td><strong>\${esc(u.fullName)}</strong><br><small>\${esc(u.phone)} • \${esc(u.email)}</small></td>
                <td><span class="badge \${u.role === 'ADMIN' ? 'warn' : 'neutral'}">\${esc(u.role)}</span></td>
                <td><span class="badge \${u.accountStatus === 'ACTIVE' ? '' : 'danger'}">\${esc(u.accountStatus)}</span></td>
                <td>\${esc(u.entitlement?.status || 'NONE')}<br><small>Trial ends: \${fmtDate(u.entitlement?.trialExpiresAt)}</small></td>
                <td>\${u._count.transactions} tx • \${u._count.payments} pay</td>
                <td class="row">
                  <button class="btn btn-sm \${u.accountStatus === 'ACTIVE' ? 'btn-danger' : ''}" data-toggle-user="\${u.id}" data-next="\${u.accountStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'}">
                    \${u.accountStatus === 'ACTIVE' ? 'Suspend' : 'Activate'}
                  </button>
                  <button class="btn btn-sm btn-amber" data-extend-trial="\${u.id}">+7d Trial</button>
                  <button class="btn btn-sm btn-outline" data-toggle-role="\${u.id}" data-next-role="\${u.role === 'ADMIN' ? 'AGENT' : 'ADMIN'}">
                    \${u.role === 'ADMIN' ? 'Make Agent' : 'Make Admin'}
                  </button>
                </td>
              </tr>
            \`).join('') || '<tr><td colspan="6">No matching agents</td></tr>'}
          </tbody>
        </table>
      </div>
    \`;

    $('userFilterBtn').onclick = () => renderUsers($('userSearchInput').value.trim(), $('userStatusSelect').value);
    document.querySelectorAll('[data-toggle-user]').forEach((btn) => {
      btn.onclick = async () => {
        await api('/admin/users/' + btn.dataset.toggleUser + '/status', {
          method: 'PATCH',
          body: JSON.stringify({ accountStatus: btn.dataset.next }),
        });
        showToast('Updated account status');
        renderUsers($('userSearchInput').value.trim(), $('userStatusSelect').value);
      };
    });
    document.querySelectorAll('[data-extend-trial]').forEach((btn) => {
      btn.onclick = async () => {
        await api('/admin/users/' + btn.dataset.extendTrial + '/trial-extend', {
          method: 'POST',
          body: JSON.stringify({ extraDays: 7 }),
        });
        showToast('Extended trial by 7 days');
        renderUsers($('userSearchInput').value.trim(), $('userStatusSelect').value);
      };
    });
    document.querySelectorAll('[data-toggle-role]').forEach((btn) => {
      btn.onclick = async () => {
        await api('/admin/users/' + btn.dataset.toggleRole + '/role', {
          method: 'PATCH',
          body: JSON.stringify({ role: btn.dataset.nextRole }),
        });
        showToast('Updated user role');
        renderUsers($('userSearchInput').value.trim(), $('userStatusSelect').value);
      };
    });
  }

  async function renderSettings() {
    const { settings } = await api('/admin/settings');
    const paystackKeys = settings.filter(s => s.key.startsWith('PAYSTACK_'));
    const platformKeys = settings.filter(s => !s.key.startsWith('PAYSTACK_'));

    const renderField = (s) => \`
      <div style="margin-bottom:14px">
        <div class="row" style="justify-content:space-between">
          <label for="set-\${s.key}" style="margin:0">\${esc(s.label)} <small style="font-weight:400">(\${esc(s.key)})</small></label>
          <span class="badge \${s.isConfigured ? '' : 'warn'}">\${s.isConfigured ? 'Configured (' + esc(s.source) + ')' : 'Not set'}</span>
        </div>
        <p style="margin:4px 0 6px;font-size:12px;color:var(--muted)">\${esc(s.description)}</p>
        <input id="set-\${s.key}" data-setting-key="\${s.key}" type="\${s.isSecret ? 'password' : 'text'}" value="\${esc(s.value)}" placeholder="\${s.isSecret ? 'Enter new secret key to update' : ''}" />
      </div>
    \`;

    $('tab-settings').innerHTML = \`
      <form id="settingsForm">
        <div class="grid-2">
          <div class="card">
            <h2>Paystack Keys &amp; Checkout</h2>
            <p class="card-sub">Changes take effect immediately for subscription checkout, verification, and webhook signatures.</p>
            \${paystackKeys.map(renderField).join('')}
          </div>
          <div class="card">
            <h2>Platform &amp; Trial Configuration</h2>
            <p class="card-sub">Control free trial defaults, support contact info, and app version gates.</p>
            \${platformKeys.map(renderField).join('')}
          </div>
        </div>
        <button class="btn" type="submit">Save Paystack &amp; Platform Settings</button>
      </form>
    \`;

    $('settingsForm').onsubmit = async (e) => {
      e.preventDefault();
      const payload = Array.from(document.querySelectorAll('[data-setting-key]')).map((el) => ({
        key: el.dataset.settingKey,
        value: el.value,
      }));
      await api('/admin/settings', { method: 'PUT', body: JSON.stringify({ settings: payload }) });
      showToast('Settings saved successfully');
      renderSettings();
    };
  }

  async function renderPlans() {
    const [{ plans }, { payments }] = await Promise.all([api('/admin/plans'), api('/admin/payments')]);
    $('tab-plans').innerHTML = \`
      <div class="grid-2">
        <div class="card">
          <h2>Subscription Plans</h2>
          <p class="card-sub">Manage pricing tiers offered in the EasyMoMo app</p>
          <table>
            <thead><tr><th>Code</th><th>Name</th><th>Price</th><th>Interval</th><th>Status</th></tr></thead>
            <tbody>
              \${plans.map(p => \`
                <tr>
                  <td><strong>\${esc(p.code)}</strong></td>
                  <td>\${esc(p.name)}</td>
                  <td>\${esc(p.currency)} \${Number(p.price).toFixed(2)}</td>
                  <td>\${esc(p.billingInterval)} (\${p.durationDays}d)</td>
                  <td><span class="badge \${p.active ? '' : 'neutral'}">\${p.active ? 'ACTIVE' : 'INACTIVE'}</span></td>
                </tr>
              \`).join('') || '<tr><td colspan="5">No plans yet</td></tr>'}
            </tbody>
          </table>
        </div>
        <div class="card">
          <h2>Create / Update Plan</h2>
          <p class="card-sub">Enter an existing code to update, or a new code to create.</p>
          <form id="planForm">
            <label>Code</label><input id="planCode" required placeholder="MONTHLY_PRO" />
            <label>Name</label><input id="planName" required placeholder="Monthly Agent Pro" />
            <div class="row">
              <div style="flex:1"><label>Price (GHS)</label><input id="planPrice" type="number" step="0.01" required placeholder="50" /></div>
              <div style="flex:1"><label>Duration (Days)</label><input id="planDays" type="number" required value="30" /></div>
            </div>
            <div class="row">
              <div style="flex:1">
                <label>Interval</label>
                <select id="planInterval">
                  <option value="MONTHLY">MONTHLY</option>
                  <option value="QUARTERLY">QUARTERLY</option>
                  <option value="ANNUAL">ANNUAL</option>
                </select>
              </div>
              <div style="flex:1">
                <label>Active</label>
                <select id="planActive"><option value="true">true</option><option value="false">false</option></select>
              </div>
            </div>
            <div style="margin-top:14px"><button class="btn" type="submit">Save Plan</button></div>
          </form>
        </div>
      </div>
      <div class="card">
        <h2>Paystack Payment History</h2>
        <table>
          <thead><tr><th>Reference</th><th>Agent</th><th>Plan</th><th>Amount</th><th>Status</th><th>Paid At</th></tr></thead>
          <tbody>
            \${payments.map(p => \`
              <tr>
                <td><code>\${esc(p.reference)}</code></td>
                <td>\${esc(p.user?.fullName || '')}<br><small>\${esc(p.user?.email || '')}</small></td>
                <td>\${esc(p.plan?.name || p.plan?.code || '—')}</td>
                <td>\${esc(p.currency)} \${Number(p.amount).toFixed(2)}</td>
                <td><span class="badge \${p.status === 'SUCCESS' ? '' : p.status === 'FAILED' ? 'danger' : 'warn'}">\${esc(p.status)}</span></td>
                <td>\${fmtDate(p.paidAt || p.createdAt)}</td>
              </tr>
            \`).join('') || '<tr><td colspan="6">No payments recorded yet</td></tr>'}
          </tbody>
        </table>
      </div>
    \`;

    $('planForm').onsubmit = async (e) => {
      e.preventDefault();
      await api('/admin/plans', {
        method: 'PUT',
        body: JSON.stringify({
          code: $('planCode').value.trim(),
          name: $('planName').value.trim(),
          price: Number($('planPrice').value),
          currency: 'GHS',
          billingInterval: $('planInterval').value,
          durationDays: Number($('planDays').value),
          active: $('planActive').value === 'true',
        }),
      });
      showToast('Subscription plan saved');
      renderPlans();
    };
  }

  async function renderTransactions(network = '', status = '') {
    const params = new URLSearchParams();
    if (network) params.set('network', network);
    if (status) params.set('status', status);
    const { transactions } = await api('/admin/transactions?' + params.toString());
    $('tab-transactions').innerHTML = \`
      <div class="card">
        <h2>All Agent Transactions</h2>
        <div class="row" style="margin-bottom:12px">
          <select id="txNet" style="max-width:160px">
            <option value="">All Networks</option>
            <option value="MTN" \${network==='MTN'?'selected':''}>MTN</option>
            <option value="TELECEL" \${network==='TELECEL'?'selected':''}>TELECEL</option>
            <option value="AT" \${network==='AT'?'selected':''}>AT</option>
          </select>
          <select id="txStatus" style="max-width:180px">
            <option value="">All Statuses</option>
            <option value="SUCCESSFUL" \${status==='SUCCESSFUL'?'selected':''}>SUCCESSFUL</option>
            <option value="FAILED" \${status==='FAILED'?'selected':''}>FAILED</option>
            <option value="PENDING" \${status==='PENDING'?'selected':''}>PENDING</option>
          </select>
          <button id="txFilterBtn" class="btn btn-sm" type="button">Filter</button>
        </div>
        <table>
          <thead><tr><th>Agent</th><th>Network / Type</th><th>Recipient</th><th>Amount</th><th>Status</th><th>Date</th></tr></thead>
          <tbody>
            \${transactions.map(t => \`
              <tr>
                <td>\${esc(t.user?.fullName || 'Agent')}<br><small>\${esc(t.user?.phone || '')}</small></td>
                <td><strong>\${esc(t.network)}</strong> • \${esc(t.transactionType)}</td>
                <td>\${esc(t.recipientNumber)}</td>
                <td>\${fmtMoney(t.amount)}</td>
                <td><span class="badge \${t.status === 'SUCCESSFUL' ? '' : t.status === 'FAILED' ? 'danger' : 'warn'}">\${esc(t.status)}</span></td>
                <td>\${fmtDate(t.createdAt)}</td>
              </tr>
            \`).join('') || '<tr><td colspan="6">No transactions found</td></tr>'}
          </tbody>
        </table>
      </div>
    \`;
    $('txFilterBtn').onclick = () => renderTransactions($('txNet').value, $('txStatus').value);
  }

  async function renderUssd() {
    const [{ flows }, { bundles }] = await Promise.all([api('/admin/ussd-flows'), api('/admin/bundles')]);
    $('tab-ussd').innerHTML = \`
      <div class="grid-2">
        <div class="card">
          <h2>Published USSD Flows</h2>
          <table>
            <thead><tr><th>Network</th><th>Type</th><th>Ver</th><th>Enabled</th></tr></thead>
            <tbody>
              \${flows.map(f => \`
                <tr>
                  <td><strong>\${esc(f.network)}</strong></td>
                  <td>\${esc(f.transactionType)}</td>
                  <td>v\${f.version}</td>
                  <td><span class="badge \${f.enabled ? '' : 'neutral'}">\${f.enabled ? 'ENABLED' : 'DISABLED'}</span></td>
                </tr>
              \`).join('') || '<tr><td colspan="4">No remote USSD flows</td></tr>'}
            </tbody>
          </table>
        </div>
        <div class="card">
          <h2>Data Bundles</h2>
          <table>
            <thead><tr><th>Network</th><th>Bundle</th><th>Price</th><th>Status</th></tr></thead>
            <tbody>
              \${bundles.map(b => \`
                <tr>
                  <td><strong>\${esc(b.network)}</strong></td>
                  <td>\${esc(b.name)} (\${esc(b.allowance)})</td>
                  <td>\${fmtMoney(b.price)}</td>
                  <td>
                    <button class="btn btn-sm \${b.active ? 'btn-outline' : ''}" data-toggle-bundle="\${b.id}" data-active="\${!b.active}">
                      \${b.active ? 'Disable' : 'Enable'}
                    </button>
                  </td>
                </tr>
              \`).join('') || '<tr><td colspan="4">No data bundles yet</td></tr>'}
            </tbody>
          </table>
        </div>
      </div>
    \`;
    document.querySelectorAll('[data-toggle-bundle]').forEach((btn) => {
      btn.onclick = async () => {
        await api('/admin/bundles/' + btn.dataset.toggleBundle, {
          method: 'PATCH',
          body: JSON.stringify({ active: btn.dataset.active === 'true' }),
        });
        showToast('Updated bundle status');
        renderUssd();
      };
    });
  }

  function showApp(user) {
    $('loginView').classList.add('hidden');
    $('appView').classList.remove('hidden');
    $('adminIdentity').textContent = (user?.fullName || 'Admin') + ' • ' + (user?.email || '');
    switchTab(activeTab);
  }

  function showLogin(errMsg = '') {
    $('appView').classList.add('hidden');
    $('loginView').classList.remove('hidden');
    if (errMsg) {
      $('loginError').textContent = errMsg;
      $('loginError').classList.remove('hidden');
    } else {
      $('loginError').classList.add('hidden');
    }
  }

  $('loginForm').onsubmit = async (e) => {
    e.preventDefault();
    try {
      const data = await api('/admin/login', {
        method: 'POST',
        body: JSON.stringify({
          email: $('loginEmail').value.trim(),
          password: $('loginPassword').value,
        }),
      });
      if (data.user?.role !== 'ADMIN') {
        showLogin('This account does not have ADMIN privileges.');
        return;
      }
      localStorage.setItem(TOKEN_KEY, data.token);
      localStorage.setItem(USER_KEY, JSON.stringify(data.user));
      showApp(data.user);
    } catch (err) {
      showLogin(err.message);
    }
  };

  $('logoutBtn').onclick = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    showLogin();
  };
  $('refreshBtn').onclick = () => loadTab(activeTab);
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.onclick = () => switchTab(btn.dataset.tab);
  });

  const savedToken = localStorage.getItem(TOKEN_KEY);
  const savedUser = JSON.parse(localStorage.getItem(USER_KEY) || 'null');
  if (savedToken && savedUser?.role === 'ADMIN') showApp(savedUser);
  else showLogin();
})();`;

router.get('/', (_req, res) => {
  res.redirect('/admin');
});

router.get('/admin', (_req, res) => {
  res.type('html').send(ADMIN_HTML);
});

router.get('/admin/app.js', (_req, res) => {
  res.type('application/javascript').send(ADMIN_JS);
});

export default router;
