import { APIConnectionError } from './errors.js';
import { createGroup } from './groups.js';
import { Registry } from './registry.js';
import { buildRequest, encodeQuery } from './request-builder.js';
import { buildError, parseSuccess } from './responses.js';
import { RetryPolicy } from './retry.js';
import { ConnectionFailure, FetchTransport } from './transport.js';

export * from './errors.js';

export const VERSION = '1.0.0';
const DEFAULT_BASE_URL = 'https://axioapi.com';
const DEFAULT_TIMEOUT_MS = 30000;

export class AxioAPI {
  /**
   * @param {string} [apiKey] defaults to process.env.AXIOAPI_KEY
   * @param {{baseUrl?: string, timeout?: number, maxRetries?: number, userAgent?: string, fetch?: typeof fetch, sleep?: (ms:number)=>Promise<void>}} [options]
   */
  constructor(apiKey, options = {}) {
    this.apiKey = apiKey || process.env.AXIOAPI_KEY;
    if (!this.apiKey) throw new Error('Pass an API key or set the AXIOAPI_KEY environment variable.');
    this.baseUrl = (options.baseUrl || process.env.AXIOAPI_BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
    this.userAgent = options.userAgent || `axioapi-node/${VERSION}`;
    this.registry = Registry.load();
    this.retry = new RetryPolicy(options.maxRetries ?? 2, options.sleep || ((ms) => new Promise((resolve) => setTimeout(resolve, ms))));
    this.transport = new FetchTransport(options.fetch || globalThis.fetch, options.timeout ?? DEFAULT_TIMEOUT_MS);
    return new Proxy(this, {
      get: (target, prop, receiver) => {
        if (typeof prop === 'string' && !(prop in target) && target.registry.hasGroup(prop)) return createGroup(target, prop);
        return Reflect.get(target, prop, receiver);
      },
    });
  }

  operations() {
    return this.registry.all();
  }

  /** Calls an operation by capability key (for example 'seo.keyword-metrics') and resolves with `data`. */
  async call(operation, params = {}) {
    const found = this.registry.find(operation);
    if (!found) throw new Error(`Unknown operation '${operation}'. See client.operations().`);
    const { method, path, query, body } = buildRequest(found, params);
    return this.request(method, path, { params: query, body });
  }

  /** Sends a request with retries; resolves with `data`, the envelope with {raw: true}, or a Buffer for files. */
  async request(method, path, { params, body, raw = false } = {}) {
    method = method.toUpperCase();
    const url = this._url(path, params);
    const payload = body !== undefined ? JSON.stringify(body) : undefined;
    const headers = this._headers(payload !== undefined);

    for (let attempt = 0; ; attempt++) {
      let response;
      try {
        response = await this.transport.send(method, url, headers, payload);
      } catch (failure) {
        if (!(failure instanceof ConnectionFailure)) throw failure;
        if (this.retry.shouldRetryConnection(method, attempt)) {
          await this.retry.wait(attempt);
          continue;
        }
        throw new APIConnectionError(`Could not reach ${this.baseUrl}: ${failure.message}`);
      }
      if (response.ok) return parseSuccess(response, raw);
      if (this.retry.shouldRetryStatus(response.status, method, attempt)) {
        await this.retry.wait(attempt, response.headers.get('retry-after'));
        continue;
      }
      throw buildError(response);
    }
  }

  _url(path, params) {
    const url = this.baseUrl + (path.startsWith('/') ? path : `/${path}`);
    return params && Object.keys(params).length ? `${url}?${encodeQuery(params)}` : url;
  }

  _headers(hasBody) {
    const headers = { Authorization: `Bearer ${this.apiKey}`, Accept: 'application/json', 'User-Agent': this.userAgent };
    if (hasBody) headers['Content-Type'] = 'application/json';
    return headers;
  }
}

export default AxioAPI;
