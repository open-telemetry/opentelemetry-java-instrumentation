/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.lettuce.v5_1;

import io.grpc.Context;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import reactor.util.annotation.Nullable;

public enum OpenTelemetryTracing implements Tracing {
  INSTANCE;

  public static final io.opentelemetry.trace.Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.lettuce-5.1");

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

      return new OpenTelemetryEndpoint(
          address.getAddress().getHostAddress(), address.getPort(), address.getHostString());
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

    public Context getContext() {
      return context;
    }
  }

  private static class OpenTelemetryEndpoint implements Endpoint {
    final String ip;
    final int port;
    @Nullable final String name;

    OpenTelemetryEndpoint(String ip, int port, @Nullable String name) {
      this.ip = ip;
      this.port = port;
      if (!ip.equals(name)) {
        this.name = name;
      } else {
        this.name = null;
      }
    }
  }

  private static class OpenTelemetryTracer extends Tracer {

    OpenTelemetryTracer() {}

    @Override
    public OpenTelemetrySpan nextSpan() {
      return new OpenTelemetrySpan(TRACER.getCurrentSpan());
    }

    @Override
    public OpenTelemetrySpan nextSpan(TraceContext traceContext) {
      if (!(traceContext instanceof OpenTelemetryTraceContext)) {
        return nextSpan();
      }

      Context context = ((OpenTelemetryTraceContext) traceContext).getContext();

      io.opentelemetry.trace.Span parent = TracingContextUtils.getSpan(context);

      return new OpenTelemetrySpan(parent);
    }
  }

  // The order that callbacks will be called in or which thread they are called from is not well
  // defined. We go ahead and buffer all data until we know we have a span. This implementation is
  // particularly safe, synchronizing all accesses. Relying on implementation details would allow
  // reducing synchronization but the impact should be minimal.
  private static class OpenTelemetrySpan extends Tracer.Span {
    private final Span.Builder spanBuilder;

    @Nullable private String name;

    @Nullable private List<Object> events;

    @Nullable private Status status;

    @Nullable private Span span;

    @Nullable private String args;

    OpenTelemetrySpan(Span parent) {
      // Name will be updated later, we create with an arbitrary one here to store other data before
      // the span starts.
      spanBuilder =
          TRACER
              .spanBuilder("redis")
              .setSpanKind(Kind.CLIENT)
              .setParent(parent)
              .setAttribute(SemanticAttributes.DB_SYSTEM.key(), DbSystem.REDIS);
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
          fillEndpoint(span, (OpenTelemetryEndpoint) endpoint);
        } else {
          fillEndpoint(spanBuilder, (OpenTelemetryEndpoint) endpoint);
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
          span.addEvent((String) events.get(i), (long) events.get(i + 1));
        }
        events = null;
      }

      if (status != null) {
        span.setStatus(status);
        status = null;
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
        Instant now = Instant.now();
        events.add(TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano());
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
      // TODO(anuraaga): Check if any lettuce exceptions map well to a Status and try mapping.
      Status status =
          Status.UNKNOWN.withDescription(throwable.getClass() + ": " + throwable.getMessage());
      if (span != null) {
        span.setStatus(status);
      } else {
        this.status = status;
      }
      return this;
    }

    @Override
    public synchronized void finish() {
      if (span != null) {
        if (name != null) {
          String statement = args != null && !args.isEmpty() ? name + " " + args : name;
          SemanticAttributes.DB_STATEMENT.set(span, statement);
        }
        span.end();
      }
    }

    private static void fillEndpoint(Span.Builder span, OpenTelemetryEndpoint endpoint) {
      span.setAttribute(SemanticAttributes.NET_TRANSPORT.key(), "IP.TCP");
      span.setAttribute(SemanticAttributes.NET_PEER_IP.key(), endpoint.ip);

      StringBuilder redisUrl = new StringBuilder("redis://");

      if (endpoint.name != null) {
        span.setAttribute(SemanticAttributes.NET_PEER_NAME.key(), endpoint.name);
        redisUrl.append(endpoint.name);
      } else {
        redisUrl.append(endpoint.ip);
      }

      if (endpoint.port != 0) {
        span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), endpoint.port);
        redisUrl.append(":").append(endpoint.port);
      }

      span.setAttribute(SemanticAttributes.DB_CONNECTION_STRING.key(), redisUrl.toString());
    }

    private static void fillEndpoint(Span span, OpenTelemetryEndpoint endpoint) {
      span.setAttribute(SemanticAttributes.NET_TRANSPORT.key(), "IP.TCP");
      span.setAttribute(SemanticAttributes.NET_PEER_IP.key(), endpoint.ip);

      StringBuilder redisUrl = new StringBuilder("redis://");

      if (endpoint.name != null) {
        span.setAttribute(SemanticAttributes.NET_PEER_NAME.key(), endpoint.name);
        redisUrl.append(endpoint.name);
      } else {
        redisUrl.append(endpoint.ip);
      }

      if (endpoint.port != 0) {
        span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), endpoint.port);
        redisUrl.append(":").append(endpoint.port);
      }

      span.setAttribute(SemanticAttributes.DB_CONNECTION_STRING.key(), redisUrl.toString());
    }
  }
}
