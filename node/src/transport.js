/** Thrown when a request produced no HTTP response. */
export class ConnectionFailure extends Error {}

/** One HTTP round trip with fetch; HTTP error statuses are returned, not thrown. */
export class FetchTransport {
  constructor(fetchImpl, timeoutMs) {
    this.fetch = fetchImpl;
    this.timeoutMs = timeoutMs;
  }

  async send(method, url, headers, payload) {
    let response;
    try {
      response = await this.fetch(url, { method, headers, body: payload, signal: AbortSignal.timeout(this.timeoutMs) });
    } catch (error) {
      throw new ConnectionFailure(error.cause?.code || error.message);
    }
    return {
      status: response.status,
      ok: response.ok,
      headers: response.headers,
      contentType: response.headers.get('content-type') || '',
      body: Buffer.from(await response.arrayBuffer()),
    };
  }
}
