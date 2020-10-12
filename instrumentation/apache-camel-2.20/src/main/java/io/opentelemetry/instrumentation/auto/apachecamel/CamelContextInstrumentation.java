/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachecamel;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.CamelContext;

@AutoService(Instrumenter.class)
public class CamelContextInstrumentation extends Instrumenter.Default {

  public CamelContextInstrumentation() {
    super("apachecamel", "apache-camel");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.camel.CamelContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {

    return not(isAbstract()).and(implementsInterface(named("org.apache.camel.CamelContext")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$ContextAdvice",
      "io.opentelemetry.instrumentation.auto.apachecamel.SpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.BaseSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.DbSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.MessagingSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.HttpSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.InternalSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.KafkaSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.LogSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.RestSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.TimerSpanDecorator",
      "io.opentelemetry.instrumentation.auto.apachecamel.decorators.DecoratorRegistry",
      "io.opentelemetry.instrumentation.auto.apachecamel.ActiveSpanManager",
      "io.opentelemetry.instrumentation.auto.apachecamel.ActiveSpanManager$SpanWithScope",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelPropagationUtil",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelPropagationUtil$MapGetter",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelPropagationUtil$MapSetter",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelTracer",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelEventNotifier",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelRoutePolicy",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelTracingService",
      "io.opentelemetry.instrumentation.auto.apachecamel.CamelContextInstrumentation"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        named("start").and(isPublic()).and(takesArguments(0)),
        CamelContextInstrumentation.class.getName() + "$ContextAdvice");

    return transformers;
  }

  public static class ContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onContextStart(@Advice.This final CamelContext context) throws Exception {

      if (context.hasService(CamelTracingService.class) == null) {
        // start this service eager so we init before Camel is starting up
        context.addService(new CamelTracingService(context), true, true);
      }
    }
  }
}
