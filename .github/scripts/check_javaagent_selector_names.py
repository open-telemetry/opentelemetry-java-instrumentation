#!/usr/bin/env python3
"""Check javaagent enablement selectors in v3-preview mode.

This checks selector structure, not whether a component describes the right library.
Unversioned modules are limited to the JDK instrumentations listed below. A Java
expression the checker cannot evaluate is an error, not an implicit exemption.
Modules sharing just the family and base selectors are selected together; the
source alone cannot establish whether they need independent selection.
"""

import argparse
import re
import sys
from pathlib import Path


JDK_MODULES = {
    "executors",
    "http-url-connection",
    "java-http-client",
    "java-http-server",
    "java-util-logging",
    "jdbc",
    "rmi",
}
# Agent infrastructure is not an instrumentation of an external library.
INTERNAL_MODULES = {"external-annotations", "methods"}
VERSIONED = re.compile(r"^([a-z][a-z0-9-]*)-([0-9]+(?:\.[0-9]+)+)(?:-|$)")
KEBAB = re.compile(r"[a-z][a-z0-9]*(?:-[a-z0-9]+(?:\.[0-9]+)*)*")
STRING = re.compile(r'"([^"\\]*)"')
COMMENT_OR_STRING = re.compile(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/', re.DOTALL)


def without_comments(source):
    def replace(match):
        text = match.group()
        if text.startswith('"'):
            return text
        return re.sub(r"[^\n]", " ", text)

    return COMMENT_OR_STRING.sub(replace, source)


def split_top_level(expression, delimiter):
    parts = []
    start = 0
    depth = [0, 0, 0]
    quoted = False
    escaped = False
    for position, char in enumerate(expression):
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
            continue
        if char == '"':
            quoted = True
        elif char in "([{":
            depth["([{".index(char)] += 1
        elif char in ")]}":
            index = ")]}".index(char)
            depth[index] -= 1
            if depth[index] < 0:
                raise ValueError("unbalanced constructor expression")
        elif char == delimiter and not any(depth):
            parts.append(expression[start:position].strip())
            start = position + 1
    if quoted or any(depth):
        raise ValueError("unbalanced constructor expression")
    parts.append(expression[start:].strip())
    return parts


def super_arguments(source):
    source = without_comments(source)
    match = re.search(r"\bsuper\s*\(", source)
    if match is None:
        raise ValueError("InstrumentationModule constructor has no super(...) call")
    opening = match.end() - 1
    for ending in range(opening + 1, len(source)):
        try:
            parts = split_top_level(source[opening + 1 : ending], ",")
        except ValueError:
            continue
        # A balanced prefix could still be inside the call; only its closing ')' ends it.
        if source[ending] == ")":
            return parts
    raise ValueError("unterminated super(...) call")


def evaluate(expression):
    expression = expression.strip()
    literal = STRING.fullmatch(expression)
    if literal:
        return [literal.group(1)]
    branches = split_top_level(expression, "?")
    if len(branches) == 2:
        condition, alternatives = branches
        if condition != "AgentCommonConfig.get().isV3Preview()":
            raise ValueError(f"unsupported selector condition: {condition}")
        cases = split_top_level(alternatives, ":")
        if len(cases) != 2:
            raise ValueError(f"unsupported preview selector expression: {expression}")
        return evaluate(cases[0])
    for function in ("expandDeprecatedNames", "new String[]"):
        if function == "expandDeprecatedNames":
            match = re.fullmatch(r"expandDeprecatedNames\s*\((.*)\)", expression, re.DOTALL)
        else:
            match = re.fullmatch(r"new\s+String\[\]\s*\{(.*)\}", expression, re.DOTALL)
        if match:
            values = [
                value
                for part in split_top_level(match.group(1), ",")
                for value in evaluate(part)
            ]
            if function == "expandDeprecatedNames":
                # The deprecated half is registered only outside v3-preview.
                values = [value.split("|deprecated:", 1)[0] for value in values]
            return values
    raise ValueError(f"unsupported selector expression: {expression}")


def owning_names(path):
    parts = path.parts
    try:
        module_end = parts.index("javaagent")
    except ValueError as error:
        raise ValueError("not a javaagent module") from error
    leaf = parts[module_end - 1]
    for part in parts[:module_end]:
        match = VERSIONED.match(part)
        if match:
            family, version = match.groups()
            return family, f"{family}-{version}"
    if leaf not in JDK_MODULES:
        raise ValueError(f"versionless module {leaf!r} is not a JDK instrumentation")
    return leaf, None


def module_files(root):
    for path in sorted(root.rglob("*.java")):
        parts = path.relative_to(root).parts
        if not parts or parts[0] in {"internal", *INTERNAL_MODULES}:
            continue
        if "javaagent" not in parts:
            continue
        index = parts.index("javaagent")
        if parts[index : index + 4] != ("javaagent", "src", "main", "java"):
            continue
        source = path.read_text(encoding="utf-8")
        if re.search(r"@AutoService\s*\(\s*InstrumentationModule\.class\s*\)", without_comments(source)):
            yield path, source


def selectors(path, source):
    declaration = re.search(
        r"\bclass\s+\w+\s+extends\s+(\w+)\b", without_comments(source)
    )
    if declaration is None:
        raise ValueError("cannot determine InstrumentationModule superclass")
    parent = declaration.group(1)
    args = super_arguments(source)
    if parent in {"InstrumentationModule", "V3PreviewFallbackEnabledInstrumentationModule"}:
        return [name for arg in args for name in evaluate(arg)]
    base_path = path.with_name(parent + ".java")
    if not base_path.is_file():
        raise ValueError(f"unsupported InstrumentationModule superclass {parent}")
    base_source = base_path.read_text(encoding="utf-8")
    if not re.search(rf"\babstract\s+class\s+{parent}\s+extends\s+InstrumentationModule\b", without_comments(base_source)):
        raise ValueError(f"unsupported InstrumentationModule superclass {parent}")
    parameter = re.search(rf"\b{parent}\s*\(\s*String\s+(\w+)\s*\)", without_comments(base_source))
    if parameter is None or len(args) != 1:
        raise ValueError(f"unsupported {parent} constructor parameters")
    base_args = super_arguments(base_source)
    return [
        name
        for arg in base_args
        for name in evaluate(args[0] if arg == parameter.group(1) else arg)
    ]


def check(root):
    errors = []
    modules = list(module_files(root))

    for path, source in modules:
        relative = path.relative_to(root)
        try:
            family, base = owning_names(relative)
            # InstrumentationModule stores the names in a LinkedHashSet.
            names = list(dict.fromkeys(selectors(path, source)))
            required = [family] + ([base] if base else [])
            if names[: len(required)] != required:
                raise ValueError(f"expected first selectors {required}, found {names}")
            if any(not KEBAB.fullmatch(name) for name in names):
                raise ValueError(f"selectors must use kebab-case: {names}")
            extras = names[len(required) :]
            if len(extras) > 1:
                raise ValueError(f"expected at most one exact component selector, found {extras}")
            if extras:
                prefix = (base or family) + "-"
                component = extras[0].removeprefix(prefix)
                if not extras[0].startswith(prefix) or not KEBAB.fullmatch(component):
                    raise ValueError(
                        f"expected exact selector {prefix}<component>, found {extras[0]!r}"
                    )
        except ValueError as error:
            match = re.search(r"\bsuper\s*\(", source)
            line = source[: match.start()].count("\n") + 1 if match else 1
            errors.append(f"{relative}:{line}: {error}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "root", type=Path, nargs="?", default=Path("instrumentation"), help="instrumentation root"
    )
    args = parser.parse_args()
    if not args.root.is_dir() or not any(module_files(args.root)):
        print(f"No javaagent InstrumentationModule sources found in {args.root}", file=sys.stderr)
        return 1
    errors = check(args.root)
    for error in errors:
        print(error)
    if errors:
        print(f"{len(errors)} javaagent selector violation(s)", file=sys.stderr)
        return 1
    print("Javaagent enablement selectors follow the naming convention")
    return 0


if __name__ == "__main__":
    sys.exit(main())
