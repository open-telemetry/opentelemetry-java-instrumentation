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

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMSSessionInstrumentation extends Instrumenter.Default {

  public JMSSessionInstrumentation() {
    super("jms", "jms-1", "jms-2");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.jms.Session");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.Session"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".MessageDestination",
      packageName + ".JMSTracer",
      packageName + ".MessageExtractAdapter",
      packageName + ".MessageInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("createConsumer")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(isPublic()),
        JMSSessionInstrumentation.class.getName() + "$ConsumerAdvice");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "javax.jms.MessageConsumer",
        "io.opentelemetry.javaagent.instrumentation.jms.MessageDestination");
  }

  public static class ConsumerAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Destination destination, @Advice.Return MessageConsumer consumer) {
      MessageDestination messageDestination = JMSTracer.extractMessageDestination(destination);
      InstrumentationContext.get(MessageConsumer.class, MessageDestination.class)
          .put(consumer, messageDestination);
    }
  }
}
