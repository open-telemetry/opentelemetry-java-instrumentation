/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_13.ThriftSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerInProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerOutProtocolDecorator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;

class ThriftTBaseProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.TBaseProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("process"), getClass().getName() + "$ProcessAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] methodEnter(
        @Advice.Argument(0) TProtocol inProtocol,
        @Advice.Argument(1) TProtocol outProtocol,
        @Advice.FieldValue("iface") Object iface) {
      String serviceName = iface.getClass().getName();

      ServerInProtocolDecorator serverInProtocolDecorator =
          new ServerInProtocolDecorator(inProtocol, serviceName, serverInstrumenter());
      ServerOutProtocolDecorator serverOutProtocolDecorator =
          new ServerOutProtocolDecorator(outProtocol);

      return new Object[] {serverInProtocolDecorator, serverOutProtocolDecorator};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Enter Object[] enter, @Advice.Thrown Throwable throwable) {
      if (enter == null) {
        return;
      }

      ServerInProtocolDecorator serverInProtocolDecorator = (ServerInProtocolDecorator) enter[0];
      ServerOutProtocolDecorator serverOutProtocolDecorator = (ServerOutProtocolDecorator) enter[1];

      serverInProtocolDecorator.endSpan(throwable, serverOutProtocolDecorator.hasException());
    }
  }
}
