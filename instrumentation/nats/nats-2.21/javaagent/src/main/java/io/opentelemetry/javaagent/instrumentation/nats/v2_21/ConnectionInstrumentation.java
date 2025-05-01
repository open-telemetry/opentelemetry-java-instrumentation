/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.nats.v2_21.NatsSingletons.PRODUCER_INSTRUMENTER;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.nats.v2_21.OpenTelemetryMessage;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.nats.client.Connection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("publish"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.nats.client.Message"))),
        ConnectionInstrumentation.class.getName() + "$PublishAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublishAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static Message onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope) {
      Context parentContext = Context.current();

      if (!PRODUCER_INSTRUMENTER.shouldStart(parentContext, message)) {
        return message;
      }

      OpenTelemetryMessage otelMessage = new OpenTelemetryMessage(connection, message);

      otelContext = PRODUCER_INSTRUMENTER.start(parentContext, otelMessage);
      otelScope = otelContext.makeCurrent();

      return otelMessage;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.This Connection connection,
        @Advice.Argument(0) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope) {
      if (otelScope == null) {
        return;
      }

      otelScope.close();
      PRODUCER_INSTRUMENTER.end(otelContext, message, null, throwable);
    }
  }
}
