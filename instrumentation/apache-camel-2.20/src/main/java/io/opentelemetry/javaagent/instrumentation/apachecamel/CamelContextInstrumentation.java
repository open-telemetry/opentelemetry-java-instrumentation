/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Collections;
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
      "io.opentelemetry.javaagent.instrumentation.apachecamel.SpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.BaseSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.DbSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.MessagingSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.HttpSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.InternalSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.KafkaSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.LogSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.RestSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.TimerSpanDecorator",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.DecoratorRegistry",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.ActiveSpanManager",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.ActiveSpanManager$SpanWithScope",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelPropagationUtil",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelPropagationUtil$MapGetter",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelPropagationUtil$MapSetter",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelTracer",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelEventNotifier",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelRoutePolicy",
      "io.opentelemetry.javaagent.instrumentation.apachecamel.CamelTracingService"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    return Collections.singletonMap(
        named("start").and(isPublic()).and(takesArguments(0)),
        CamelContextInstrumentation.class.getName() + "$ContextAdvice");
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
