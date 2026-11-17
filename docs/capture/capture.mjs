// Captures the report's screenshots from the running stack (docker compose up) and renders the BPMN models.
// Run from the repository root in the Codespace:
//   docker run --rm --network host -v "$PWD:/work" -w /work/docs/capture node:22-bookworm \
//     bash -c "npm i --silent --no-save playwright@1 && npx playwright install --with-deps chromium >/dev/null && node capture.mjs"
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.BASE || 'http://localhost:3000';
const API = `${BASE}/api`;
const PASSWORD = process.env.DEMO_PASSWORD || 'demo123';
const OUT = path.resolve('../screenshots');
const MODELS = path.resolve('../../bpmn-models');
fs.mkdirSync(OUT, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

// ------------------------------------------------------------------ API helpers (for polling and setup)
async function apiLogin(username) {
  const r = await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: PASSWORD }) });
  if (!r.ok) throw new Error(`login ${username}: ${r.status}`);
  return (await r.json()).token;
}
async function api(token, p, { method = 'GET', body, form } = {}) {
  const headers = { Authorization: `Bearer ${token}` };
  let payload = form;
  if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const r = await fetch(`${API}${p}`, { method, headers, body: payload });
  if (!r.ok) throw new Error(`${method} ${p}: ${r.status} ${await r.text()}`);
  return r.status === 204 ? null : r.json();
}
async function waitFor(fn, label, timeout = 180000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const v = await fn().catch(() => null);
    if (v) return v;
    await sleep(2000);
  }
  throw new Error(`timed out waiting for ${label}`);
}
const findTask = (token, requestId, kind) =>
  waitFor(async () => (await api(token, '/tasks')).find((t) => t.requestId === requestId && t.kind === kind), `${kind} task for ${requestId}`);
const waitStatus = (token, requestId, statuses) =>
  waitFor(async () => { const s = await api(token, `/requests/${requestId}/status`); return statuses.includes(s.status) && s.processState === 'COMPLETED' ? s : null; }, `${requestId} to finish`);

// sample documents (the validate-document worker checks the real file signature)
const PDF = Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n');
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64');
const files = { pdf: '/tmp/medical-certificate.pdf', png: '/tmp/hospital-discharge-summary.png' };
fs.writeFileSync(files.pdf, PDF);
fs.writeFileSync(files.png, PNG);

async function submitViaApi(token, courseCode, reasonCategory, reason) {
  const form = new FormData();
  form.append('file', new Blob([PDF], { type: 'application/pdf' }), 'medical-certificate.pdf');
  const f = await api(token, '/files', { method: 'POST', form });
  const r = await api(token, '/requests', { method: 'POST', body: { courseCode, reasonCategory, reason, documents: [{ type: 'MEDICAL_CERTIFICATE', fileId: f.fileId }] } });
  return r.requestId;
}
async function reviewViaApi(token, requestId, remarks) {
  const t = await findTask(token, requestId, 'REVIEW');
  await api(token, `/tasks/${t.taskKey}/review`, { method: 'POST', body: { decision: 'APPROVED', remarks } });
}

// ------------------------------------------------------------------ browser helpers
const browser = await chromium.launch();
async function session(username, viewport = { width: 1280, height: 860 }) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1.5 });
  const page = await context.newPage();
  await page.goto(`${BASE}/login`);
  await page.fill('#username', username);
  await page.fill('#password', PASSWORD);
  await page.click('button[type=submit]');
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
  return page;
}
async function shot(page, name, fullPage = true) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await sleep(600);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage });
  log('saved', name);
}

// ------------------------------------------------------------------ 0. BPMN diagrams rendered with bpmn-js
async function renderModels() {
  const page = await browser.newPage({ viewport: { width: 2400, height: 1400 }, deviceScaleFactor: 1 });
  await page.setContent('<html><body style="margin:0;background:#fff"><div id="c" style="width:2400px;height:1400px"></div></body></html>');
  await page.addScriptTag({ url: 'https://unpkg.com/bpmn-js@17.11.1/dist/bpmn-viewer.development.js' });
  for (const [file, name] of [['condonation-main.bpmn', 'bpmn-main'], ['approval-child.bpmn', 'bpmn-approval-child'], ['notification-child.bpmn', 'bpmn-notification-child']]) {
    const xml = fs.readFileSync(path.join(MODELS, file), 'utf8');
    const box = await page.evaluate(async (x) => {
      document.getElementById('c').innerHTML = '';
      const viewer = new window.BpmnJS({ container: '#c' });
      await viewer.importXML(x);
      const canvas = viewer.get('canvas');
      const inner = canvas.viewbox().inner;
      canvas.viewbox({ x: inner.x - 30, y: inner.y - 30, width: inner.width + 60, height: inner.height + 60 });
      return { w: Math.ceil(inner.width + 60), h: Math.ceil(inner.height + 60) };
    }, xml);
    await page.setViewportSize({ width: box.w, height: box.h });
    await page.evaluate(({ w, h }) => { const c = document.getElementById('c'); c.style.width = `${w}px`; c.style.height = `${h}px`; }, box);
    const xml2 = xml;
    await page.evaluate(async (x) => {
      document.getElementById('c').innerHTML = '';
      const viewer = new window.BpmnJS({ container: '#c' });
      await viewer.importXML(x);
      const canvas = viewer.get('canvas');
      const inner = canvas.viewbox().inner;
      canvas.viewbox({ x: inner.x - 30, y: inner.y - 30, width: inner.width + 60, height: inner.height + 60 });
    }, xml2);
    await sleep(300);
    await page.screenshot({ path: path.join(OUT, `${name}.png`) });
    log('saved', name, box);
  }
  await page.close();
}

