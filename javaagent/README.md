# Java Agent for Auto Instrumentation

## Investigating custom system loader startup (issue #10525)

[Issue #10525](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10525)
reports `IllegalStateException: agent initializer should be loaded in boot loader`
with WebLogic 14.1.1, Oracle Linux 8, and JDK 11.0.15.1. The title names agent
1.32.1, while the version field says `v1.3.2`; the exact original artifact needs
confirmation. The reporter configured a managed server's `ServerStart` arguments
and started it through Node Manager. Exporting `USE_JVM_SYSTEM_LOADER=true` and
restarting Node Manager, AdminServer, and the managed server avoided the error;
unsetting it and repeating restored it. A maintainer could not reproduce with
either `startWebLogic.sh` or a managed server.

`CustomSystemClassLoaderTest` exercises actual `-javaagent` startup in child JVMs.
Its standalone fixtures have no dependencies beyond the JDK:

- `ChildFirstSystemClassLoader` implements the JVM's
  `appendToClassPathForInstrumentation` hook with a `URLClassLoader`. It searches
  appended JARs before its parent for `io.opentelemetry.javaagent.*` classes.
- The JVM loads `OpenTelemetryAgent` through this system loader and calls
  `premain`. The agent appends its JAR to bootstrap search, but the defining
  loader's child-first lookup still defines `AgentInitializer` itself. Appending
  to bootstrap search does not override that lookup policy.
- `AgentStartupProbe` runs afterward and observes loader identity and
  `isAgentStarted(true)`; it never calls `initialize`. A separate bootstrap lookup
  confirms that the initializer is available there, but is a different class.

The normal system loader and the same custom loader with
`-Drepro.parent-first=true` are successful controls. In the latter,
`OpenTelemetryAgent` is still defined by the custom loader, but subsequent
initializer lookup delegates to bootstrap.

This is a synthetic reproduction of a sufficient loader behavior, **not a
reproduction of WebLogic's implementation or proof of its root cause**. No
WebLogic binaries are included or required. PR #20001 covers an agent JAR already
on the bootstrap path, a different scenario; these commands do not use
`-Xbootclasspath/a`.

### Run the focused source-agent test

Use JDK 25 for Gradle, as required by the repository. Set `REPO` to the absolute
checkout path:

```sh
REPO=/absolute/path/to/opentelemetry-java-instrumentation
"$REPO/gradlew" -p "$REPO" :javaagent:test \
  --tests io.opentelemetry.javaagent.CustomSystemClassLoaderTest \
  :javaagent:spotlessJavaCheck
# Also run the child JVMs on JDK 11:
"$REPO/gradlew" -p "$REPO" :javaagent:test \
  --tests io.opentelemetry.javaagent.CustomSystemClassLoaderTest \
  --tests io.opentelemetry.javaagent.AgentLoadedIntoBootstrapTest \
  -PtestJavaVersion=11 :javaagent:spotlessCheck :javaagent:checkstyleTest
```

The test asserts the exact exception and `initialize` / `startAgent` / `premain`
stack frames, not just arbitrary error output. Both the failure and the controls
must exit **0** and complete application `main`: `premain` catches the exception,
so a successful process exit alone does not mean the agent started.

### Run standalone with a released or source-built agent

Choose a JDK 11+ installation and compile the two dependency-free fixtures.
Download only the public agent release (2.33.0 was latest on 2026-10-23):

```sh
JAVA_HOME=/absolute/path/to/jdk-11
WORK=$(mktemp -d)
"$JAVA_HOME/bin/javac" --release 8 -d "$WORK/classes" \
  "$REPO/javaagent/src/test/java/jvmbootstraptest/ChildFirstSystemClassLoader.java" \
  "$REPO/javaagent/src/test/java/jvmbootstraptest/AgentStartupProbe.java"
curl -fL https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.0/opentelemetry-javaagent.jar \
  -o "$WORK/opentelemetry-javaagent-2.33.0.jar"
AGENT="$WORK/opentelemetry-javaagent-2.33.0.jar"
echo "5f9d1f10056ed2b246fbb19543519b8066cda23d6aff19022dbdb84402d6c3ab  $AGENT" | sha256sum -c - || exit 1
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/java" -jar "$AGENT"

for mode in normal parent-first child-first; do
  (
    set --
    if [ "$mode" != normal ]; then
      set -- -Djava.system.class.loader=jvmbootstraptest.ChildFirstSystemClassLoader
    fi
    if [ "$mode" = parent-first ]; then
      set -- "$@" -Drepro.parent-first=true
    fi
    "$JAVA_HOME/bin/java" "$@" \
      -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
      "-javaagent:$AGENT" -cp "$WORK/classes" jvmbootstraptest.AgentStartupProbe \
      > "$WORK/$mode.log" 2>&1
    status=$?
    cat "$WORK/$mode.log"
    echo "$mode exit=$status"
    test "$status" -eq 0 || exit 1
    grep -Fxq 'application-main-completed' "$WORK/$mode.log" || exit 1
    if [ "$mode" = child-first ]; then
      grep -Fxq 'java.lang.IllegalStateException: agent initializer should be loaded in boot loader' \
        "$WORK/$mode.log" || exit 1
      grep -Fxq 'initializer-loader=jvmbootstraptest.ChildFirstSystemClassLoader' \
        "$WORK/$mode.log" || exit 1
      started=false
    else
      if grep -Fq 'ERROR io.opentelemetry.javaagent.OpenTelemetryAgent' "$WORK/$mode.log"; then
        exit 1
      fi
      grep -Fxq 'initializer-loader=bootstrap' "$WORK/$mode.log" || exit 1
      started=true
    fi
    grep -Fxq "agent-started=$started" "$WORK/$mode.log" || exit 1
    grep -Fxq "same-bootstrap-initializer=$started" "$WORK/$mode.log" || exit 1
  ) || exit 1
done
```

Exporters are disabled to make startup independent of a collector. For a
source-built agent, build with JDK 25 using
`JAVA_HOME=/absolute/path/to/jdk-25 "$REPO/gradlew" -p "$REPO" :javaagent:shadowJar`, set
`AGENT="$REPO/javaagent/build/libs/opentelemetry-javaagent-3.0.0-SNAPSHOT.jar"`,
and repeat the loop. The JUnit test above provides automated output assertions.

Observed on Ubuntu 24.04.5 with Temurin 11.0.32.1+1 and 25.0.4.1+1-LTS:

| Agent | Normal loader | Custom parent-first | Custom child-first |
| --- | --- | --- | --- |
| Released 2.33.0 (SHA-256 above) | Started | Started | Exact reported exception; not started |
| Source-built 3.0.0-SNAPSHOT | Started | Started | Exact reported exception; not started |

The source build used production sources from `main` commit
`fa6160741fc71a196de38ad53020a050aba2002a`, without production changes.
Its SHA-256 was
`35515d45a66162384f7928ef0712b4390fb926bf7667a7548c1e5d77ee0f95e4`.
All three new JUnit tests passed on both JDKs; all three existing
`AgentLoadedIntoBootstrapTest` tests also passed on JDK 11. Spotless and
`:javaagent:checkstyleTest` passed. The released agent was checked standalone
in all three modes on both JDKs, asserting the exception, probe output, and exit code.

In both controls, output includes `initializer-loader=bootstrap`,
`agent-started=true`, and `same-bootstrap-initializer=true`, with no agent startup
error. Child-first output includes:

```text
ERROR io.opentelemetry.javaagent.OpenTelemetryAgent
java.lang.IllegalStateException: agent initializer should be loaded in boot loader
    at io.opentelemetry.javaagent.bootstrap.AgentInitializer.initialize(...)
    at io.opentelemetry.javaagent.OpenTelemetryAgent.startAgent(...)
    at io.opentelemetry.javaagent.OpenTelemetryAgent.premain(...)
...
premain-loader=jvmbootstraptest.ChildFirstSystemClassLoader
initializer-loader=jvmbootstraptest.ChildFirstSystemClassLoader
agent-started=false
same-bootstrap-initializer=false
application-main-completed
```

### Confirmation needed in the original environment

WebLogic, Oracle Linux 8, and Oracle JDK 11.0.15.1 were not available for this
investigation. With a licensed WebLogic 14.1.1 installation:

1. Record the WebLogic patch level, OS, full `java -version`, agent manifest version
   and SHA-256, Node Manager startup configuration (including script-based versus
   direct Java startup), and the actual managed-server JVM command. Confirm the
   original artifact version rather than relying on the inconsistent issue fields.
2. With `USE_JVM_SYSTEM_LOADER` unset in the launching environment, stop and restart
   Node Manager and AdminServer so they inherit that environment. In the Admin
   Console, edit the target managed server's **Configuration → Server Start →
   Arguments**, or use WLST at
   `/Servers/<server>/ServerStart/<server>`. Preserve existing arguments and append
   `-javaagent:/absolute/path/to/opentelemetry-javaagent.jar`,
   `-Dotel.exporter.otlp.endpoint=http://<collector>:4317`, and
   `-Dotel.resource.attributes=service.name=<server>`. Save and activate.
3. Start that managed server through Node Manager, not just `startWebLogic.sh`.
   Capture Node Manager and server logs from JVM launch onward, including the
   exact exception stack and evidence of whether agent initialization completed.
   Record `java.system.class.loader` and class-loading provenance; on JDK 11,
   `-Xlog:class+load=debug:file=/absolute/path/to/classes.log` can help distinguish
   the defining loaders of `OpenTelemetryAgent` and `AgentInitializer`.
4. Export `USE_JVM_SYSTEM_LOADER=true` in the environment used to launch the
   processes, then fully restart Node Manager, AdminServer, and the managed server,
   in that order. Repeat with the variable unset, restarting all three again.
   Verify the actual JVM arguments/loader change, not just the shell variable.
5. Repeat each state with a fresh JVM for the original agent, release 2.33.0, and
   the source-built agent. Keep all other configuration unchanged. Compare loader
   provenance and the precise startup result, not the server's exit code alone.

Until that retest identifies the actual loader behavior, WebLogic confirmation
remains blocked; this fixture neither fixes production code nor establishes that
current WebLogic deployments reproduce the issue.
