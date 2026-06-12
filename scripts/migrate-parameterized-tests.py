#!/usr/bin/env python3
"""
Migrates @ParameterizedTest(name = "{0}") + String-name-first-param pattern
to Arguments.argumentSet() pattern.

For each test method with @ParameterizedTest(name = "{0}") + @MethodSource:
1. Changes annotation to @ParameterizedTest
2. Removes the first String parameter from the test method signature
3. Changes Arguments.of(STRING_LIT, ...) and arguments(STRING_LIT, ...)
   to Arguments.argumentSet(STRING_LIT, ...) inside the provider method
4. Removes the static import for Arguments.arguments if no longer used
"""

import re
import sys
import argparse
from pathlib import Path


# ---------------------------------------------------------------------------
# Brace / paren matching helpers
# ---------------------------------------------------------------------------

def _skip_string(text, pos):
    """Assuming text[pos] == '"', return index past the closing '"'."""
    i = pos + 1
    while i < len(text):
        c = text[i]
        if c == '\\':
            i += 2
            continue
        if c == '"':
            return i + 1
        i += 1
    return i


def _skip_char_literal(text, pos):
    """Assuming text[pos] == "'", return index past the closing "'"."""
    i = pos + 1
    while i < len(text):
        c = text[i]
        if c == '\\':
            i += 2
            continue
        if c == "'":
            return i + 1
        i += 1
    return i


def matching_brace(text, open_pos):
    """Given position of '{', return index just past the matching '}'."""
    assert text[open_pos] == '{'
    depth = 1
    i = open_pos + 1
    while i < len(text) and depth > 0:
        c = text[i]
        if c == '"':
            i = _skip_string(text, i)
        elif c == "'":
            i = _skip_char_literal(text, i)
        elif c == '{':
            depth += 1
            i += 1
        elif c == '}':
            depth -= 1
            i += 1
        else:
            i += 1
    return i


def matching_paren(text, open_pos):
    """Given position of '(', return index just past the matching ')'."""
    assert text[open_pos] == '('
    depth = 1
    i = open_pos + 1
    while i < len(text) and depth > 0:
        c = text[i]
        if c == '"':
            i = _skip_string(text, i)
        elif c == "'":
            i = _skip_char_literal(text, i)
        elif c == '(':
            depth += 1
            i += 1
        elif c == ')':
            depth -= 1
            i += 1
        else:
            i += 1
    return i


# ---------------------------------------------------------------------------
# Find provider method body
# ---------------------------------------------------------------------------

def find_provider_body(text, provider_name):
    """Return (brace_start, brace_end) of provider method body, or (None, None)."""
    pattern = re.compile(rf'\b{re.escape(provider_name)}\s*\(')
    for match in pattern.finditer(text):
        pos = match.start()

        # Look back to start of the line (and one previous line) for return type
        line_start = text.rfind('\n', 0, pos) + 1
        prev_end = line_start - 1 if line_start > 0 else 0
        prev_start = text.rfind('\n', 0, prev_end) + 1
        context = text[prev_start:pos]

        # Must return Stream<Arguments> or List<Arguments>
        if not re.search(r'(Stream|List)\s*<\s*Arguments', context):
            continue

        # Find matching ) then first {
        paren_end = matching_paren(text, match.end() - 1)
        rest = text[paren_end:]
        brace_rel = rest.find('{')
        if brace_rel == -1:
            continue
        # Ensure no ; before { (would be abstract/interface)
        if ';' in rest[:brace_rel]:
            continue
        brace_start = paren_end + brace_rel
        brace_end = matching_brace(text, brace_start)
        return brace_start, brace_end

    return None, None


# ---------------------------------------------------------------------------
# Transform provider method
# ---------------------------------------------------------------------------

def transform_provider(text, provider_name):
    start, end = find_provider_body(text, provider_name)
    if start is None:
        print(f'  WARNING: provider method not found: {provider_name!r}')
        return text

    segment = text[start:end]

    # Arguments.of(WHITESPACE? STRING_LIT → Arguments.argumentSet(WHITESPACE? STRING_LIT
    segment = re.sub(
        r'Arguments\.of\((\s*)("(?:[^"\\]|\\.)*")',
        r'Arguments.argumentSet(\1\2',
        segment,
    )

    # arguments(WHITESPACE? STRING_LIT → Arguments.argumentSet(WHITESPACE? STRING_LIT
    # (static-import form)
    segment = re.sub(
        r'(?<!\.)(\barguments)\((\s*)("(?:[^"\\]|\\.)*")',
        r'Arguments.argumentSet(\2\3',
        segment,
    )

    if segment != text[start:end]:
        print(f'  Transformed provider: {provider_name!r}')

    return text[:start] + segment + text[end:]


# ---------------------------------------------------------------------------
# Remove first String param from test method
# ---------------------------------------------------------------------------