// ------------------------------------------------------------------ the walkthrough
try {
  await renderModels();

  const tok = {};
  for (const u of ['fa', 'aa', 'hod', 'admin', 'RA2311004010101', 'RA2311004010102', 'RA2311004010103']) tok[u] = await apiLogin(u);

  // A second request waiting for the FA (so the inbox shows more than one) and one that will hit the simulated SIS failure
  const arjun = await submitViaApi(tok.RA2311004010101, '21ECC302J', 'MEDICAL', 'Viral fever with hospital admission from 4 to 15 September; medical certificate attached.');
  log('Arjun request', arjun);
  await api(tok.admin, '/admin/settings', { method: 'PUT', body: { simulateSisFailure: true } });
  const priya = await submitViaApi(tok.RA2311004010102, '21ECC301T', 'MEDICAL', 'Fractured wrist in a road accident on 2 August; advised rest for three weeks.');
  await api(tok.admin, '/admin/settings', { method: 'PUT', body: { simulateSisFailure: false } });
  log('Priya request (SIS failure)', priya);

  // 1-4: student submits through the website
  const login = await browser.newPage({ viewport: { width: 1280, height: 860 }, deviceScaleFactor: 1.5 });
  await login.goto(`${BASE}/login`);
  await shot(login, '01-login', false);
  await login.close();

  const rahul = await session('RA2311004010103');
  await rahul.goto(`${BASE}/requests/new`);
  await rahul.check('input[value="21ECC301T"]');
  await rahul.selectOption('#reasonCategory', 'MEDICAL');
  await rahul.fill('#reason', 'I had dengue fever and was advised two weeks of bed rest from 18 to 31 August, so I missed 9 Digital Signal Processing classes.');
  const rows = rahul.locator('fieldset ol > li');
  await rows.nth(0).locator('input[type=file]').setInputFiles(files.pdf);
  await rows.nth(0).locator('input:not([type=file])').fill("Doctor's certificate, 18 to 31 August");
  await rahul.getByRole('button', { name: 'Add another document' }).click();
  await rows.nth(1).locator('select').selectOption('HOSPITAL_RECORD');
  await rows.nth(1).locator('input[type=file]').setInputFiles(files.png);
  await rows.nth(1).locator('input:not([type=file])').fill('Discharge summary from the hospital');
  await rahul.locator('span', { hasText: /^Uploaded$/ }).nth(1).waitFor();
  await shot(rahul, '03-new-request-form');
  await rahul.getByRole('button', { name: 'Submit request' }).click();
  await rahul.waitForURL(/\/requests\/REQ-/);
  const requestId = rahul.url().split('/').pop();
  log('Rahul request', requestId);
  await findTask(tok.fa, requestId, 'REVIEW');
  await rahul.reload();
  await shot(rahul, '04-route-slip-in-progress');

  // 5-6: Faculty Advisor
  await findTask(tok.fa, arjun, 'REVIEW');
  const fa = await session('fa');
  await fa.goto(`${BASE}/inbox`);
  await fa.getByText(requestId).first().waitFor();
  await shot(fa, '05-fa-inbox', false);
  await fa.locator('a[href^="/tasks/"]', { hasText: requestId }).click();
  await fa.getByText('Your decision').waitFor();
  await fa.locator('label', { hasText: 'Approve' }).first().click();
  await fa.fill('#remarks', 'Medical certificate and discharge summary cover the absence period.');
  await shot(fa, '06-fa-review');
  await fa.getByRole('button', { name: 'Approve request' }).click();
  await fa.waitForURL(/\/inbox/);

  // 7: Academic Advisor
  const aaTask = await findTask(tok.aa, requestId, 'REVIEW');
  const aa = await session('aa');
  await aa.goto(`${BASE}/tasks/${aaTask.taskKey}`);
  await aa.getByText('Earlier decisions').waitFor();
  await aa.locator('label', { hasText: 'Approve' }).first().click();
  await aa.fill('#remarks', 'Verified with the attendance register.');
  await shot(aa, '07-aa-review');
  await aa.getByRole('button', { name: 'Approve request' }).click();
  await aa.waitForURL(/\/inbox/);

  // 8: Head of Department
  const hodTask = await findTask(tok.hod, requestId, 'REVIEW');
  const hod = await session('hod');
  await hod.goto(`${BASE}/tasks/${hodTask.taskKey}`);
  await hod.getByText('Earlier decisions').waitFor();
  await hod.locator('label', { hasText: 'Approve' }).first().click();
  await hod.fill('#remarks', 'Approved under Slab A.');
  await shot(hod, '08-hod-review');
  await hod.getByRole('button', { name: 'Approve request' }).click();
  await hod.waitForURL(/\/inbox/);

  // 9-10: student pays and the request completes
  const payTask = await findTask(tok.RA2311004010103, requestId, 'PAY_FEE');
  await rahul.goto(`${BASE}/tasks/${payTask.taskKey}`);
  await rahul.selectOption('#paymentMode', 'UPI');
  await rahul.fill('#paymentReference', `UPI${Date.now().toString().slice(-10)}`);
  await rahul.getByLabel('I confirm that the payment details above are correct.').check();
  await shot(rahul, '09-pay-fee');
  await rahul.getByRole('button', { name: /Submit payment/ }).click();
  await rahul.waitForURL(/\/requests\/REQ-/);
  await waitStatus(tok.RA2311004010103, requestId, ['APPROVED']);
  await rahul.reload();
  await rahul.getByText('Condoned').first().waitFor();
  await shot(rahul, '10-route-slip-condoned');
  await rahul.goto(`${BASE}/requests`);
  await shot(rahul, '02-student-my-requests');

  // mobile view of the route slip
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true });
  const m = await mobile.newPage();
  await m.goto(`${BASE}/login`);
  await m.fill('#username', 'RA2311004010103');
  await m.fill('#password', PASSWORD);
  await m.click('button[type=submit]');
  await m.waitForURL((u) => !u.pathname.startsWith('/login'));
  await m.goto(`${BASE}/requests/${requestId}`);
  await m.getByText('Condoned').first().waitFor();
  await shot(m, '15-mobile-route-slip');

  // 11-12: SIS failure fixed by the admin
  await reviewViaApi(tok.fa, priya, 'Accident report and certificate verified.');
  await reviewViaApi(tok.aa, priya, 'OK');
  await reviewViaApi(tok.hod, priya, 'Approved under Slab C (medical).');
  const priyaPay = await findTask(tok.RA2311004010102, priya, 'PAY_FEE');
  await api(tok.RA2311004010102, `/tasks/${priyaPay.taskKey}/pay-fee`, { method: 'POST', body: { paymentMode: 'NET_BANKING', paymentReference: `NB${Date.now().toString().slice(-10)}`, amountPaid: 2000, paymentConfirmed: true } });
  const fixTask = await findTask(tok.admin, priya, 'ADMIN_FIX');
  const admin = await session('admin');
  await admin.goto(`${BASE}/inbox`);
  await shot(admin, '11a-admin-inbox', false);
  await admin.goto(`${BASE}/tasks/${fixTask.taskKey}`);
  await admin.fill('#adminNote', 'Unlocked the semester 5 course record in the SIS.');
  await admin.getByLabel('The SIS record is fixed.').check();
  await shot(admin, '11-admin-fix');
  await admin.getByRole('button', { name: 'Retry SIS update' }).click();
  await admin.waitForURL(/\/inbox/);
  await waitStatus(tok.admin, priya, ['APPROVED']);
  await admin.goto(`${BASE}/admin`);
  await admin.getByRole('heading', { name: 'SIS records' }).waitFor();
  await shot(admin, '12-admin-settings-sis');
  await admin.goto(`${BASE}/requests/${priya}`);
  await admin.getByText('Condoned').first().waitFor();
  await shot(admin, '12b-route-slip-sis-fixed');

  // 13-14: staff views and a rejection
  await hod.goto(`${BASE}/all`);
  await shot(hod, '13-all-requests');
  const all = await api(tok.hod, '/requests');
  for (const [stage, name] of [['DOCUMENTS', '14-route-slip-document-rejected'], ['ELIGIBILITY', '14b-route-slip-not-eligible'], ['FA_APPROVAL', '14c-route-slip-fa-rejected'], ['FEE', '14d-route-slip-payment-rejected']]) {
    for (const r of all.filter((x) => x.status === 'REJECTED')) {
      const d = await api(tok.hod, `/requests/${r.requestId}`);
      if (d.rejectionStage === stage) { await hod.goto(`${BASE}/requests/${r.requestId}`); await hod.getByText('Why the request was not condoned').first().waitFor(); await shot(hod, name); break; }
    }
  }
  fs.writeFileSync(path.join(OUT, 'walkthrough.json'), JSON.stringify({ rahul: requestId, arjun, priya, at: new Date().toISOString() }, null, 2));
  log('done');
} catch (e) {
  console.error('FAILED:', e.message);
  process.exitCode = 1;
} finally {
  await browser.close();
}
