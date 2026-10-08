const BODYLESS_METHODS = ['GET', 'DELETE', 'HEAD'];

const hasBody = (operation) => !BODYLESS_METHODS.includes(operation.method);

const belongsInBody = (operation, name) => hasBody(operation) && (operation.body.includes(name) || !operation.query.includes(name));

function fillPath(operation, params) {
  let path = operation.path;
  for (const name of operation.path_params) {
    if (params[name] === undefined || params[name] === null) {
      throw new Error(`Missing path parameter '${name}' for ${operation.key}`);
    }
    path = path.replace(`{${name}}`, encodeURIComponent(String(params[name])));
    delete params[name];
  }
  return path;
}

/** Splits flat params into path, query and body according to the operation. */
export function buildRequest(operation, params) {
  const remaining = { ...params };
  const path = fillPath(operation, remaining);
  const query = {};
  const body = {};
  for (const [name, value] of Object.entries(remaining)) {
    if (value === undefined || value === null) continue;
    (belongsInBody(operation, name) ? body : query)[name] = value;
  }
  return {
    method: operation.method,
    path,
    query: Object.keys(query).length ? query : undefined,
    body: hasBody(operation) && Object.keys(body).length ? body : undefined,
  };
}

export function encodeQuery(params) {
  const out = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    if (Array.isArray(value)) value.forEach((item) => out.append(`${key}[]`, String(item)));
    else out.append(key, typeof value === 'boolean' ? (value ? 'true' : 'false') : String(value));
  }
  return out.toString();
}
