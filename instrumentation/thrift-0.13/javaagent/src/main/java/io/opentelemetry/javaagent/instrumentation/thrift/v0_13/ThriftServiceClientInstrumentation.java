/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_13.ThriftSingletons.getPropagators;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientProtocolDecorator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;

class ThriftServiceClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.TServiceClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("org.apache.thrift.protocol.TProtocol"))
                    .and(takesArgument(1, named("org.apache.thrift.protocol.TProtocol")))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.AssignReturned.ToFields({
      @ToField(value = "iprot_", index = 0),
      @ToField(value = "oprot_", index = 1)
    })
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Object[] onExit(
        @Advice.This TServiceClient serviceClient,
        @Advice.FieldValue("iprot_") TProtocol inProtocol,
        @Advice.FieldValue("oprot_") TProtocol outProtocol) {
      Class<?> serviceClass = serviceClient.getClass();
      if (serviceClass.getDeclaringClass() != null) {
        serviceClass = serviceClass.getDeclaringClass();
      }
      ClientProtocolDecorator.AgentDecorator agentDecorator =
          new ClientProtocolDecorator.AgentDecorator(
              serviceClass.getName(), ThriftSingletons.clientInstrumenter(), getPropagators());
      return new Object[] {
        agentDecorator.decorate(inProtocol), agentDecorator.decorate(outProtocol)
      };
    }
  }
}
