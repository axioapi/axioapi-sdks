import { readFileSync } from 'node:fs';
import { normalize } from './naming.js';

const OPERATIONS_URL = new URL('./operations.json', import.meta.url);

const indexKey = (group, name) => `${normalize(group)}.${normalize(name)}`;

const splitKey = (key) => {
  const dot = key.indexOf('.');
  return [key.slice(0, dot), key.slice(dot + 1)];
};

/** Every API operation, loaded from operations.json. */
export class Registry {
  constructor(operations) {
    this.operations = operations;
    this.index = new Map();
    this.groups = new Set();
    for (const key of Object.keys(operations)) {
      const [group, name] = splitKey(key);
      this.groups.add(normalize(group));
      this.index.set(indexKey(group, name), key);
    }
  }

  static load() {
    const { operations } = JSON.parse(readFileSync(OPERATIONS_URL, 'utf8'));
    return new Registry(Object.fromEntries(Object.entries(operations).map(([key, op]) => [key, { key, ...op }])));
  }

  all() {
    return { ...this.operations };
  }

  hasGroup(group) {
    return this.groups.has(normalize(group));
  }

  /** Capability key for a group and operation name in any spelling, or undefined. */
  resolve(group, name) {
    return this.index.get(indexKey(group, name));
  }

  /** Looks an operation up by exact key or by any spelling of group.name. */
  find(operation) {
    if (this.operations[operation]) return this.operations[operation];
    if (!operation.includes('.')) return undefined;
    const [group, name] = splitKey(operation);
    const key = this.resolve(group, name);
    return key ? this.operations[key] : undefined;
  }
}
