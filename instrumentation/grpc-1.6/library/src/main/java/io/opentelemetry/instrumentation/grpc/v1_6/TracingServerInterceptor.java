/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class TracingServerInterceptor implements ServerInterceptor {

  private static final AttributeKey<Boolean> GRPC_CANCELED =
      AttributeKey.booleanKey("grpc.canceled");
  private static final AttributeKey<Long> GRPC_RECEIVED_MESSAGE_COUNT =
      AttributeKey.longKey("grpc.received.message_count");
  private static final AttributeKey<Long> GRPC_SENT_MESSAGE_COUNT =
      AttributeKey.longKey("grpc.sent.message_count");
  // copied from MessageIncubatingAttributes
  private static final AttributeKey<Long> MESSAGE_ID = AttributeKey.longKey("message.id");
  private static final AttributeKey<String> MESSAGE_TYPE = AttributeKey.stringKey("message.type");
  // copied from MessageIncubatingAttributes.MessageTypeValues
  private static final String SENT = "SENT";
  private static final String RECEIVED = "RECEIVED";

  @SuppressWarnings("rawtypes")
  private static final AtomicLongFieldUpdater<TracingServerCall> SENT_MESSAGE_ID_UPDATER =
      AtomicLongFieldUpdater.newUpdater(TracingServerCall.class, "sentMessageId");

  @SuppressWarnings("rawtypes")
  private static final AtomicLongFieldUpdater<TracingServerCall> RECEIVED_MESSAGE_ID_UPDATER =
      AtomicLongFieldUpdater.newUpdater(TracingServerCall.class, "receivedMessageId");

  private static final VirtualField<ServerCall<?, ?>, String> AUTHORITY_FIELD =
      VirtualField.find(ServerCall.class, String.class);

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final boolean captureExperimentalSpanAttributes;
  private final boolean emitMessageEvents;

  TracingServerInterceptor(
      Instrumenter<GrpcRequest, Status> instrumenter,
      boolean captureExperimentalSpanAttributes,
      boolean emitMessageEvents) {
    this.instrumenter = instrumenter;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.emitMessageEvents = emitMessageEvents;
  }

  @Override
  public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(
      ServerCall<REQUEST, RESPONSE> call,
      Metadata headers,
      ServerCallHandler<REQUEST, RESPONSE> next) {
    String authority = call.getAuthority();
    if (authority == null) {
      // Armeria grpc server call does not implement getAuthority(). In
      // ArmeriaServerCallInstrumentation we store the value for the authority header in a virtual
      // field.
      authority = AUTHORITY_FIELD.get(call);
    }
    GrpcRequest request =
        new GrpcRequest(
            call.getMethodDescriptor(),
            headers,
            call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR),
            authority);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return next.startCall(call, headers);
    }

    Context context = instrumenter.start(parentContext, request);

    try (Scope ignored = context.makeCurrent()) {
      return new TracingServerCall<>(call, context, request).start(headers, next);
    } catch (Throwable e) {
      instrumenter.end(context, request, Status.UNKNOWN, e);
      throw e;
    }
  }

  final class TracingServerCall<REQUEST, RESPONSE>
      extends ForwardingServerCall.SimpleForwardingServerCall<REQUEST, RESPONSE> {
    private final Context context;
    private final GrpcRequest request;
    private Status status;

    // Used by SENT_MESSAGE_ID_UPDATER
    @SuppressWarnings("UnusedVariable")
    volatile long sentMessageId;

    // Used by RECEIVED_MESSAGE_ID_UPDATER
    @SuppressWarnings("UnusedVariable")
    volatile long receivedMessageId;

    TracingServerCall(
        ServerCall<REQUEST, RESPONSE> delegate, Context context, GrpcRequest request) {
      super(delegate);
      this.context = context;
      this.request = request;
    }

    TracingServerCallListener start(Metadata headers, ServerCallHandler<REQUEST, RESPONSE> next) {
      return new TracingServerCallListener(
          Contexts.interceptCall(io.grpc.Context.current(), this, headers, next), context, request);
    }

    @Override
    public void sendMessage(RESPONSE message) {
      try (Scope ignored = context.makeCurrent()) {
        super.sendMessage(message);
      }
      long messageId = SENT_MESSAGE_ID_UPDATER.incrementAndGet(this);
      if (emitMessageEvents) {
        Attributes attributes = Attributes.of(MESSAGE_TYPE, SENT, MESSAGE_ID, messageId);
        Span.fromContext(context).addEvent("message", attributes);
      }
    }

    @Override
    public void close(Status status, Metadata trailers) {
      this.status = status;
      try {
        delegate().close(status, trailers);
      } catch (Throwable e) {
        instrumenter.end(context, request, status, e);
        throw e;
      }
    }

    final class TracingServerCallListener
        extends ForwardingServerCallListener.SimpleForwardingServerCallListener<REQUEST> {
      private final Context context;
      private final GrpcRequest request;

      TracingServerCallListener(Listener<REQUEST> delegate, Context context, GrpcRequest request) {
        super(delegate);
        this.context = context;
        this.request = request;
      }

      private void end(Context context, GrpcRequest request, Status response, Throwable error) {
        if (captureExperimentalSpanAttributes) {
          Span span = Span.fromContext(context);
          span.setAttribute(
              GRPC_RECEIVED_MESSAGE_COUNT, RECEIVED_MESSAGE_ID_UPDATER.get(TracingServerCall.this));
          span.setAttribute(
              GRPC_SENT_MESSAGE_COUNT, SENT_MESSAGE_ID_UPDATER.get(TracingServerCall.this));
          if (Status.CANCELLED.equals(status)) {
            span.setAttribute(GRPC_CANCELED, true);
          }
        }
        instrumenter.end(context, request, response, error);
      }

      @Override
      public void onMessage(REQUEST message) {
        long messageId = RECEIVED_MESSAGE_ID_UPDATER.incrementAndGet(TracingServerCall.this);
        if (emitMessageEvents) {
          Attributes attributes = Attributes.of(MESSAGE_TYPE, RECEIVED, MESSAGE_ID, messageId);
          Span.fromContext(context).addEvent("message", attributes);
        }
        delegate().onMessage(message);
      }

      @Override
      public void onHalfClose() {
        try {
          delegate().onHalfClose();
        } catch (Throwable e) {
          instrumenter.end(context, request, Status.UNKNOWN, e);
          throw e;
        }
      }

      @Override
      public void onCancel() {
        try {
          delegate().onCancel();
        } catch (Throwable e) {
          end(context, request, Status.UNKNOWN, e);
          throw e;
        }
        end(context, request, Status.CANCELLED, null);
      }

      @Override
      public void onComplete() {
        try {
          delegate().onComplete();
        } catch (Throwable e) {
          end(context, request, Status.UNKNOWN, e);
          throw e;
        }
        if (status == null) {
          status = Status.UNKNOWN;
        }
        end(context, request, status, status.getCause());
      }

      @Override
      public void onReady() {
        try {
          delegate().onReady();
        } catch (Throwable e) {
          end(context, request, Status.UNKNOWN, e);
          throw e;
        }
      }
    }
  }
}
