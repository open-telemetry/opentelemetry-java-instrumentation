/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TNonblockingTransport;

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
        named("invoke"), getClass().getName() + "$FrameBufferConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class FrameBufferConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void methodEnter(
        @Advice.FieldValue("inProt_") TProtocol inProtocol,
        @Advice.FieldValue("trans_") TNonblockingTransport transport) {
      if (inProtocol instanceof ServerInProtocolWrapper) {
        ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) inProtocol;
        wrapper.setTransport(transport);
      }
    }
  }
}
