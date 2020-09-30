/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.jms;

import static io.opentelemetry.instrumentation.auto.jms.JMSTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMSMessageProducerInstrumentation extends Instrumenter.Default {

  public JMSMessageProducerInstrumentation() {
    super("jms", "jms-1", "jms-2");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.jms.MessageProducer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageProducer"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".MessageDestination",
      packageName + ".Operation",
      packageName + ".JMSTracer",
      packageName + ".MessageExtractAdapter",
      packageName + ".MessageInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("send").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        JMSMessageProducerInstrumentation.class.getName() + "$ProducerAdvice");
    transformers.put(
        named("send")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(takesArgument(1, named("javax.jms.Message")))
            .and(isPublic()),
        JMSMessageProducerInstrumentation.class.getName() + "$ProducerWithDestinationAdvice");
    return transformers;
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Message message,
        @Advice.This MessageProducer producer,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return;
      }

      Destination defaultDestination;
      try {
        defaultDestination = producer.getDestination();
      } catch (JMSException e) {
        defaultDestination = null;
      }

      MessageDestination messageDestination =
          TRACER.extractDestination(message, defaultDestination);
      span = TRACER.startProducerSpan(messageDestination, message);
      scope = TRACER.startProducerScope(span, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      CallDepthThreadLocalMap.reset(MessageProducer.class);

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }
    }
  }

  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Destination destination,
        @Advice.Argument(1) Message message,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return;
      }

      MessageDestination messageDestination = TRACER.extractDestination(message, destination);
      span = TRACER.startProducerSpan(messageDestination, message);
      scope = TRACER.startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(MessageProducer.class);

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }
    }
  }
}
