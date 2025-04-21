/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.server;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocolFactory;

public final class ThriftServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.server.TServer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(1)),
        ThriftServerInstrumentation.class.getName() + "$ServerConstructorAdvice");
  }

  public static class ServerConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.FieldValue(value = "inputProtocolFactory_", readOnly = false)
            TProtocolFactory factory) {
      if (factory instanceof ServerProtocolFactoryWrapper) {
        return;
      }
      factory = new ServerProtocolFactoryWrapper(factory);
    }
  }
}
