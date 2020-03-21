/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import static io.opentelemetry.auto.instrumentation.rabbitmq.amqp.RabbitCommandInstrumentation.SpanHolder.CURRENT_RABBIT_SPAN;
import static io.opentelemetry.auto.instrumentation.rabbitmq.amqp.RabbitDecorator.DECORATE;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.rabbitmq.client.Command;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class RabbitCommandInstrumentation extends Instrumenter.Default {

  public RabbitCommandInstrumentation() {
    super("amqp", "rabbitmq");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("com.rabbitmq.client.Command");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.rabbitmq.client.Command"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RabbitDecorator",
      // These are only used by muzzleCheck:
      packageName + ".TextMapExtractAdapter",
      packageName + ".TracedDelegatingConsumer",
      RabbitCommandInstrumentation.class.getName() + "$SpanHolder"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor(),
        RabbitCommandInstrumentation.class.getName() + "$CommandConstructorAdvice");
  }

  public static class SpanHolder {
    public static final ThreadLocal<Span> CURRENT_RABBIT_SPAN = new ThreadLocal<>();
  }

  public static class CommandConstructorAdvice {
    @Advice.OnMethodExit
    public static void setResourceNameAddHeaders(@Advice.This final Command command) {

      final Span span = CURRENT_RABBIT_SPAN.get();
      if (span != null && command.getMethod() != null) {
        DECORATE.onCommand(span, command);
      }
    }

    /**
     * This instrumentation will match with 2.6, but the channel instrumentation only matches with
     * 2.7 because of TracedDelegatingConsumer. This unused method is added to ensure consistent
     * muzzle validation by preventing match with 2.6.
     */
    public static void muzzleCheck(final TracedDelegatingConsumer consumer) {
      consumer.handleRecoverOk(null);
    }
  }
}
