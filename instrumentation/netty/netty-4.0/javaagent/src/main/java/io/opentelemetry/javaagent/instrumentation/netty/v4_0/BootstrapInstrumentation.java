/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyHttpClientTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class BootstrapInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.netty.bootstrap.Bootstrap");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), BootstrapInstrumentation.class.getName() + "$InitAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {
    @Advice.OnMethodEnter
    public static void enter() {
      // Ensure that tracer is initialized. Connection failure handling is initialized in the static
      // initializer of tracer which needs to be run before an attempt is made to establish
      // connection.
      tracer();
    }
  }
}
