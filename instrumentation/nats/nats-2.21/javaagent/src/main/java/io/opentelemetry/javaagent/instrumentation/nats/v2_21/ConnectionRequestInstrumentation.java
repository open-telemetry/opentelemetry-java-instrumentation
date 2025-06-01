/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.nats.v2_21.NatsSingletons.CLIENT_INSTRUMENTER;
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
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.nats.v2_21.internal.MessageConsumer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
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
        ConnectionRequestInstrumentation.class.getName() + "$RequestBodyHeadersAdvice");
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
        ConnectionRequestInstrumentation.class.getName() + "$RequestFutureBodyHeadersAdvice");
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
        ConnectionRequestInstrumentation.class.getName() + "$RequestFutureTimeoutBodyAdvice");
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
            + "$RequestFutureTimeoutBodyHeadersAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("requestWithTimeout"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.nats.client.Message")))
            .and(takesArgument(1, Duration.class))
            .and(returns(named("java.util.concurrent.CompletableFuture"))),
        ConnectionRequestInstrumentation.class.getName() + "$RequestFutureTimeoutMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestBodyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, subject, body);
      Context parentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(parentContext, natsRequest);
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

      Throwable error = throwable;
      NatsRequest natsResponse = null;
      if (message == null) {
        error = new TimeoutException("Timed out waiting for message");
      } else {
        natsResponse = NatsRequest.create(connection, message);
      }

      otelScope.close();
      CLIENT_INSTRUMENTER.end(otelContext, natsRequest, natsResponse, error);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestBodyHeadersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers headers,
        @Advice.Argument(2) byte[] body,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, subject, headers, body);
      Context parentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(parentContext, natsRequest);
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

      Throwable error = throwable;
      NatsRequest natsResponse = null;
      if (message == null) {
        error = new TimeoutException("Timed out waiting for message");
      } else {
        natsResponse = NatsRequest.create(connection, message);
      }

      otelScope.close();
      CLIENT_INSTRUMENTER.end(otelContext, natsRequest, natsResponse, error);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, message);
      Context parentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(parentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(parentContext, natsRequest);
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

      Throwable error = throwable;
      NatsRequest natsResponse = null;
      if (message == null) {
        error = new TimeoutException("Timed out waiting for message");
      } else {
        natsResponse = NatsRequest.create(connection, message);
      }

      otelScope.close();
      CLIENT_INSTRUMENTER.end(otelContext, natsRequest, natsResponse, error);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureBodyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, subject, body);
      otelParentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(otelParentContext, natsRequest);
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
        CLIENT_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture.whenComplete(
            new MessageConsumer(CLIENT_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureBodyHeadersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers headers,
        @Advice.Argument(2) byte[] body,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, subject, headers, body);
      otelParentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(otelParentContext, natsRequest);
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
        CLIENT_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture.whenComplete(
            new MessageConsumer(CLIENT_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, message);
      otelParentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(otelParentContext, natsRequest);
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
        CLIENT_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture.whenComplete(
            new MessageConsumer(CLIENT_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureTimeoutBodyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) byte[] body,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, subject, body);
      otelParentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(otelParentContext, natsRequest);
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
        CLIENT_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture.whenComplete(
            new MessageConsumer(CLIENT_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureTimeoutBodyHeadersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) String subject,
        @Advice.Argument(1) Headers headers,
        @Advice.Argument(2) byte[] body,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, subject, headers, body);
      otelParentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(otelParentContext, natsRequest);
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
        CLIENT_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture.whenComplete(
            new MessageConsumer(CLIENT_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RequestFutureTimeoutMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Message message,
        @Advice.Local("otelContext") Context otelContext,
        @Advice.Local("otelParentContext") Context otelParentContext,
        @Advice.Local("otelScope") Scope otelScope,
        @Advice.Local("natsRequest") NatsRequest natsRequest) {
      natsRequest = NatsRequest.create(connection, message);
      otelParentContext = Context.current();

      if (!CLIENT_INSTRUMENTER.shouldStart(otelParentContext, natsRequest)) {
        return;
      }

      otelContext = CLIENT_INSTRUMENTER.start(otelParentContext, natsRequest);
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
        CLIENT_INSTRUMENTER.end(otelContext, natsRequest, null, throwable);
      } else {
        messageFuture.whenComplete(
            new MessageConsumer(CLIENT_INSTRUMENTER, otelContext, connection, natsRequest));
        messageFuture = CompletableFutureWrapper.wrap(messageFuture, otelParentContext);
      }
    }
  }
}
