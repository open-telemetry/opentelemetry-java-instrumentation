package io.opentelemetry.auto.instrumentation.lettuce.v5_2;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.grpc.Context;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

public class OpenTelemetryTracing implements Tracing
{
  private static final TracerProviderRedisAdapter TRACER_PROVIDER_REDIS_ADAPTER_INSTANCE = new TracerProviderRedisAdapter();

  @Override
  public TracerProvider getTracerProvider()
  {
    return TRACER_PROVIDER_REDIS_ADAPTER_INSTANCE;
  }

  @Override
  public TraceContextProvider initialTraceContextProvider()
  {
    return new TraceContextProviderRedisAdapter();
  }

  @Override
  public boolean isEnabled()
  {
    return true;
  }

  @Override
  public boolean includeCommandArgsInSpanTags()
  {
    return true;
  }

  @Override
  public Endpoint createEndpoint(SocketAddress socketAddress)
  {
    return null;
  }

  public static class TraceContextProviderRedisAdapter implements TraceContextProvider
  {

    @Override
    public TraceContext getTraceContext()
    {
      return new TraceContextRedisAdapter();
    }
  }

  public static class TraceContextRedisAdapter implements TraceContext
  {
    private final Context context;

    TraceContextRedisAdapter()
    {
      this.context = Context.current();
    }

    public Context getContext()
    {
      return context;
    }
  }

  public static class TracerProviderRedisAdapter implements io.lettuce.core.tracing.TracerProvider
  {

    @Override
    public Tracer getTracer()
    {
      return new TracerRedisAdapter();
    }
  }

  public static class TracerRedisAdapter extends Tracer
  {

    @Override
    public SpanRedisAdapter nextSpan()
    {
      return new SpanRedisAdapter();
    }

    @Override
    public io.opentelemetry.trace.Span nextSpan(TraceContext traceContext)
    {
      return new SpanRedisAdapter(traceContext);
    }
  }

  public static class SpanRedisAdapter extends Tracer.Span
  {
    List<String> events = new ArrayList<>();

    private Span.Builder spanBuilder;
    private final Context context;
    private Span span;
    private String name;

    private Scope scope = null;

    public SpanRedisAdapter(TraceContext traceContext)
    {
      super();
      if (!(traceContext instanceof TraceContextRedisAdapter))
      {
        throw new RuntimeException("Invalid type given: " + traceContext);
      }
      this.spanBuilder = OpenTelemetry.getTracerProvider().get("com.trace.adapter.redis")
          .spanBuilder("RedisTracingAdapter");

      this.context = ((TraceContextRedisAdapter)traceContext).getContext();
      this.span = null;
    }

    public SpanRedisAdapter()
    {
      super();
      this.spanBuilder = OpenTelemetry.getTracerProvider().get("com.trace.adapter.redis")
          .spanBuilder("RedisTracingAdapter");
      this.context = null;
    }

    @Override
    public Tracer.Span start()
    {
      span = spanBuilder.startSpan();
      if (context != null)
      {
        // Cannot support passing context between threads so ignoring the context here for now
      }
      events.forEach(event -> span.addEvent(event));
      if (name != null)
      {
        span.updateName(name);
      }
      spanBuilder = null;
      return this;
    }

    @Override
    public Tracer.Span name(String name)
    {
      if (span == null)
      {
        this.name = name;
      }
      else
      {
        span.updateName(name);
      }
      return this;
    }

    @Override
    public Tracer.Span annotate(String value)
    {
      if (span == null)
      {
        events.add(value);
      }
      else
      {
        span.addEvent(value);
      }
      return this;
    }

    @Override
    public Tracer.Span tag(String key, String value)
    {
      if (span == null)
      {
        spanBuilder.setAttribute(key, value);
      }
      else
      {
        span.setAttribute(key, value);
      }
      return this;
    }

    @Override
    public Tracer.Span error(Throwable throwable)
    {
      tag("exception.class", throwable.getClass()
          .getName());
      tag("exception.msg", throwable.getMessage());
      return null;
    }

    @Override
    public Tracer.Span remoteEndpoint(Endpoint endpoint)
    {
      return this;
    }

    @Override
    public void finish()
    {
      if (span != null)
      {
        span.end();
      }
    }
  }
}