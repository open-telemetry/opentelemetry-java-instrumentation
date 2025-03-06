/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IoReactorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.hc.core5.reactor.IOReactor"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("close", "initiateShutdown"), this.getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @SuppressWarnings("SystemOut")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This Object instance) {
      System.err.println("closing i/o reactor " + instance);
      new Exception().printStackTrace();
    }
  }
}
