package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.instrumentation.trace_annotation.TraceConfigInstrumentation.PACKAGE_CLASS_NAME_REGEX;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Trace;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class TraceAnnotationInstrumentation extends Instrumenter.Configurable {
  private static final String CONFIG_NAME = "dd.trace.annotations";

  static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;?\\s*";

  private static final String[] DEFAULT_ANNOTATIONS =
      new String[] {
        "com.newrelic.api.agent.Trace",
        "kamon.annotation.Trace",
        "com.tracelytics.api.ext.LogMethod",
        "io.opentracing.contrib.dropwizard.Trace",
        "org.springframework.cloud.sleuth.annotation.NewSpan"
      };

  private final Set<String> additionalTraceAnnotations;

  public TraceAnnotationInstrumentation() {
    super("trace", "trace-annotation");

    final String configString = getPropOrEnv(CONFIG_NAME);
    if (configString == null) {
      additionalTraceAnnotations =
          Collections.unmodifiableSet(Sets.<String>newHashSet(DEFAULT_ANNOTATIONS));
    } else if (configString.trim().isEmpty()) {
      additionalTraceAnnotations = Collections.emptySet();
    } else if (!configString.matches(CONFIG_FORMAT)) {
      log.warn(
          "Invalid trace annotations config '{}'. Must match 'package.Annotation$Name;*'.",
          configString);
      additionalTraceAnnotations = Collections.emptySet();
    } else {
      final Set<String> annotations = Sets.newHashSet();
      final String[] annotationClasses = configString.split(";", -1);
      for (final String annotationClass : annotationClasses) {
        if (!annotationClass.trim().isEmpty()) {
          annotations.add(annotationClass.trim());
        }
      }
      additionalTraceAnnotations = Collections.unmodifiableSet(annotations);
    }
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    ElementMatcher.Junction<NamedElement> methodTraceMatcher =
        is(new TypeDescription.ForLoadedType(Trace.class));
    for (final String annotationName : additionalTraceAnnotations) {
      methodTraceMatcher = methodTraceMatcher.or(named(annotationName));
    }
    return agentBuilder
        .type(failSafe(hasSuperType(declaresMethod(isAnnotatedWith(methodTraceMatcher)))))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(isAnnotatedWith(methodTraceMatcher), TraceAdvice.class.getName()))
        .asDecorator();
  }

  private String getPropOrEnv(final String name) {
    return System.getProperty(name, System.getenv(propToEnvName(name)));
  }

  static String propToEnvName(final String name) {
    return name.toUpperCase().replace(".", "_");
  }
}
