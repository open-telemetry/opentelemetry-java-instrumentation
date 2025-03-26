/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

final class TracingClientInterceptor implements ClientInterceptor {

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
  private static final AtomicLongFieldUpdater<TracingClientCall> SENT_MESSAGE_ID_UPDATER =
      AtomicLongFieldUpdater.newUpdater(TracingClientCall.class, "sentMessageId");

  @SuppressWarnings("rawtypes")
  private static final AtomicLongFieldUpdater<TracingClientCall> RECEIVED_MESSAGE_ID_UPDATER =
      AtomicLongFieldUpdater.newUpdater(TracingClientCall.class, "receivedMessageId");

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final ContextPropagators propagators;
  private final boolean captureExperimentalSpanAttributes;
  private final boolean emitMessageEvents;

  TracingClientInterceptor(
      Instrumenter<GrpcRequest, Status> instrumenter,
      ContextPropagators propagators,
      boolean captureExperimentalSpanAttributes,
      boolean emitMessageEvents) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.emitMessageEvents = emitMessageEvents;
  }

  @Override
  public <REQUEST, RESPONSE> ClientCall<REQUEST, RESPONSE> interceptCall(
      MethodDescriptor<REQUEST, RESPONSE> method, CallOptions callOptions, Channel next) {
    GrpcRequest request = new GrpcRequest(method, null, null, next.authority());
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return next.newCall(method, callOptions);
    }

    Context context = instrumenter.start(parentContext, request);
    ClientCall<REQUEST, RESPONSE> result;
    try (Scope ignored = context.makeCurrent()) {
      // call other interceptors
      result = next.newCall(method, callOptions);
    } catch (Throwable t) {
      instrumenter.end(context, request, Status.UNKNOWN, t);
      throw t;
    }

    return new TracingClientCall<>(result, parentContext, context, request);
  }

  final class TracingClientCall<REQUEST, RESPONSE>
      extends ForwardingClientCall.SimpleForwardingClientCall<REQUEST, RESPONSE> {

    private final Context parentContext;
    private final Context context;
    private final GrpcRequest request;

    // Used by SENT_MESSAGE_ID_UPDATER
    @SuppressWarnings("UnusedVariable")
    volatile long sentMessageId;

    // Used by RECEIVED_MESSAGE_ID_UPDATER
    @SuppressWarnings("UnusedVariable")
    volatile long receivedMessageId;

    TracingClientCall(
        ClientCall<REQUEST, RESPONSE> delegate,
        Context parentContext,
        Context context,
        GrpcRequest request) {
      super(delegate);
      this.parentContext = parentContext;
      this.context = context;
      this.request = request;
    }

    @Override
    public void start(Listener<RESPONSE> responseListener, Metadata headers) {
      propagators.getTextMapPropagator().inject(context, headers, MetadataSetter.INSTANCE);
      // store metadata so that it can be used by custom AttributesExtractors
      request.setMetadata(headers);
      try (Scope ignored = context.makeCurrent()) {
        super.start(
            new TracingClientCallListener(responseListener, parentContext, context, request),
            headers);
      } catch (Throwable e) {
        instrumenter.end(context, request, Status.UNKNOWN, e);
        throw e;
      }
    }

    @Override
    public void sendMessage(REQUEST message) {
      try (Scope ignored = context.makeCurrent()) {
        super.sendMessage(message);
      } catch (Throwable e) {
        instrumenter.end(context, request, Status.UNKNOWN, e);
        throw e;
      }
      long messageId = SENT_MESSAGE_ID_UPDATER.incrementAndGet(this);
      if (emitMessageEvents) {
        Attributes attributes = Attributes.of(MESSAGE_TYPE, SENT, MESSAGE_ID, messageId);
        Span.fromContext(context).addEvent("message", attributes);
      }
    }

    final class TracingClientCallListener
        extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RESPONSE> {

      private final Context parentContext;
      private final Context context;
      private final GrpcRequest request;

      TracingClientCallListener(
          Listener<RESPONSE> delegate,
          Context parentContext,
          Context context,
          GrpcRequest request) {
        super(delegate);
        this.parentContext = parentContext;
        this.context = context;
        this.request = request;
      }

      @Override
      public void onMessage(RESPONSE message) {
        long messageId = RECEIVED_MESSAGE_ID_UPDATER.incrementAndGet(TracingClientCall.this);
        if (emitMessageEvents) {
          Attributes attributes = Attributes.of(MESSAGE_TYPE, RECEIVED, MESSAGE_ID, messageId);
          Span.fromContext(context).addEvent("message", attributes);
        }
        try (Scope ignored = context.makeCurrent()) {
          delegate().onMessage(message);
        }
      }

      @Override
      public void onClose(Status status, Metadata trailers) {
        request.setPeerSocketAddress(getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
        if (captureExperimentalSpanAttributes) {
          Span span = Span.fromContext(context);
          span.setAttribute(
              GRPC_RECEIVED_MESSAGE_COUNT, RECEIVED_MESSAGE_ID_UPDATER.get(TracingClientCall.this));
          span.setAttribute(
              GRPC_SENT_MESSAGE_COUNT, SENT_MESSAGE_ID_UPDATER.get(TracingClientCall.this));
        }
        instrumenter.end(context, request, status, status.getCause());
        try (Scope ignored = parentContext.makeCurrent()) {
          delegate().onClose(status, trailers);
        }
      }

      @Override
      public void onReady() {
        try (Scope ignored = context.makeCurrent()) {
          delegate().onReady();
        }
      }
    }
  }
}
