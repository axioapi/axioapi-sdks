/** Folds snake_case, camelCase and kebab-case to one comparable form. */
export const normalize = (name) => String(name).toLowerCase().replace(/[^a-z0-9]/g, '');
