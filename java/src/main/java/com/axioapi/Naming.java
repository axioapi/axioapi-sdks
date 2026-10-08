package com.axioapi;

final class Naming {
    private Naming() {
    }

    /** Folds snake_case, camelCase and kebab-case to one comparable form. */
    static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
