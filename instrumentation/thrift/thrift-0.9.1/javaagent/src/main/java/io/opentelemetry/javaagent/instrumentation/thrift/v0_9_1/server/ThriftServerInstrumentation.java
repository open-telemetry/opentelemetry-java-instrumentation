/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocolFactory;

class ThriftServerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.server.TServer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(1)), getClass().getName() + "$ServerConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServerConstructorAdvice {

    @AssignReturned.ToFields(@ToField("inputProtocolFactory_"))
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static TProtocolFactory onExit(
        @Advice.FieldValue("inputProtocolFactory_") TProtocolFactory factory) {
      if (factory instanceof ServerProtocolFactoryWrapper) {
        return factory;
      }
      return new ServerProtocolFactoryWrapper(factory);
    }
  }
}
