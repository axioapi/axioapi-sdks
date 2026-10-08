import { readFileSync } from 'node:fs';
import {
  AxioAPIError, AuthenticationError, InsufficientCreditsError, NotFoundError,
  RateLimitError, ValidationError, APIConnectionError,
} from './errors.js';

export * from './errors.js';

export const VERSION = '1.0.0';
const DEFAULT_BASE_URL = 'https://axioapi.com';
const RETRY_STATUS = new Set([502, 503, 504]);
const OPERATIONS = JSON.parse(readFileSync(new URL('./operations.json', import.meta.url), 'utf8')).operations;

const normalize = (name) => String(name).toLowerCase().replace(/[^a-z0-9]/g, '');

function flatten(params) {
  const out = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    if (Array.isArray(value)) value.forEach((v) => out.append(`${key}[]`, String(v)));
    else out.append(key, typeof value === 'boolean' ? (value ? 'true' : 'false') : String(value));
  }
  return out.toString();
}

function delay(retryAfter, attempt) {
  if (retryAfter && /^\d+(\.\d+)?$/.test(retryAfter)) return Math.min(Number(retryAfter), 30) * 1000;
  return Math.min(500 * 2 ** attempt, 8000);
}

export class AxioAPI {
  /**
   * @param {string} [apiKey] defaults to process.env.AXIOAPI_KEY
   * @param {{baseUrl?: string, timeout?: number, maxRetries?: number, userAgent?: string, fetch?: typeof fetch, sleep?: (ms:number)=>Promise<void>}} [options]
   */
  constructor(apiKey, options = {}) {
    this.apiKey = apiKey || process.env.AXIOAPI_KEY;
    if (!this.apiKey) throw new Error('Pass an API key or set the AXIOAPI_KEY environment variable.');
    this.baseUrl = (options.baseUrl || process.env.AXIOAPI_BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
    this.timeout = options.timeout ?? 30000;
    this.maxRetries = Math.max(0, options.maxRetries ?? 2);
    this.userAgent = options.userAgent || `axioapi-node/${VERSION}`;
    this._fetch = options.fetch || globalThis.fetch;
    this._sleep = options.sleep || ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
    this._index = new Map();
    this._groups = new Set();
    for (const key of Object.keys(OPERATIONS)) {
      const dot = key.indexOf('.');
      const group = normalize(key.slice(0, dot));
      this._groups.add(group);
      this._index.set(`${group}.${normalize(key.slice(dot + 1))}`, key);
    }
    return new Proxy(this, {
      get: (target, prop, receiver) => {
        if (typeof prop === 'string' && !(prop in target) && target._groups.has(normalize(prop))) {
          return target._group(normalize(prop));
        }
        return Reflect.get(target, prop, receiver);
      },
    });
  }

  /** Registry of every operation: method, path, parameters and credit cost. */
  operations() {
    return { ...OPERATIONS };
  }

  _group(group) {
    return new Proxy({}, {
      get: (_, name) => {
        if (typeof name !== 'string' || name === 'then') return undefined;
        const key = this._index.get(`${group}.${normalize(name)}`);
        return key ? (params = {}) => this.call(key, params) : undefined;
      },
    });
  }

  /** Call an operation by capability key, e.g. call('seo.keyword-metrics', {keywords: [...]}). Resolves with `data`. */
  async call(operation, params = {}) {
    let key = OPERATIONS[operation] ? operation : null;
    if (!key) {
      const dot = operation.indexOf('.');
      key = dot > 0 ? this._index.get(`${normalize(operation.slice(0, dot))}.${normalize(operation.slice(dot + 1))}`) : null;
    }
    if (!key) throw new Error(`Unknown operation '${operation}'. See client.operations().`);
    const op = OPERATIONS[key];
    const rest = { ...params };
    let path = op.path;
    for (const name of op.path_params) {
      if (rest[name] === undefined) throw new Error(`Missing path parameter '${name}' for ${key}`);
      path = path.replace(`{${name}}`, encodeURIComponent(String(rest[name])));
      delete rest[name];
    }
    const hasBody = !['GET', 'DELETE', 'HEAD'].includes(op.method);
    const query = {};
    const body = {};
    for (const [name, value] of Object.entries(rest)) {
      if (value === undefined || value === null) continue;
      if (hasBody && (op.body.includes(name) || !op.query.includes(name))) body[name] = value;
      else query[name] = value;
    }
    return this.request(op.method, path, {
      params: Object.keys(query).length ? query : undefined,
      body: hasBody && Object.keys(body).length ? body : undefined,
    });
  }

  /** Send a request. Resolves with `data`, the whole envelope with {raw: true}, or a Buffer for file downloads. */
  async request(method, path, { params, body, raw = false } = {}) {
    method = method.toUpperCase();
    let url = this.baseUrl + (path.startsWith('/') ? path : `/${path}`);
    if (params && Object.keys(params).length) url += `?${flatten(params)}`;
    const headers = { Authorization: `Bearer ${this.apiKey}`, Accept: 'application/json', 'User-Agent': this.userAgent };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const idempotent = ['GET', 'HEAD', 'DELETE'].includes(method);

    for (let attempt = 0; ; attempt++) {
      let res;
      try {
        res = await this._fetch(url, {
          method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
          signal: AbortSignal.timeout(this.timeout),
        });
      } catch (err) {
        if (idempotent && attempt < this.maxRetries) {
          await this._sleep(delay(null, attempt));
          continue;
        }
        throw new APIConnectionError(`Could not reach ${this.baseUrl}: ${err.cause?.code || err.message}`);
      }
      if (res.ok) return this._parse(res, raw);
      const retryable = res.status === 429 || (idempotent && RETRY_STATUS.has(res.status));
      if (retryable && attempt < this.maxRetries) {
        await this._sleep(delay(res.headers.get('retry-after'), attempt));
        continue;
      }
      throw await this._error(res);
    }
  }

  async _parse(res, raw) {
    if (!(res.headers.get('content-type') || '').includes('json')) return Buffer.from(await res.arrayBuffer());
    const text = await res.text();
    if (!text) return null;
    const envelope = JSON.parse(text);
    return raw || typeof envelope !== 'object' || envelope === null ? envelope : envelope.data;
  }

  async _error(res) {
    let body = null;
    try { body = JSON.parse(await res.text()); } catch { /* not JSON */ }
    const err = body && typeof body.error === 'object' && body.error ? body.error : {};
    const message = err.message || body?.message || `HTTP ${res.status}`;
    const info = { status: res.status, code: err.code, requestId: err.request_id || res.headers.get('x-request-id'), fields: err.fields, body };
    switch (res.status) {
      case 401: return new AuthenticationError(message, info);
      case 402: return new InsufficientCreditsError(message, info);
      case 404: return new NotFoundError(message, info);
      case 422: return new ValidationError(message, info);
      case 429: {
        const retryAfter = res.headers.get('retry-after');
        return new RateLimitError(message, { ...info, retryAfter: retryAfter && /^\d+$/.test(retryAfter) ? Number(retryAfter) : undefined });
      }
      default: return new AxioAPIError(message, info);
    }
  }
}

export default AxioAPI;
