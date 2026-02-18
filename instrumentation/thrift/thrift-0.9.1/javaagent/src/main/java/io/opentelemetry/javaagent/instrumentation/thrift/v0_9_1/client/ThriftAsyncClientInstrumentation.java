/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

public final class ThriftAsyncClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.async.TAsyncClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("org.apache.thrift.protocol.TProtocolFactory")))
            .and(takesArgument(2, named("org.apache.thrift.transport.TNonblockingTransport"))),
        ThriftAsyncClientInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") String serviceName,
        @Advice.Argument(value = 0, readOnly = false) TProtocolFactory factory,
        @Advice.Argument(value = 2) TTransport transport) {
      if (factory instanceof ClientProtocolFactoryWrapper) {
        return;
      }
      factory = new ClientProtocolFactoryWrapper(factory, transport, serviceName);
    }
  }
}
