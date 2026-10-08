/** `client.seo`: property access to the operations of one API group. */
export function createGroup(client, group) {
  return new Proxy({}, {
    get: (_, name) => {
      if (typeof name !== 'string' || name === 'then') return undefined;
      const key = client.registry.resolve(group, name);
      return key ? (params = {}) => client.call(key, params) : undefined;
    },
  });
}
