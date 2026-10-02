#!/usr/bin/env python3
"""Check javaagent enablement selectors in v3-preview mode.

This checks selector structure, not whether an independent feature needs a control.
Component families include their owning baseline except for the JDK instrumentations
listed below. Unsupported Java selector expressions are errors, not implicit exemptions.
Module classes may share all public names. Optional feature selectors preserve
independent controls; Muzzle identifies individual modules by fully qualified class.
Client/server role and independent feature selectors are versionless and follow the baseline.
Standalone default-off feature modules may use their directory name first, followed by
their versionless feature name.
Product umbrellas follow component selectors. Default-off features must not
share selectors with default-on instrumentation.
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
# These public feature controls are shared with other instrumentations.
SHARED_FEATURE_FAMILIES = {
    "jaxrs": {"cxf", "jersey", "resteasy"},
    "jaxws": {"axis2", "cxf", "metro"},
}
# Product ownership is explicit; directory nesting and shared prefixes are not sufficient.
UMBRELLA_SELECTORS = {
    "armeria-grpc": ["armeria"],
    "aws-lambda-core": ["aws-lambda"],
    "aws-lambda-events": ["aws-lambda"],
    "clickhouse-client-v1": ["clickhouse-client", "clickhouse"],
    "clickhouse-client-v2": ["clickhouse-client", "clickhouse"],
    "elasticsearch-api-client": ["elasticsearch"],
    "elasticsearch-rest": ["elasticsearch"],
    "elasticsearch-transport": ["elasticsearch"],
    "hibernate-procedure-call": ["hibernate"],
    "kafka-clients": ["kafka"],
    "kafka-connect": ["kafka"],
    "kafka-streams": ["kafka"],
    "liberty-dispatcher": ["liberty"],
    "mongo-async": ["mongo"],
    "openai-java": ["openai"],
    "opensearch-java": ["opensearch"],
    "opensearch-rest": ["opensearch"],
    "play-mvc": ["play"],
    "quarkus-resteasy-reactive": ["jaxrs", "quarkus"],
    "spring-boot-actuator-autoconfigure": ["micrometer"],
    "spring-cloud-gateway-webmvc": ["spring-cloud-gateway"],
    "vertx-http-client": ["vertx"],
    "vertx-kafka-client": ["vertx"],
    "vertx-redis-client": ["vertx"],
    "vertx-rx-java": ["vertx"],
    "vertx-sql-client": ["vertx"],
    "vertx-web": ["vertx"],
    "zio-http": ["zio"],
}
# These deprecated implementations disappear in 3.0 rather than becoming optional features.
REMOVED_IN_V3_BASELINES = {"jedis-1.4", "lettuce-5.1"}
# This generic Reactor Netty server support lives with the WebFlux tests that exercise it.
REACTOR_NETTY_SERVER_MODULE = Path(
    "spring", "spring-webflux", "spring-webflux-5.0", "javaagent", "src", "main", "java",
    "io", "opentelemetry", "javaagent", "instrumentation", "spring", "webflux", "v5_0",
    "server", "reactornetty", "ReactorNettyInstrumentationModule.java",
)
MODULE_SUPERCLASSES = ("InstrumentationModule", "V3PreviewFallbackEnabledInstrumentationModule")
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
            return [] if parts == [""] else parts
    raise ValueError("unterminated super(...) call")


def evaluate(expression):
    expression = expression.strip()
    literal = STRING.fullmatch(expression)
    if literal:
        return [literal.group(1)]
    # A module with no additional selectors still has to pass an empty varargs array.
    if re.fullmatch(r"new\s+String\[\s*0\s*\]", expression):
        return []
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
            if not match.group(1).strip():
                return []
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
    if parent in MODULE_SUPERCLASSES:
        return [name for arg in args for name in evaluate(arg)]
    base_path = path.with_name(parent + ".java")
    if not base_path.is_file():
        raise ValueError(f"unsupported InstrumentationModule superclass {parent}")
    base_source = base_path.read_text(encoding="utf-8")
    inherited = "|".join(MODULE_SUPERCLASSES)
    if not re.search(
        rf"\babstract\s+class\s+{parent}\s+extends\s+(?:{inherited})\b", without_comments(base_source)
    ):
        raise ValueError(f"unsupported InstrumentationModule superclass {parent}")
    parameter = re.search(
        rf"\b{parent}\s*\(\s*String\s*(\.\.\.)?\s+(\w+)\s*\)",
        without_comments(base_source),
    )
    if parameter is None or (parameter.group(1) is None and len(args) != 1):
        raise ValueError(f"unsupported {parent} constructor parameters")
    base_args = super_arguments(base_source)
    return [
        name
        for arg in base_args
        for expression in (args if arg == parameter.group(2) else [arg])
        for name in evaluate(expression)
    ]


def validate_feature_selectors(family, extras):
    for name in extras:
        versioned = VERSIONED.match(name)
        if (
            versioned
            and versioned.group(1) == family
            and name[versioned.end():] in {"client", "server"}
        ):
            raise ValueError(f"versioned role selectors are not supported: {name!r}")
    if f"{family}-client" in extras and f"{family}-server" in extras:
        raise ValueError("client and server role selectors must not be combined")
    for role in ("client", "server"):
        role_names = [f"{family}-{role}"]
        if role_names[0] in extras:
            if extras[:len(role_names)] != role_names:
                raise ValueError(f"expected role selectors {role_names}, found {extras}")
            extras = extras[len(role_names) :]
            break
    for name in extras:
        versioned = VERSIONED.match(name)
        if versioned and name.startswith(family + "-"):
            raise ValueError(f"versioned feature selectors are not supported: {name!r}")
        prefix = family + "-"
        if name.startswith(prefix) and KEBAB.fullmatch(name[len(prefix):]):
            continue
        shared_family = versioned.group(1) if versioned else name
        if shared_family in SHARED_FEATURE_FAMILIES.get(family, set()) and (
            versioned is None or not name[versioned.end():]
        ):
            continue
        raise ValueError(f"expected a feature selector of {family!r}, found {name!r}")


def evaluate_default(expression, inherited=True):
    expression = expression.strip()
    for operator in ("||", "&&"):
        parts = expression.split(operator)
        if len(parts) > 1:
            values = [evaluate_default(part, inherited) for part in parts]
            return any(values) if operator == "||" else all(values)
    if expression.startswith("!"):
        return not evaluate_default(expression[1:], inherited)
    # Classify the ordinary defaults with global enablement on and optional settings absent.
    values = {
        "true": True,
        "false": False,
        "super.defaultEnabled()": inherited,
        "AgentCommonConfig.get().isV3Preview()": True,
        "ExperimentalConfig.get().controllerTelemetryEnabled()": False,
        "AgentCommonConfig.get().getUserConfig().isAnyEnabled()": False,
    }
    if expression not in values:
        raise ValueError(f"unsupported default enablement expression: {expression}")
    return values[expression]


def default_enabled(path, source):
    source = without_comments(source)
    parent = re.search(r"\bclass\s+\w+\s+extends\s+(\w+)\b", source).group(1)
    inherited = True
    if parent not in MODULE_SUPERCLASSES:
        base_path = path.with_name(parent + ".java")
        inherited = default_enabled(base_path, base_path.read_text(encoding="utf-8"))
    method = re.search(r"\bboolean\s+defaultEnabled\s*\((.*?)\)\s*\{(.*?)\}", source, re.DOTALL)
    if method:
        result = re.fullmatch(r"\s*return\s+(.*?);\s*", method.group(2), re.DOTALL)
        if method.group(1).strip() or result is None:
            raise ValueError("unsupported defaultEnabled() method")
        return evaluate_default(result.group(1), inherited)
    return inherited


def check(root, modules=None):
    errors = []
    if modules is None:
        modules = list(module_files(root))
    defaults_by_selector = {}

    def report(path, source, error):
        match = re.search(r"\bsuper\s*\(", without_comments(source))
        line = source[: match.start()].count("\n") + 1 if match else 1
        errors.append(f"{path.relative_to(root)}:{line}: {error}")

    for path, source in modules:
        relative = path.relative_to(root)
        try:
            # InstrumentationModule stores the names in a LinkedHashSet.
            names = list(dict.fromkeys(selectors(path, source)))
            enabled = default_enabled(path, source)
            family, base = owning_names(relative)
            if relative == REACTOR_NETTY_SERVER_MODULE:
                required = [
                    "spring-webflux", "spring-webflux-5.0", "reactor-netty", "reactor-netty-server"
                ]
                if names != required:
                    raise ValueError(f"expected selectors {required}, found {names}")
            else:
                required = [family] + ([base] if base else [])
                if not enabled and names and names[0] != family:
                    # Independent default-off features use only their feature namespace.
                    module = relative.parts[relative.parts.index("javaagent") - 1]
                    if names[0] == module:
                        feature = re.sub(r"-\d+(?:\.\d+)*", "", module)
                        required = [module, feature]
                        if names[:2] != required:
                            raise ValueError(f"expected first selectors {required}, found {names}")
                        validate_feature_selectors(family, names[1:])
                    else:
                        validate_feature_selectors(family, names)
                else:
                    if names[: len(required)] != required:
                        raise ValueError(f"expected first selectors {required}, found {names}")
                    extras = names[len(required) :]
                    umbrellas = UMBRELLA_SELECTORS.get(family, [])
                    used = [name for name in extras if name in umbrellas]
                    if used:
                        if used != umbrellas or extras[-len(umbrellas):] != umbrellas:
                            raise ValueError(f"expected umbrella selectors {umbrellas} last, found {extras}")
                        extras = extras[:-len(umbrellas)]
                    validate_feature_selectors(family, extras)
                if any(not KEBAB.fullmatch(name) for name in names):
                    raise ValueError(f"selectors must use kebab-case: {names}")
            if base not in REMOVED_IN_V3_BASELINES:
                for name in names:
                    defaults_by_selector.setdefault(name, {}).setdefault(enabled, []).append((path, source))
        except ValueError as error:
            report(path, source, error)

    for name, defaults in defaults_by_selector.items():
        if len(defaults) > 1:
            for path, source in defaults[False]:
                report(path, source, f"default-off feature shares selector {name!r} with default-on instrumentation")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "root", type=Path, nargs="?", default=Path("instrumentation"), help="instrumentation root"
    )
    args = parser.parse_args()
    modules = list(module_files(args.root)) if args.root.is_dir() else []
    if not modules:
        print(f"No javaagent InstrumentationModule sources found in {args.root}", file=sys.stderr)
        return 1
    errors = check(args.root, modules)
    for error in errors:
        print(error)
    if errors:
        print(f"{len(errors)} javaagent selector violation(s)", file=sys.stderr)
        return 1
    print(f"Checked {len(modules)} javaagent modules: enablement selectors follow the naming convention")
    return 0


if __name__ == "__main__":
    sys.exit(main())
