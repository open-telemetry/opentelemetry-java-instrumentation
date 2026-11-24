/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientHeadersContextTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void inlineHeadersRestoreOutgoingContext() {
    ContextKey<String> key = ContextKey.named("custom-value");
    Span callerSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("caller").startSpan();
    Context caller = Context.root().with(callerSpan).with(key, "caller");
    ControlledChannel channel = new ControlledChannel(true);
    ClientCall<Helloworld.Request, Helloworld.Response> call;
    try (Scope ignored = caller.makeCurrent()) {
      call =
          GrpcTelemetry.create(testing.getOpenTelemetry())
              .createClientInterceptor()
              .interceptCall(GreeterGrpc.METHOD_SAY_HELLO, CallOptions.DEFAULT, channel);
    }

    AtomicReference<Context> observed = new AtomicReference<>();
    Context startContext = Context.root().with(key, "start");
    try (Scope ignored = startContext.makeCurrent()) {
      call.start(
          new ClientCall.Listener<Helloworld.Response>() {
            @Override
            public void onHeaders(Metadata headers) {
              observed.set(Context.current());
            }
          },
          new Metadata());
      assertThat(Context.current()).isSameAs(startContext);
    }

    assertThat(observed.get()).isSameAs(caller);
    assertThat(Span.fromContext(observed.get())).isSameAs(callerSpan);
    assertThat(observed.get().get(key)).isEqualTo("caller");
    assertThat(channel.beforeHeaders).isNotNull();
    assertThat(Span.fromContext(channel.beforeHeaders).getSpanContext().isValid()).isTrue();
    assertThat(Span.fromContext(channel.beforeHeaders)).isNotSameAs(callerSpan);
    assertThat(channel.afterHeaders).isSameAs(channel.beforeHeaders);
    channel.close();
    callerSpan.end();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deferredHeadersRestoreDeliveryContext(boolean throwFromCallback) {
    ContextKey<String> key = ContextKey.named("custom-value");
    Context caller = Context.root().with(key, "caller");
    ControlledChannel channel = new ControlledChannel(false);
    ClientCall<Helloworld.Request, Helloworld.Response> call;
    try (Scope ignored = caller.makeCurrent()) {
      call =
          GrpcTelemetry.create(testing.getOpenTelemetry())
              .createClientInterceptor()
              .interceptCall(GreeterGrpc.METHOD_SAY_HELLO, CallOptions.DEFAULT, channel);
    }

    RuntimeException failure = new RuntimeException("callback failure");
    AtomicReference<Context> observed = new AtomicReference<>();
    call.start(
        new ClientCall.Listener<Helloworld.Response>() {
          @Override
          public void onHeaders(Metadata headers) {
            observed.set(Context.current());
            if (throwFromCallback) {
              throw failure;
            }
          }
        },
        new Metadata());

    Context delivery = Context.root().with(key, "delivery");
    try (Scope ignored = delivery.makeCurrent()) {
      if (throwFromCallback) {
        assertThatThrownBy(() -> channel.headers()).isSameAs(failure);
      } else {
        channel.headers();
      }
      assertThat(Context.current()).isSameAs(delivery);
    }
    assertThat(observed.get()).isSameAs(caller);
    assertThat(observed.get().get(key)).isEqualTo("caller");
    assertThat(Span.fromContext(observed.get()).getSpanContext().isValid()).isFalse();
    channel.close();
  }

  private static final class ControlledChannel extends Channel {
    private final boolean inlineHeaders;
    private ClientCall.Listener<?> listener;
    private Context beforeHeaders;
    private Context afterHeaders;

    private ControlledChannel(boolean inlineHeaders) {
      this.inlineHeaders = inlineHeaders;
    }

    @Override
    public String authority() {
      return "localhost";
    }

    @Override
    public <REQUEST, RESPONSE> ClientCall<REQUEST, RESPONSE> newCall(
        MethodDescriptor<REQUEST, RESPONSE> method, CallOptions callOptions) {
      return new ClientCall<REQUEST, RESPONSE>() {
        @Override
        public void start(Listener<RESPONSE> responseListener, Metadata headers) {
          listener = responseListener;
          if (inlineHeaders) {
            beforeHeaders = Context.current();
            headers();
            afterHeaders = Context.current();
          }
        }

        @Override
        public void request(int numMessages) {}

        @Override
        public void cancel(String message, Throwable cause) {}

        @Override
        public void halfClose() {}

        @Override
        public void sendMessage(REQUEST message) {}
      };
    }

    private void headers() {
      listener.onHeaders(new Metadata());
    }

    private void close() {
      listener.onClose(Status.OK, new Metadata());
    }
  }
}
