package datadog.trace.instrumentation.trace_annotation;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Trace;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class TraceAnnotationInstrumentation extends Instrumenter.Configurable {

  private static final String[] ADDITIONAL_ANNOTATIONS =
      new String[] {
        "com.newrelic.api.agent.Trace",
        "kamon.annotation.Trace",
        "com.tracelytics.api.ext.LogMethod",
        "io.opentracing.contrib.dropwizard.Trace",
        "org.springframework.cloud.sleuth.annotation.NewSpan"
      };

  public TraceAnnotationInstrumentation() {
    super("trace", "trace-annotation");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    ElementMatcher.Junction<NamedElement> methodTraceMatcher =
        is(new TypeDescription.ForLoadedType(Trace.class));
    for (final String annotationName : ADDITIONAL_ANNOTATIONS) {
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
}
