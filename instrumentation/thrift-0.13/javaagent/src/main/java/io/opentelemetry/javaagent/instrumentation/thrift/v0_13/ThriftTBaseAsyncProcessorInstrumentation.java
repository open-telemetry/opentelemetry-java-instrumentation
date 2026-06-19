/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerInProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerOutProtocolDecorator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.server.AbstractNonblockingServer;

class ThriftTBaseAsyncProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.TBaseAsyncProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process")
            .and(
                takesArgument(
                    0,
                    named("org.apache.thrift.server.AbstractNonblockingServer$AsyncFrameBuffer"))),
        getClass().getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void methodEnter(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb,
        @Advice.FieldValue("iface") Object iface) {
      String serviceName = iface.getClass().getName();
      ((ServerInProtocolDecorator) fb.getInputProtocol()).setServiceName(serviceName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb,
        @Advice.Thrown Throwable throwable) {
      ServerInProtocolDecorator serverInProtocolDecorator =
          (ServerInProtocolDecorator) fb.getInputProtocol();
      ServerOutProtocolDecorator serverOutProtocolDecorator =
          (ServerOutProtocolDecorator) fb.getOutputProtocol();
      if (serverInProtocolDecorator.isOneway()
          || serverOutProtocolDecorator.hasException()
          || throwable != null) {
        serverInProtocolDecorator.endSpan(throwable, serverOutProtocolDecorator.hasException());
      }
    }
  }
}
