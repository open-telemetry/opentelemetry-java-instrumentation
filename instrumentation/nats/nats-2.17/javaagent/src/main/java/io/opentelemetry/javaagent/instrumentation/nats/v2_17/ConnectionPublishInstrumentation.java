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
import net.bytebuddy.asm.Advice;
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
      connection.publish(NatsMessageWritableHeaders.create(subject, body));
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class PublishHeadersBodyAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(value = 1, readOnly = false) Headers headers,
        @Advice.Argument(2) byte[] body) {
      connection.publish(NatsMessageWritableHeaders.create(subject, headers, body));
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
      connection.publish(NatsMessageWritableHeaders.create(subject, replyTo, body));
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class PublishReplyToHeadersBodyAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) String replyTo,
        @Advice.Argument(value = 2, readOnly = false) Headers headers,
        @Advice.Argument(3) byte[] body) {
      connection.publish(NatsMessageWritableHeaders.create(subject, replyTo, headers, body));
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class PublishMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, readOnly = false) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      message = NatsMessageWritableHeaders.create(message);

      Context parentContext = Context.current();
      natsRequest = NatsRequest.create(connection, message);

      if (!PRODUCER_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
        return;
      }

      otelContext = PRODUCER_INSTRUMENTER.start(parentContext, natsRequest);
      otelScope = otelContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      if (otelScope == null) {
        return;
      }

      otelScope.close();
      PRODUCER_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
    }
  }
}
