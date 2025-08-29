/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.nats.v2_17.NatsSingletons.PRODUCER_INSTRUMENTER;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ConnectionRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.nats.client.Connection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("request"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, byte[].class))
            .and(takesArgument(2, Duration.class))
            .and(returns(named("io.nats.client.Message"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("request"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("io.nats.client.impl.Headers")))
            .and(takesArgument(2, byte[].class))
            .and(takesArgument(3, Duration.class))
            .and(returns(named("io.nats.client.Message"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestHeadersBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("request"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.nats.client.Message")))
            .and(takesArgument(1, Duration.class))
            .and(returns(named("io.nats.client.Message"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestMessageAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("request"))
            .and(takesArguments(2))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, byte[].class))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestFutureBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("request"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("io.nats.client.impl.Headers")))
            .and(takesArgument(2, byte[].class))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestFutureHeadersBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("request"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.nats.client.Message")))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestFutureMessageAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("requestWithTimeout"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, byte[].class))
            .and(takesArgument(2, Duration.class))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestTimeoutFutureBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("requestWithTimeout"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("io.nats.client.impl.Headers")))
            .and(takesArgument(2, byte[].class))
            .and(takesArgument(3, Duration.class))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName()
            + "$RequestTimeoutFutureHeadersBodyAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("requestWithTimeout"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.nats.client.Message")))
            .and(takesArgument(1, Duration.class))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestTimeoutFutureMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Message onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Argument(2) Duration timeout,
        @Advice.Local("message") Message message
    ) throws InterruptedException {
      message = connection.request(NatsMessageWritableHeaders.create(subject,body), timeout);
      return message;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Message onExit(
        @Advice.Return(readOnly = false) Message returned,
        @Advice.Local("message") Message message) {
      returned = message;
      return returned;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestHeadersBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Message onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(value = 1, readOnly = false) Headers headers,
        @Advice.Argument(2) byte[] body,
        @Advice.Argument(3) Duration timeout,
        @Advice.Local("message") Message message) throws InterruptedException {
      message = connection.request(NatsMessageWritableHeaders.create(subject, headers, body), timeout);
      return message;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Message onExit(
        @Advice.Return(readOnly = false) Message returned,
        @Advice.Local("message") Message message) {
      returned = message;
      return returned;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, readOnly = false) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
        message = NatsMessageWritableHeaders.create(message);


      natsRequest = NatsRequest.create(connection, message);
      Context parentContext = Context.current();

      if (!PRODUCER_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
        return;
      }

      otelContext = PRODUCER_INSTRUMENTER.start(parentContext, natsRequest);
      otelScope = otelContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Connection connection,
        @Advice.Thrown Throwable throwable,
        @Advice.Return Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      if (otelScope == null) {
        return;
      }

      NatsRequest natsResponse = null;
      if (message != null) {
        natsResponse = NatsRequest.create(connection, message);
      }

      otelScope.close();
      PRODUCER_INSTRUMENTER.end(otelContext, natsRequest, natsResponse, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Local("future") CompletableFuture<Message> future) {
      future = connection.request(NatsMessageWritableHeaders.create(subject, body));
      return future;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Return(readOnly = false) CompletableFuture<Message> messageFuture,
        @Advice.Local("future") CompletableFuture<Message> future) {
      messageFuture = future;
      return messageFuture;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureHeadersBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(value = 1,readOnly = false) Headers headers,
        @Advice.Argument(2) byte[] body,
        @Advice.Local("future") CompletableFuture<Message> future) {
      future = connection.request(NatsMessageWritableHeaders.create(subject, headers, body));
      return future;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Return(readOnly = false) CompletableFuture<Message> messageFuture,
        @Advice.Local("future") CompletableFuture<Message> future) {
      messageFuture = future;
      return messageFuture;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, readOnly = false) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
       message = NatsMessageWritableHeaders.create(message);


      natsRequest = NatsRequest.create(connection, message);
      otelParentContext = Context.current();

      if (!PRODUCER_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = PRODUCER_INSTRUMENTER.start(otelParentContext, natsRequest);
      otelScope = otelContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Connection connection,
        @Advice.Thrown Throwable throwable,
        @Advice.Return(readOnly = false) CompletableFuture<Message> messageFuture,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      if (otelScope == null) {
        return;
      }

      otelScope.close();
      if (throwable != null) {
        PRODUCER_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture =
            messageFuture.whenComplete(
                new SpanFinisher(PRODUCER_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestTimeoutFutureBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Local("future") CompletableFuture<Message> future) {
      future = connection.request(NatsMessageWritableHeaders.create(subject, body));
      return future;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Return(readOnly = false) CompletableFuture<Message> messageFuture,
        @Advice.Local("future") CompletableFuture<Message> future) {
      messageFuture = future;
      return messageFuture;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestTimeoutFutureHeadersBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(value = 1, readOnly = false) Headers headers,
        @Advice.Argument(2) byte[] body,
        @Advice.Local("future") CompletableFuture<Message> future) {
      future = connection.request(NatsMessageWritableHeaders.create(subject, headers, body));
      return future;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Return(readOnly = false) CompletableFuture<Message> messageFuture,
        @Advice.Local("future") CompletableFuture<Message> future) {
      messageFuture = future;
      return messageFuture;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestTimeoutFutureMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0,readOnly = false) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
        message = NatsMessageWritableHeaders.create(message);

      natsRequest = NatsRequest.create(connection, message);
      otelParentContext = Context.current();

      if (!PRODUCER_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = PRODUCER_INSTRUMENTER.start(otelParentContext, natsRequest);
      otelScope = otelContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Connection connection,
        @Advice.Thrown Throwable throwable,
        @Advice.Return(readOnly = false) CompletableFuture<Message> messageFuture,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      if (otelScope == null) {
        return;
      }

      otelScope.close();
      if (throwable != null) {
        PRODUCER_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture =
            messageFuture.whenComplete(
                new SpanFinisher(PRODUCER_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }
}
