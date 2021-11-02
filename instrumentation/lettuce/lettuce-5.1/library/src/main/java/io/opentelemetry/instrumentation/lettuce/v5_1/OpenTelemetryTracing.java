/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter.splitArgs;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;

import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CompleteableCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.tracer.AttributeSetter;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class OpenTelemetryTracing implements Tracing {

  private final TracerProvider tracerProvider;

  OpenTelemetryTracing(io.opentelemetry.api.trace.Tracer tracer) {
    this.tracerProvider = new OpenTelemetryTracerProvider(tracer);
  }

  @Override
  public TracerProvider getTracerProvider() {
    return tracerProvider;
  }

  @Override
  public TraceContextProvider initialTraceContextProvider() {
    return new OpenTelemetryTraceContextProvider();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  // Added in lettuce 5.2, ignored in 6.0+
  // @Override
  public boolean includeCommandArgsInSpanTags() {
    return true;
  }

  @Override
  @Nullable
  public Endpoint createEndpoint(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress address = (InetSocketAddress) socketAddress;

      String ip = address.getAddress() == null ? null : address.getAddress().getHostAddress();
      return new OpenTelemetryEndpoint(ip, address.getPort(), address.getHostString());
    }
    return null;
  }

  private static class OpenTelemetryTracerProvider implements TracerProvider {

    private final Tracer openTelemetryTracer;

    OpenTelemetryTracerProvider(io.opentelemetry.api.trace.Tracer tracer) {
      openTelemetryTracer = new OpenTelemetryTracer(tracer);
    }

    @Override
    public Tracer getTracer() {
      return openTelemetryTracer;
    }
  }

  private static class OpenTelemetryTraceContextProvider implements TraceContextProvider {

    @Override
    public TraceContext getTraceContext() {
      return new OpenTelemetryTraceContext();
    }
  }

  private static class OpenTelemetryTraceContext implements TraceContext {
    private final Context context;

    OpenTelemetryTraceContext() {
      this.context = Context.current();
    }

    public Context getSpanContext() {
      return context;
    }
  }

  private static class OpenTelemetryEndpoint implements Endpoint {
    @Nullable final String ip;
    final int port;
    @Nullable final String name;

    OpenTelemetryEndpoint(@Nullable String ip, int port, @Nullable String name) {
      this.ip = ip;
      this.port = port;
      this.name = name;
    }
  }

  private static class OpenTelemetryTracer extends Tracer {

    private final io.opentelemetry.api.trace.Tracer tracer;

    OpenTelemetryTracer(io.opentelemetry.api.trace.Tracer tracer) {
      this.tracer = tracer;
    }

    @Override
    public OpenTelemetrySpan nextSpan() {
      return nextSpan(Context.current());
    }

    @Override
    public OpenTelemetrySpan nextSpan(TraceContext traceContext) {
      if (!(traceContext instanceof OpenTelemetryTraceContext)) {
        return nextSpan();
      }

      Context context = ((OpenTelemetryTraceContext) traceContext).getSpanContext();
      return nextSpan(context);
    }

    private OpenTelemetrySpan nextSpan(Context context) {
      // Name will be updated later, we create with an arbitrary one here to store other data before
      // the span starts.
      SpanBuilder spanBuilder =
          tracer
              .spanBuilder("redis")
              .setSpanKind(SpanKind.CLIENT)
              .setParent(context)
              .setAttribute(SemanticAttributes.DB_SYSTEM, DbSystemValues.REDIS);
      return new OpenTelemetrySpan(spanBuilder);
    }
  }

  // The order that callbacks will be called in or which thread they are called from is not well
  // defined. We go ahead and buffer all data until we know we have a span. This implementation is
  // particularly safe, synchronizing all accesses. Relying on implementation details would allow
  // reducing synchronization but the impact should be minimal.
  private static class OpenTelemetrySpan extends Tracer.Span {
    private final SpanBuilder spanBuilder;

    @Nullable private String name;

    @Nullable private List<Object> events;

    @Nullable private Throwable error;

    @Nullable private Span span;

    @Nullable private String args;

    OpenTelemetrySpan(SpanBuilder spanBuilder) {
      this.spanBuilder = spanBuilder;
    }

    @Override
    public synchronized Tracer.Span name(String name) {
      if (span != null) {
        span.updateName(name);
      }

      this.name = name;

      return this;
    }

    @Override
    public synchronized Tracer.Span remoteEndpoint(Endpoint endpoint) {
      if (endpoint instanceof OpenTelemetryEndpoint) {
        if (span != null) {
          fillEndpoint(span::setAttribute, (OpenTelemetryEndpoint) endpoint);
        } else {
          fillEndpoint(spanBuilder::setAttribute, (OpenTelemetryEndpoint) endpoint);
        }
      }
      return this;
    }

    // Added and called in 6.0+
    // @Override
    public synchronized Tracer.Span start(RedisCommand<?, ?, ?> command) {
      start();

      Span span = this.span;
      if (span == null) {
        throw new IllegalStateException("Span started but null, this is a programming error.");
      }
      span.updateName(command.getType().name());

      if (command.getArgs() != null) {
        args = command.getArgs().toCommandString();
      }

      if (command instanceof CompleteableCommand) {
        CompleteableCommand<?> completeableCommand = (CompleteableCommand<?>) command;
        completeableCommand.onComplete(
            (o, throwable) -> {
              if (throwable != null) {
                span.recordException(throwable);
              }

              CommandOutput<?, ?, ?> output = command.getOutput();
              if (output != null) {
                String error = output.getError();
                if (error != null) {
                  span.setStatus(StatusCode.ERROR, error);
                }
              }

              finish(span);
            });
      }

      return this;
    }

    // Not called by Lettuce in 6.0+ (though we call it ourselves above).
    @Override
    public synchronized Tracer.Span start() {
      span = spanBuilder.startSpan();
      if (name != null) {
        span.updateName(name);
      }

      if (events != null) {
        for (int i = 0; i < events.size(); i += 2) {
          span.addEvent((String) events.get(i), (Instant) events.get(i + 1));
        }
        events = null;
      }

      if (error != null) {
        span.setStatus(StatusCode.ERROR);
        span.recordException(error);
        error = null;
      }

      return this;
    }

    @Override
    public synchronized Tracer.Span annotate(String value) {
      if (span != null) {
        span.addEvent(value);
      } else {
        if (events == null) {
          events = new ArrayList<>();
        }
        events.add(value);
        events.add(Instant.now());
      }
      return this;
    }

    @Override
    public synchronized Tracer.Span tag(String key, String value) {
      if (key.equals("redis.args")) {
        args = value;
        return this;
      }
      if (span != null) {
        span.setAttribute(key, value);
      } else {
        spanBuilder.setAttribute(key, value);
      }
      return this;
    }

    @Override
    public synchronized Tracer.Span error(Throwable throwable) {
      if (span != null) {
        span.recordException(throwable);
      } else {
        this.error = throwable;
      }
      return this;
    }

    @Override
    public synchronized void finish() {
      if (span != null) {
        finish(span);
      }
    }

    private void finish(Span span) {
      if (name != null) {
        String statement = RedisCommandSanitizer.sanitize(name, splitArgs(args));
        span.setAttribute(SemanticAttributes.DB_STATEMENT, statement);
      }
      span.end();
    }
  }

  private static void fillEndpoint(AttributeSetter span, OpenTelemetryEndpoint endpoint) {
    span.setAttribute(SemanticAttributes.NET_TRANSPORT, IP_TCP);
    NetPeerAttributes.INSTANCE.setNetPeer(span, endpoint.name, endpoint.ip, endpoint.port);
  }
}
