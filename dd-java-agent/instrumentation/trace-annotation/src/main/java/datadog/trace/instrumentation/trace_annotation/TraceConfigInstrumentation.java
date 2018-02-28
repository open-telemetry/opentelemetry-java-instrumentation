package datadog.trace.instrumentation.trace_annotation;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public class TraceConfigInstrumentation extends Instrumenter.Configurable {
  private static final String CONFIG_NAME = "dd.trace.methods";

  private final Map<String, Set<String>> classMethodsToTrace;

  public TraceConfigInstrumentation() {
    super("trace", "trace-config");

    final String configString = getPropOrEnv(CONFIG_NAME);
    if (configString == null || configString.trim().isEmpty()) {
      classMethodsToTrace = Collections.emptyMap();

    } else if (!configString.matches(
        "(?:([\\w.\\$]+)\\[((?:\\w+,)*(?:\\w+,?))\\];)*([\\w.\\$]+)\\[((?:\\w+,)*(?:\\w+,?))\\];?")) {
      log.warn(
          "Invalid config '{}'. Must match 'package.Class$Name[method1,method2];*'.", configString);
      classMethodsToTrace = Collections.emptyMap();

    } else {
      final Map<String, Set<String>> toTrace = Maps.newHashMap();
      final String[] classMethods = configString.split(";");
      for (final String classMethod : classMethods) {
        final String[] splitClassMethod = classMethod.split("\\[");
        final String className = splitClassMethod[0];
        final String methodNames =
            splitClassMethod[1].substring(0, splitClassMethod[1].length() - 1);
        final String[] splitMethodNames = methodNames.split(",");
        toTrace.put(className, Sets.newHashSet(splitMethodNames));
      }
      classMethodsToTrace = Collections.unmodifiableMap(toTrace);
    }
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    if (classMethodsToTrace.isEmpty()) {
      return agentBuilder;
    }
    AgentBuilder builder = agentBuilder;

    for (final Map.Entry<String, Set<String>> entry : classMethodsToTrace.entrySet()) {

      ElementMatcher.Junction<MethodDescription> methodMatchers = null;
      for (final String methodName : entry.getValue()) {
        if (methodMatchers == null) {
          methodMatchers = named(methodName);
        } else {
          methodMatchers = methodMatchers.or(named(methodName));
        }
      }
      builder =
          builder
              .type(hasSuperType(named(entry.getKey())))
              .transform(DDAdvice.create().advice(methodMatchers, TraceAdvice.class.getName()))
              .asDecorator();
    }
    return builder;
  }

  private String getPropOrEnv(final String name) {
    return System.getProperty(name, System.getenv(propToEnvName(name)));
  }

  static String propToEnvName(final String name) {
    return name.toUpperCase().replace(".", "_");
  }
}
