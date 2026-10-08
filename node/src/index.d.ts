export const VERSION: string;

export interface ClientOptions {
  baseUrl?: string;
  /** Milliseconds. Default 30000. */
  timeout?: number;
  /** Retries for 429 and, on GET/DELETE, 502/503/504 and network errors. Default 2. */
  maxRetries?: number;
  userAgent?: string;
  fetch?: typeof fetch;
  sleep?: (ms: number) => Promise<void>;
}

export interface OperationInfo {
  method: string;
  path: string;
  summary: string;
  cost: number | null;
  path_params: string[];
  query: string[];
  body: string[];
  binary: boolean;
  auth: boolean;
}

export interface Envelope<T = unknown> {
  status: string;
  data: T;
  request: { id: string; method: string; path: string; input: Record<string, unknown> };
  error?: { code: string; message: string; request_id: string; fields?: Record<string, string[]> };
}

export type OperationFn = (params?: Record<string, unknown>) => Promise<any>;
export type OperationGroup = { [operation: string]: OperationFn };

export class AxioAPI {
  constructor(apiKey?: string, options?: ClientOptions);
  /** Operation groups, for example client.seo.keywordMetrics({ keywords: ['api gateway'], country: 'us' }). */
  [group: string]: any;
  operations(): Record<string, OperationInfo>;
  call<T = any>(operation: string, params?: Record<string, unknown>): Promise<T>;
  request<T = any>(method: string, path: string, options?: { params?: Record<string, unknown>; body?: unknown; raw?: boolean }): Promise<T>;
}

export class AxioAPIError extends Error {
  status?: number;
  code?: string;
  requestId?: string;
  fields: Record<string, string[]>;
  body?: unknown;
}
export class AuthenticationError extends AxioAPIError {}
export class InsufficientCreditsError extends AxioAPIError {}
export class NotFoundError extends AxioAPIError {}
export class ValidationError extends AxioAPIError {}
export class APIConnectionError extends AxioAPIError {}
export class RateLimitError extends AxioAPIError {
  retryAfter?: number;
}
export default AxioAPI;
