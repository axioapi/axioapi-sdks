export class AxioAPIError extends Error {
  /** @param {string} message @param {{status?: number, code?: string, requestId?: string, fields?: object, body?: any}} [info] */
  constructor(message, info = {}) {
    super(message);
    this.name = this.constructor.name;
    this.status = info.status;
    this.code = info.code;
    this.requestId = info.requestId;
    this.fields = info.fields || {};
    this.body = info.body;
  }
}

export class AuthenticationError extends AxioAPIError {}
export class InsufficientCreditsError extends AxioAPIError {}
export class NotFoundError extends AxioAPIError {}
export class ValidationError extends AxioAPIError {}
export class APIConnectionError extends AxioAPIError {}

export class RateLimitError extends AxioAPIError {
  constructor(message, info = {}) {
    super(message, info);
    this.retryAfter = info.retryAfter;
  }
}