def remove_first_string_param(text, method_name):
    """Remove the leading String parameter (possibly annotated) from a method."""
    # Find: void methodName( or: ) methodName( for return-type-on-prev-line cases
    pattern = re.compile(rf'\bvoid\s+{re.escape(method_name)}\s*\(')
    match = pattern.search(text)
    if not match:
        print(f'  WARNING: test method not found: {method_name!r}')
        return text

    paren_start = text.index('(', match.start())
    paren_end = matching_paren(text, paren_start)  # one past ')'
    params = text[paren_start + 1 : paren_end - 1]

    # Remove first param: optional @Annotation(...)  String varname  optional comma+whitespace
    first_param_re = re.compile(
        r'^(\s*)'                              # leading whitespace (preserve indent)
        r'(?:@\w+(?:\s*\([^)]*\))?\s+)*'      # zero or more annotations
        r'String\s+\w+\s*'                    # String varname
        r',?\s*',                              # optional comma + trailing space/newline
        re.DOTALL,
    )
    new_params, n = first_param_re.subn(r'\1', params, count=1)
    if n == 0:
        print(f'  WARNING: String param not found in: {method_name!r}')
        return text

    print(f'  Removed String param from: {method_name!r}')
    return text[:paren_start + 1] + new_params + text[paren_end - 1:]


# ---------------------------------------------------------------------------
# Parse which tests use the old pattern
# ---------------------------------------------------------------------------

def parse_old_pattern_tests(text):
    """Return list of (annotation_pos, provider_name, test_method_name).

    annotation_pos is the start position of the @ParameterizedTest(...) match so
    we can do targeted (not global) annotation replacement.
    """
    results = []
    old_annotation = re.compile(r'@ParameterizedTest\s*\(\s*name\s*=\s*"\{0\}"\s*\)')
    for ann_match in old_annotation.finditer(text):
        snippet = text[ann_match.end() : ann_match.end() + 600]

        # Must have @MethodSource (not @CsvSource etc.)
        ms_match = re.search(r'@MethodSource(?:\s*\(\s*"([^"]*)"\s*\))?', snippet)
        if not ms_match:
            continue

        provider_arg = ms_match.group(1)  # None for bare @MethodSource

        # Find the test method name
        method_match = re.search(r'\bvoid\s+(\w+)\s*\(', snippet)
        if not method_match:
            continue

        test_name = method_match.group(1)
        provider_name = provider_arg if provider_arg else test_name
        results.append((ann_match.start(), ann_match.end(), provider_name, test_name))

    return results


# ---------------------------------------------------------------------------
# Main file transformation
# ---------------------------------------------------------------------------

def transform_file(path, dry_run):
    text = path.read_text(encoding='utf-8')
    original = text

    tests = parse_old_pattern_tests(text)
    if not tests:
        return False

    print(f'\n{path}')
    for _a, _b, provider, test in tests:
        print(f'  Found: test={test!r}  provider={provider!r}')

    # 1. Replace only the @ParameterizedTest(name="{0}") annotations that have @MethodSource.
    # Work backwards so character positions stay valid.
    for ann_start, ann_end, _provider, _test in reversed(tests):
        text = text[:ann_start] + '@ParameterizedTest' + text[ann_end:]

    # 2. Remove first String param from each test method
    for _a, _b, _provider, test_name in tests:
        text = remove_first_string_param(text, test_name)

    # 3. Transform provider methods (deduplicated)
    seen = set()
    for _a, _b, provider_name, _test in tests:
        if provider_name not in seen:
            seen.add(provider_name)
            text = transform_provider(text, provider_name)

    # 4. Remove static import for Arguments.arguments if now unused
    static_import = 'import static org.junit.jupiter.params.provider.Arguments.arguments;\n'
    if static_import in text:
        # Look for bare arguments( not preceded by '.'
        if not re.search(r'(?<!\.)(\barguments\()', text):
            text = text.replace(static_import, '')
            print('  Removed static import for Arguments.arguments')

    if text == original:
        print('  No changes')
        return False

    if dry_run:
        import difflib
        diff = difflib.unified_diff(
            original.splitlines(keepends=True),
            text.splitlines(keepends=True),
            fromfile=str(path),
            tofile=str(path),
            n=2,
        )
        sys.stdout.writelines(diff)
    else:
        path.write_text(text, encoding='utf-8')
        print('  Written')

    return True


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='Print diff, do not write')
    parser.add_argument('root', nargs='?', default='.', help='Root directory to scan')
    args = parser.parse_args()

    root = Path(args.root)
    target_files = [
        p for p in root.rglob('*.java')
        if '@ParameterizedTest(name = "{0}")' in p.read_text(encoding='utf-8', errors='ignore')
    ]

    print(f'Found {len(target_files)} file(s) with old pattern')
    changed = sum(transform_file(p, args.dry_run) for p in sorted(target_files))
    print(f'\nDone: {changed} file(s) {"would be " if args.dry_run else ""}changed')


if __name__ == '__main__':
    main()
