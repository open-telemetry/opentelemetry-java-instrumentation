/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.server;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.transport.TNonblockingTransport;

public final class ThriftFrameBufferInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.server.TNonblockingServer$FrameBuffer")
        .or(named("org.apache.thrift.server.AbstractNonblockingServer$FrameBuffer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("invoke")),
        ThriftFrameBufferInstrumentation.class.getName() + "$InvokeAdvice");
  }

  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.FieldValue(value = "trans_") TNonblockingTransport transport) {
      ServerInProtocolWrapper.CONTEXT_THREAD_LOCAL.set(transport);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExist() {
      ServerInProtocolWrapper.CONTEXT_THREAD_LOCAL.remove();
    }
  }
}
