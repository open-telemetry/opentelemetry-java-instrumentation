/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class JmsSessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.Session");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.Session"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("createConsumer")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(isPublic()),
        JmsSessionInstrumentation.class.getName() + "$ConsumerAdvice");
  }

  public static class ConsumerAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Destination destination, @Advice.Return MessageConsumer consumer) {
      MessageDestination messageDestination = JmsTracer.extractMessageDestination(destination);
      InstrumentationContext.get(MessageConsumer.class, MessageDestination.class)
          .put(consumer, messageDestination);
    }
  }
}
