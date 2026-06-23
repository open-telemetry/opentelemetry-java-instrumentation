/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_13.ThriftSingletons.getProtocolDecorator;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerCallContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;

class ThriftFrameBufferInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.thrift.server.AbstractNonblockingServer$FrameBuffer",
        "org.apache.thrift.server.AbstractNonblockingServer$AsyncFrameBuffer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("invoke").and(takesNoArguments()), getClass().getName() + "$InvokeAdvice");

    transformer.applyAdviceToMethod(
        namedOneOf("getInputProtocol").and(takesNoArguments()),
        getClass().getName() + "$InputProtocolAdvice");

    transformer.applyAdviceToMethod(
        namedOneOf("getOutputProtocol").and(takesNoArguments()),
        getClass().getName() + "$OutputProtocolAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ServerCallContext methodEnter(@Advice.FieldValue("trans_") TTransport transport) {
      return ServerCallContext.start(transport);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(@Advice.Enter ServerCallContext enter) {
      enter.end();
    }
  }

  @SuppressWarnings("unused")
  public static class InputProtocolAdvice {
    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static TProtocol methodExit(@Advice.Return TProtocol inProtocol) {
      return getProtocolDecorator(inProtocol, false);
    }
  }

  @SuppressWarnings("unused")
  public static class OutputProtocolAdvice {
    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static TProtocol methodExit(@Advice.Return TProtocol outProtocol) {
      return getProtocolDecorator(outProtocol, true);
    }
  }
}
