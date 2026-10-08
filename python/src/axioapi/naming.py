import re


def normalize(name: str) -> str:
    """Folds snake_case, camelCase and kebab-case to one comparable form."""
    return re.sub(r"[^a-z0-9]", "", name.lower())
