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
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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

  public static class MessageFutureAdviceScope {
    private final NatsRequest request;
    private final Context context;
    private final Context parentContext;
    private final Scope scope;

    private MessageFutureAdviceScope(
        NatsRequest request, Context parentContext, Context context, Scope scope) {
      this.request = request;
      this.parentContext = parentContext;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static MessageFutureAdviceScope start(NatsRequest request) {
      Context parentContext = Context.current();
      if (!PRODUCER_INSTRUMENTER.shouldStart(parentContext, request)) {
        return null;
      }
      Context context = PRODUCER_INSTRUMENTER.start(parentContext, request);
      return new MessageFutureAdviceScope(request, parentContext, context, context.makeCurrent());
    }

    public CompletableFuture<Message> end(
        Connection connection,
        @Nullable CompletableFuture<Message> messageFuture,
        @Nullable Throwable throwable) {
      scope.close();
      if (throwable != null || messageFuture == null) {
        PRODUCER_INSTRUMENTER.end(context, request, null, throwable);
        return messageFuture;
      }

      messageFuture =
          messageFuture.whenComplete(
              new SpanFinisher(PRODUCER_INSTRUMENTER, context, connection, request));
      return CompletableFutureWrapper.wrap(messageFuture, parentContext);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Message onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Argument(2) Duration timeout)
        throws InterruptedException {
      // call the instrumented request method
      return connection.request(subject, null, body, timeout);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Message onExit(@Advice.Enter Message message) {
      return message;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestHeadersBodyAdvice {

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
      public static AdviceScope start(NatsRequest request) {
        Context parentContext = Context.current();
        if (!PRODUCER_INSTRUMENTER.shouldStart(parentContext, request)) {
          return null;
        }
        Context context = PRODUCER_INSTRUMENTER.start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(
          Connection connection, @Nullable Message message, @Nullable Throwable throwable) {
        scope.close();

        NatsRequest response = null;
        if (message != null) {
          response = NatsRequest.create(connection, message);
        }

        scope.close();
        PRODUCER_INSTRUMENTER.end(context, request, response, throwable);
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers originalHeaders,
        @Advice.Argument(2) byte[] body) {
      Headers headers = NatsMessageWritableHeaders.create(originalHeaders);
      NatsRequest request = NatsRequest.create(connection, subject, null, headers, body);
      AdviceScope adviceScope = AdviceScope.start(request);
      return new Object[] {adviceScope, headers};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Connection connection,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable Message message,
        @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(connection, message, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestMessageAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Message onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Message request,
        @Advice.Argument(1) Duration timeout)
        throws InterruptedException {
      if (request == null) {
        return null;
      }

      // call the instrumented request method
      return connection.request(
          request.getSubject(), request.getHeaders(), request.getData(), timeout);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Message onExit(@Advice.Enter Message response) {
      return response;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body) {
      // call the instrumented request method
      return connection.request(subject, null, body);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Enter CompletableFuture<Message> future) {
      return future;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureHeadersBodyAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers originalHeaders,
        @Advice.Argument(2) byte[] body) {
      Headers headers = NatsMessageWritableHeaders.create(originalHeaders);
      NatsRequest request = NatsRequest.create(connection, subject, null, headers, body);
      MessageFutureAdviceScope adviceScope = MessageFutureAdviceScope.start(request);
      return new Object[] {adviceScope, headers};
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.This Connection connection,
        @Advice.Thrown Throwable throwable,
        @Advice.Return CompletableFuture<Message> originalReturnValue,
        @Advice.Enter Object[] enterResult) {
      MessageFutureAdviceScope adviceScope = (MessageFutureAdviceScope) enterResult[0];
      if (adviceScope != null) {
        return adviceScope.end(connection, originalReturnValue, throwable);
      }
      return originalReturnValue;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureMessageAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection, @Advice.Argument(0) Message message) {
      // execute original method body to handle null message
      if (message == null) {
        return null;
      }

      // call the instrumented request method
      return connection.request(message.getSubject(), message.getHeaders(), message.getData());
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Return CompletableFuture<Message> originalResult,
        @Advice.Enter CompletableFuture<Message> future) {
      return future != null ? future : originalResult;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestTimeoutFutureBodyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static CompletableFuture<Message> onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Argument(2) Duration timeout) {
      // call the instrumented requestWithTimeout method
      return connection.requestWithTimeout(subject, null, body, timeout);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Enter CompletableFuture<Message> future) {
      return future;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestTimeoutFutureHeadersBodyAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers originalHeaders,
        @Advice.Argument(2) byte[] body) {

      Headers headers = NatsMessageWritableHeaders.create(originalHeaders);
      NatsRequest request = NatsRequest.create(connection, subject, null, headers, body);
      MessageFutureAdviceScope adviceScope = MessageFutureAdviceScope.start(request);
      return new Object[] {adviceScope, headers};
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.This Connection connection,
        @Advice.Thrown Throwable throwable,
        @Advice.Return CompletableFuture<Message> originalMessageFuture,
        @Advice.Enter Object[] enterResult) {

      CompletableFuture<Message> messageFuture = originalMessageFuture;
      MessageFutureAdviceScope adviceScope = (MessageFutureAdviceScope) enterResult[0];
      if (adviceScope != null) {
        return adviceScope.end(connection, originalMessageFuture, throwable);
      }
      return originalMessageFuture;
    }
  }

  @SuppressWarnings("unused")
  public static class RequestTimeoutFutureMessageAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Object[] onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Message message,
        @Advice.Argument(1) Duration timeout) {
      if (message == null) {
        return new Object[] {null, message};
      }
      // call the instrumented requestWithTimeout method
      CompletableFuture<Message> future =
          connection.requestWithTimeout(
              message.getSubject(), message.getHeaders(), message.getData(), timeout);

      return new Object[] {future, message};
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<Message> onExit(
        @Advice.Return CompletableFuture<Message> originalResult,
        @Advice.Enter Object[] enterResult) {

      @SuppressWarnings("unchecked") // fine
      CompletableFuture<Message> future = (CompletableFuture<Message>) enterResult[0];
      return future != null ? future : originalResult;
    }
  }
}
