/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.javaagent.instrumentation.lettuce.LettuceArgSplitter.splitArgs;

import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils.SpanAttributeSetter;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public enum OpenTelemetryTracing implements Tracing {
  INSTANCE;

  private static final io.opentelemetry.api.trace.Tracer TRACER =
      GlobalOpenTelemetry.getTracer("io.opentelemetry.javaagent.lettuce-5.1");

  public static io.opentelemetry.api.trace.Tracer tracer() {
    return TRACER;
  }

  @Override
  public TracerProvider getTracerProvider() {
    return OpenTelemetryTracerProvider.INSTANCE;
  }

  @Override
  public TraceContextProvider initialTraceContextProvider() {
    return OpenTelemetryTraceContextProvider.INSTANCE;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  // Added in lettuce 5.2
  // @Override
  public boolean includeCommandArgsInSpanTags() {
    return true;
  }

  @Override
  public Endpoint createEndpoint(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress address = (InetSocketAddress) socketAddress;

      String ip = address.getAddress() == null ? null : address.getAddress().getHostAddress();
      return new OpenTelemetryEndpoint(ip, address.getPort(), address.getHostString());
    }
    return null;
  }

  private enum OpenTelemetryTracerProvider implements TracerProvider {
    INSTANCE;

    private final Tracer openTelemetryTracer = new OpenTelemetryTracer();

    @Override
    public Tracer getTracer() {
      return openTelemetryTracer;
    }
  }

  private enum OpenTelemetryTraceContextProvider implements TraceContextProvider {
    INSTANCE;

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

    OpenTelemetryTracer() {}

    @Override
    public OpenTelemetrySpan nextSpan() {
      return new OpenTelemetrySpan(Context.current());
    }

    @Override
    public OpenTelemetrySpan nextSpan(TraceContext traceContext) {
      if (!(traceContext instanceof OpenTelemetryTraceContext)) {
        return nextSpan();
      }

      Context context = ((OpenTelemetryTraceContext) traceContext).getSpanContext();

      return new OpenTelemetrySpan(context);
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

    OpenTelemetrySpan(Context parent) {
      // Name will be updated later, we create with an arbitrary one here to store other data before
      // the span starts.
      spanBuilder =
          TRACER
              .spanBuilder("redis")
              .setSpanKind(SpanKind.CLIENT)
              .setParent(parent)
              .setAttribute(SemanticAttributes.DB_SYSTEM, DbSystemValues.REDIS);
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
        if (name != null) {
          String statement = RedisCommandSanitizer.sanitize(name, splitArgs(args));
          span.setAttribute(SemanticAttributes.DB_STATEMENT, statement);
        }
        span.end();
      }
    }

    private static void fillEndpoint(SpanAttributeSetter span, OpenTelemetryEndpoint endpoint) {
      span.setAttribute(SemanticAttributes.NET_TRANSPORT, "IP.TCP");
      NetPeerUtils.INSTANCE.setNetPeer(span, endpoint.name, endpoint.ip, endpoint.port);

      StringBuilder redisUrl =
          new StringBuilder("redis://").append(endpoint.name != null ? endpoint.name : endpoint.ip);
      if (endpoint.port > 0) {
        redisUrl.append(":").append(endpoint.port);
      }

      span.setAttribute(SemanticAttributes.DB_CONNECTION_STRING, redisUrl.toString());
    }
  }
}
