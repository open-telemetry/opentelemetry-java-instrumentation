/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.CamelContext;

public class CamelContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.camel.CamelContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.camel.CamelContext"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("start").and(isPublic()).and(takesArguments(0)),
        this.getClass().getName() + "$StartAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onContextStart(@Advice.This final CamelContext context) throws Exception {

      if (context.hasService(CamelTracingService.class) == null) {
        // start this service eager so we init before Camel is starting up
        context.addService(new CamelTracingService(context), true, true);
      }
    }
  }
}
