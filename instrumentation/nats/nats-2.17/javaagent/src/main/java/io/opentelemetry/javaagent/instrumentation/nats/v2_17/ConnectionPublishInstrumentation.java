/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.nats.v2_17.NatsSingletons.PRODUCER_INSTRUMENTER;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsMessageWritableHeaders;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ConnectionPublishInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.nats.client.Connection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("publish"))
            .and(takesArguments(2))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, byte[].class)),
        ConnectionPublishInstrumentation.class.getName() + "$PublishBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("publish"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("io.nats.client.impl.Headers")))
            .and(takesArgument(2, byte[].class)),
        ConnectionPublishInstrumentation.class.getName() + "$PublishHeadersBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("publish"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, byte[].class)),
        ConnectionPublishInstrumentation.class.getName() + "$PublishReplyToBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("publish"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, named("io.nats.client.impl.Headers")))
            .and(takesArgument(3, byte[].class)),
        ConnectionPublishInstrumentation.class.getName() + "$PublishReplyToHeadersBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("publish"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.nats.client.Message"))),
        ConnectionPublishInstrumentation.class.getName() + "$PublishMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublishBodyAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body) {
      // call the instrumented publish method
      connection.publish(subject, null, null, body);
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class PublishHeadersBodyAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers headers,
        @Advice.Argument(2) byte[] body) {
      // call the instrumented publish method
      connection.publish(subject, null, headers, body);
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class PublishReplyToBodyAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) String replyTo,
        @Advice.Argument(2) byte[] body) {
      // call the instrumented publish method
      connection.publish(subject, replyTo, null, body);
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class PublishReplyToHeadersBodyAdvice {

    public static class AdviceScope {
      private final NatsRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(NatsRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(NatsRequest natsRequest) {
        Context parentContext = Context.current();
        if (!PRODUCER_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
          return null;
        }
        Context context = PRODUCER_INSTRUMENTER.start(parentContext, natsRequest);
        return new AdviceScope(natsRequest, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        PRODUCER_INSTRUMENTER.end(context, request, null, throwable);
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) String replyTo,
        @Advice.Argument(2) Headers originalHeaders,
        @Advice.Argument(3) byte[] body) {
      Headers headers = NatsMessageWritableHeaders.create(originalHeaders);
      NatsRequest natsRequest = NatsRequest.create(connection, subject, replyTo, headers, body);
      AdviceScope adviceScope = AdviceScope.start(natsRequest);
      return new Object[] {adviceScope, headers};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class PublishMessageAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
        @Advice.This Connection connection, @Advice.Argument(0) Message message) {
      if (message == null) {
        return false;
      }

      // call the instrumented publish method
      connection.publish(
          message.getSubject(), message.getReplyTo(), message.getHeaders(), message.getData());
      return true;
    }
  }
}
