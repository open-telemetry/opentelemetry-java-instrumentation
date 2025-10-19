/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.nats.v2_17.NatsSingletons.CONSUMER_PROCESS_INSTRUMENTER;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.nats.client.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class MessageHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.nats.client.MessageHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("onMessage"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.nats.client.Message"))),
        MessageHandlerInstrumentation.class.getName() + "$OnMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnMessageAdvice {

    public static class AdviceScope {
      private final NatsRequest request;
      private final Context context;
      private final Scope scope;

      public AdviceScope(NatsRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Message message) {
        Context parentContext = Context.current();
        NatsRequest request = NatsRequest.create(message.getConnection(), message);
        if (!CONSUMER_PROCESS_INSTRUMENTER.shouldStart(parentContext, request)) {
          return null;
        }
        Context context = CONSUMER_PROCESS_INSTRUMENTER.start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        CONSUMER_PROCESS_INSTRUMENTER.end(context, request, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Message message) {
      return AdviceScope.start(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
