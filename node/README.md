# axioapi for Node.js

Node.js client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Zero dependencies, ESM, TypeScript types included. Requires Node.js 18+.

```bash
npm install axioapi
export AXIOAPI_KEY=ak_...
```

```js
import { AxioAPI, RateLimitError, InsufficientCreditsError } from 'axioapi';

const client = new AxioAPI(); // or new AxioAPI('ak_...')

// Keyword data API: volume, CPC and competition for up to 10 keywords per request
const rows = await client.seo.keywordMetrics({ keywords: ['api gateway', 'proxy scraper'], country: 'us' });

// Backlink API: summary with every data source that answered
const backlinks = await client.seo.backlinksSummary({ domain: 'example.com' });
console.log(backlinks.partial, backlinks.missing, Object.keys(backlinks.sources));

// Temp mail API: one inbox per test
const inbox = await client.temporaryEmail.createInbox({ ttl_minutes: 10 });

try {
  await client.verify.wait({ number: '+12025550192' });
} catch (e) {
  if (e instanceof RateLimitError) console.log('retry in', e.retryAfter, 'request', e.requestId);
  else if (e instanceof InsufficientCreditsError) console.log('top up credits');
  else throw e;
}
```

- `client.<group>.<operation>(params)` for every endpoint (camelCase, snake_case or kebab-case); `client.call('seo.keyword-metrics', params)` works by capability key. `client.operations()` lists all of them with method, path and credit cost.
- Resolves with the `data` field. `client.request('GET', '/api/v1/account/limits', { raw: true })` resolves with the whole envelope.
- Image and audio endpoints resolve with a `Buffer`.
- Options: `new AxioAPI(key, { baseUrl, timeout: 30000, maxRetries: 2, fetch })`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT
