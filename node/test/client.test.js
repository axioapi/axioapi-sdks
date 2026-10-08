import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  AxioAPI, AxioAPIError, AuthenticationError, InsufficientCreditsError, NotFoundError,
  RateLimitError, ValidationError, APIConnectionError,
} from '../src/index.js';

const URL_ = process.env.AXIOAPI_MOCK_URL || 'http://127.0.0.1:8765';
const make = (opts = {}) => new AxioAPI('test_key', { baseUrl: URL_, sleep: async () => {}, ...opts });
const hits = async () => (await fetch(`${URL_}/__hits`)).json();

beforeEach(async () => { await fetch(`${URL_}/__reset`); });

test('requires a key', () => {
  delete process.env.AXIOAPI_KEY;
  assert.throws(() => new AxioAPI(undefined, { baseUrl: URL_ }));
});

test('group call sends JSON body and unwraps data', async () => {
  const data = await make().seo.keywordMetrics({ keywords: ['api gateway', 'proxy scraper'], country: 'us' });
  assert.equal(data.method, 'POST');
  assert.equal(data.path, '/api/v1/seo/keywords/metrics');
  assert.deepEqual(data.body, { keywords: ['api gateway', 'proxy scraper'], country: 'us' });
  assert.equal(data.content_type, 'application/json');
  assert.match(data.user_agent, /^axioapi-node\//);
});

test('snake_case and kebab-case names resolve too', async () => {
  const c = make();
  assert.equal((await c.seo.keyword_metrics({ keywords: ['a'] })).path, '/api/v1/seo/keywords/metrics');
  assert.equal((await c.call('seo.keyword-metrics', { keywords: ['a'] })).path, '/api/v1/seo/keywords/metrics');
});

test('path params are encoded and query is separated', async () => {
  const c = make();
  assert.equal((await c.call('seo.backlinks-summary', { domain: 'exa mple.com' })).path, '/api/v1/seo/domains/exa%20mple.com/backlinks');
  const data = await c.seo.keywordSuggestions({ q: 'laravel api', country: 'us', lang: 'en', skipped: undefined });
  assert.deepEqual(data.query, { q: ['laravel api'], country: ['us'], lang: ['en'] });
});

test('missing path param, unknown operation and group', async () => {
  const c = make();
  await assert.rejects(() => c.call('seo.backlinks-summary'), /Missing path parameter/);
  await assert.rejects(() => c.call('nope.nothing'), /Unknown operation/);
  assert.equal(c.seo.doesNotExist, undefined);
  assert.equal(c.nothing, undefined);
});

test('every registry operation is reachable by attribute', () => {
  const c = make();
  for (const key of Object.keys(c.operations())) {
    const [group, ...rest] = key.split('.');
    assert.equal(typeof c[group][rest.join('.')], 'function', key);
  }
});

test('raw envelope', async () => {
  const env = await make().request('GET', '/api/v1/account/limits', { raw: true });
  assert.equal(env.status, 'success');
  assert.equal(env.request.id, 'req_test_1');
});

test('binary download returns a Buffer', async () => {
  const body = await make().call('ai-image.artifact', { job: 'abc' });
  assert.ok(Buffer.isBuffer(body));
  assert.equal(body.subarray(1, 4).toString(), 'PNG');
});

test('errors map to classes', async () => {
  await assert.rejects(() => new AxioAPI('wrong', { baseUrl: URL_ }).account.limits(), (e) => {
    assert.ok(e instanceof AuthenticationError);
    assert.equal(e.status, 401);
    assert.equal(e.requestId, 'req_test_1');
    return true;
  });
  await assert.rejects(() => new AxioAPI('nocredit', { baseUrl: URL_ }).account.limits(), InsufficientCreditsError);
  await assert.rejects(() => make().request('GET', '/api/v1/temp-mail/inboxes/missing'), NotFoundError);
  await assert.rejects(() => make().seo.onPageAudit(), (e) => {
    assert.ok(e instanceof ValidationError);
    assert.deepEqual(e.fields, { url: ['The url field is required.'] });
    return true;
  });
  await assert.rejects(() => make().request('GET', '/api/v1/boom'), (e) => e instanceof AxioAPIError && e.status === 500);
});

test('retries idempotent 503 then succeeds', async () => {
  assert.equal((await make().request('GET', '/api/v1/flaky')).attempts, 3);
});

test('retries 429, gives up with retryAfter, POST 503 is not retried', async () => {
  assert.equal((await make().request('GET', '/api/v1/ratelimited')).attempts, 2);
  await assert.rejects(() => make({ maxRetries: 1 }).request('POST', '/api/v1/always429', { body: {} }), (e) => e instanceof RateLimitError && e.retryAfter === 7);
  assert.equal((await hits()).always429, 2);
  await assert.rejects(() => make().request('POST', '/api/v1/post503', { body: {} }), AxioAPIError);
  assert.equal((await hits()).post503, 1);
});

test('connection error', async () => {
  await assert.rejects(() => new AxioAPI('k', { baseUrl: 'http://127.0.0.1:1', maxRetries: 0, timeout: 2000 }).account.limits(), APIConnectionError);
});
