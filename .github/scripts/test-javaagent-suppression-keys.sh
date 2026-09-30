#!/bin/bash -e

script_dir=$(cd "$(dirname "$0")" && pwd)
fixtures=$(mktemp -d)
trap 'rm -rf "$fixtures"' EXIT
module="$fixtures/foo/foo-1.0/javaagent/src/main/java/FooModule.java"
mkdir -p "$(dirname "$module")"

check() {
  "$script_dir/check-javaagent-suppression-keys.sh" "$fixtures" >/dev/null
}

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super("foo", AgentCommonConfig.get().isV3Preview()
        ? new String[] {"foo-1.0", "foo-1.0-core"}
        : new String[] {"foo-1.0"});
  }
}
EOF
check

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super(AgentCommonConfig.get().isV3Preview() ? "foo" : "foo-legacy",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"foo-1.0", "foo-1.0-client"}
            : new String[] {"foo-legacy-1.0"});
  }
}
EOF
check

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super(AgentCommonConfig.get().isV3Preview() ? "foo" : "foo-legacy", "foo-1.0");
  }
}
EOF
check

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super("foo", AgentCommonConfig.get().isV3Preview()
        ? new String[] {"foo-other"}
        : new String[] {"foo-1.0"});
  }
}
EOF
if check; then
  echo "Checker accepted the wrong preview version" >&2
  exit 1
fi

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super("foo", AgentCommonConfig.get().isV3Preview()
        ? new String[] {"foo-other", "foo-1.0"}
        : new String[] {"foo-legacy-1.0"});
  }
}
EOF
if check; then
  echo "Checker accepted the expected version as a later preview alias" >&2
  exit 1
fi

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super("foo", AgentCommonConfig.get().isV3Preview()
        ? new String[] {"foo-1x0"}
        : new String[] {"foo-legacy-1.0"});
  }
}
EOF
if check; then
  echo "Checker accepted a version differing at a regex metacharacter" >&2
  exit 1
fi

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super("bar", AgentCommonConfig.get().isV3Preview()
        ? new String[] {"foo-1.0"}
        : new String[] {"foo-legacy-1.0"});
  }
}
EOF
if check; then
  echo "Checker accepted the wrong preview owner" >&2
  exit 1
fi

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super(AgentCommonConfig.get().isV3Preview() ? "bar" : "foo",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"foo-1.0"}
            : new String[] {"foo-legacy-1.0"});
  }
}
EOF
if check; then
  echo "Checker accepted the wrong conditional preview owner" >&2
  exit 1
fi

cat >"$module" <<'EOF'
class FooModule extends InstrumentationModule {
  FooModule() {
    super("foo-legacy", "foo-legacy-1.0");
    AgentCommonConfig.get().isV3Preview();
  }
}
EOF
if check; then
  echo "Checker accepted an unrelated preview mention" >&2
  exit 1
fi
