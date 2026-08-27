/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static java.nio.charset.StandardCharsets.US_ASCII;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import javax.annotation.Nullable;

final class OpenTelemetryMessage implements InvocationHandler {
  private static final byte[] ACK_BODY = body("+ACK");
  private static final byte[] NAK_BODY = body("-NAK");
  private static final byte[] IN_PROGRESS_BODY = body("+WPI");
  private static final byte[] TERM_BODY = body("+TERM");

  private final Message delegate;
  private final Instrumenter<NatsRequest, NatsRequest> settleInstrumenter;

  static Message wrap(Message message, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    if (Proxy.isProxyClass(message.getClass())
        && Proxy.getInvocationHandler(message) instanceof OpenTelemetryMessage) {
      return message;
    }
    if (!message.isJetStream()) {
      return message;
    }
    return (Message)
        Proxy.newProxyInstance(
            Message.class.getClassLoader(),
            new Class<?>[] {Message.class},
            new OpenTelemetryMessage(message, settleInstrumenter));
  }

  private OpenTelemetryMessage(
      Message delegate, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    this.delegate = delegate;
    this.settleInstrumenter = settleInstrumenter;
  }

  @Override
  public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
    String methodName = method.getName();
    if (methodName.equals("ack") && method.getParameterCount() == 0) {
      return runSettlement(ACK_BODY, method, args);
    }
    if (methodName.equals("ackSync") && method.getParameterCount() == 1) {
      return runSettlement(ACK_BODY, method, args);
    }
    if (methodName.equals("nak") && method.getParameterCount() == 0) {
      return runSettlement(NAK_BODY, method, args);
    }
    if (methodName.equals("nakWithDelay") && method.getParameterCount() == 1) {
      Object delay = args[0];
      byte[] body =
          delay instanceof Duration
              ? nakBody(((Duration) delay).toNanos())
              : nakBody(((Long) delay) * 1_000_000);
      return runSettlement(body, method, args);
    }
    if (methodName.equals("inProgress") && method.getParameterCount() == 0) {
      return runSettlement(IN_PROGRESS_BODY, method, args);
    }
    if (methodName.equals("term") && method.getParameterCount() == 0) {
      return runSettlement(TERM_BODY, method, args);
    }
    if (methodName.equals("getSubscription") && method.getParameterCount() == 0) {
      Subscription subscription = (Subscription) invokeMethod(method, delegate, args);
      return subscription == null
          ? null
          : OpenTelemetrySubscription.wrap(subscription, settleInstrumenter);
    }
    return invokeMethod(method, delegate, args);
  }

  private Object runSettlement(byte[] body, Method method, @Nullable Object[] args)
      throws Throwable {
    SettlementScope settlement = startSettlement(body);
    if (settlement == null) {
      return invokeMethod(method, delegate, args);
    }

    Throwable error = null;
    try (Scope ignored = settlement.context.makeCurrent()) {
      return invokeMethod(method, delegate, args);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      endSettlement(settlement, error);
    }
  }

  private SettlementScope startSettlement(byte[] body) {
    Connection connection = delegate.getConnection();
    String replyTo = delegate.getReplyTo();
    if (connection == null || replyTo == null) {
      return null;
    }

    NatsRequest natsRequest = NatsRequest.create(connection, replyTo, null, null, body);
    Context parentContext = Context.current();
    if (!settleInstrumenter.shouldStart(parentContext, natsRequest)) {
      return null;
    }
    Context context = settleInstrumenter.start(parentContext, natsRequest);
    return new SettlementScope(settleInstrumenter, natsRequest, context);
  }

  private static void endSettlement(SettlementScope settlement, @Nullable Throwable error) {
    settlement.instrumenter.end(settlement.context, settlement.request, null, error);
  }

  private static Object invokeMethod(Method method, Message target, @Nullable Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static byte[] body(String body) {
    return body.getBytes(US_ASCII);
  }

  private static byte[] nakBody(long delayNanos) {
    if (delayNanos < 1) {
      return NAK_BODY;
    }
    return body("-NAK {\"delay\":" + delayNanos + "}");
  }

  private static final class SettlementScope {
    private final Instrumenter<NatsRequest, NatsRequest> instrumenter;
    private final NatsRequest request;
    private final Context context;

    private SettlementScope(
        Instrumenter<NatsRequest, NatsRequest> instrumenter, NatsRequest request, Context context) {
      this.instrumenter = instrumenter;
      this.request = request;
      this.context = context;
    }
  }
}
