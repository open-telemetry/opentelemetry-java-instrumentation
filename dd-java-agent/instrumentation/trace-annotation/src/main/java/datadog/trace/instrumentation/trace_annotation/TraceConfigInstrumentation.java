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

  static final String PACKAGE_CLASS_NAME_REGEX = "[\\w.\\$]+";
  private static final String METHOD_LIST_REGEX = "\\s*(?:\\w+\\s*,)*\\s*(?:\\w+\\s*,?)\\s*";
  private static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "\\]\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "\\]\\s*;?\\s*";

  private final Map<String, Set<String>> classMethodsToTrace;

  public TraceConfigInstrumentation() {
    super("trace", "trace-config");

    final String configString = getPropOrEnv(CONFIG_NAME);
    if (configString == null || configString.trim().isEmpty()) {
      classMethodsToTrace = Collections.emptyMap();

    } else if (!configString.matches(CONFIG_FORMAT)) {
      log.warn(
          "Invalid trace method config '{}'. Must match 'package.Class$Name[method1,method2];*'.",
          configString);
      classMethodsToTrace = Collections.emptyMap();

    } else {
      final Map<String, Set<String>> toTrace = Maps.newHashMap();
      final String[] classMethods = configString.split(";", -1);
      for (final String classMethod : classMethods) {
        if (classMethod.trim().isEmpty()) {
          continue;
        }
        final String[] splitClassMethod = classMethod.split("\\[", -1);
        final String className = splitClassMethod[0];
        final String method = splitClassMethod[1].trim();
        final String methodNames = method.substring(0, method.length() - 1);
        final String[] splitMethodNames = methodNames.split(",", -1);
        final Set<String> trimmedMethodNames =
            Sets.newHashSetWithExpectedSize(splitMethodNames.length);
        for (final String methodName : splitMethodNames) {
          final String trimmedMethodName = methodName.trim();
          if (!trimmedMethodName.isEmpty()) {
            trimmedMethodNames.add(trimmedMethodName);
          }
        }
        if (!trimmedMethodNames.isEmpty()) {
          toTrace.put(className.trim(), trimmedMethodNames);
        }
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
