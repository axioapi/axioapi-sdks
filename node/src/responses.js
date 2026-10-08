import {
  AxioAPIError, AuthenticationError, InsufficientCreditsError, NotFoundError, RateLimitError, ValidationError,
} from './errors.js';

const ERROR_BY_STATUS = {
  401: AuthenticationError,
  402: InsufficientCreditsError,
  404: NotFoundError,
  422: ValidationError,
};

/** Returns `data`, the whole envelope when raw, or a Buffer for non-JSON bodies (files). */
export function parseSuccess(response, raw) {
  if (!response.contentType.includes('json')) return response.body;
  if (response.body.length === 0) return null;
  const envelope = JSON.parse(response.body.toString('utf8'));
  return raw || typeof envelope !== 'object' || envelope === null ? envelope : envelope.data;
}

export function buildError(response) {
  const body = jsonOrNull(response.body);
  const detail = body && typeof body.error === 'object' && body.error ? body.error : {};
  const message = detail.message || body?.message || `HTTP ${response.status}`;
  const info = {
    status: response.status,
    code: detail.code,
    requestId: detail.request_id || response.headers.get('x-request-id'),
    fields: detail.fields,
    body,
  };
  if (response.status === 429) return new RateLimitError(message, { ...info, retryAfter: retryAfterSeconds(response) });
  const ErrorClass = ERROR_BY_STATUS[response.status] || AxioAPIError;
  return new ErrorClass(message, info);
}

function jsonOrNull(buffer) {
  try {
    return JSON.parse(buffer.toString('utf8'));
  } catch {
    return null;
  }
}

function retryAfterSeconds(response) {
  const value = response.headers.get('retry-after');
  return value && /^\d+$/.test(value) ? Number(value) : undefined;
}
