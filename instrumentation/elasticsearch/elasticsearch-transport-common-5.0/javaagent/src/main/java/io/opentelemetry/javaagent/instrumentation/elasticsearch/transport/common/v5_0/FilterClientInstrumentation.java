/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.support.AbstractClient;

public class FilterClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.elasticsearch.client.FilterClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("org.elasticsearch.client.Client"))),
        getClass().getName() + "$OneArgumentConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(2, named("org.elasticsearch.client.Client"))),
        getClass().getName() + "$ThreeArgumentConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class OneArgumentConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This AbstractClient client, @Advice.Argument(0) Object delegate) {
      ElasticsearchTransportServerTargets.setDelegate(client, delegate);
    }
  }

  @SuppressWarnings("unused")
  public static class ThreeArgumentConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This AbstractClient client, @Advice.Argument(2) Object delegate) {
      ElasticsearchTransportServerTargets.setDelegate(client, delegate);
    }
  }
}
