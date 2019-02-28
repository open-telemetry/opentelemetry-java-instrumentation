package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.trace_annotation.TraceConfigInstrumentation.PACKAGE_CLASS_NAME_REGEX;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.Trace;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class TraceAnnotationsInstrumentation extends Instrumenter.Default {

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
  private final ElementMatcher.Junction<NamedElement> methodTraceMatcher;

  public TraceAnnotationsInstrumentation() {
    super("trace", "trace-annotation");

    final String configString = Config.getSettingFromEnvironment(Config.TRACE_ANNOTATIONS, null);
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

    ElementMatcher.Junction<NamedElement> methodTraceMatcher =
        is(new TypeDescription.ForLoadedType(Trace.class));
    for (final String annotationName : additionalTraceAnnotations) {
      methodTraceMatcher = methodTraceMatcher.or(named(annotationName));
    }
    this.methodTraceMatcher = methodTraceMatcher;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(declaresMethod(isAnnotatedWith(methodTraceMatcher)));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator", packageName + ".TraceDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isAnnotatedWith(methodTraceMatcher), TraceAdvice.class.getName());
  }
}
