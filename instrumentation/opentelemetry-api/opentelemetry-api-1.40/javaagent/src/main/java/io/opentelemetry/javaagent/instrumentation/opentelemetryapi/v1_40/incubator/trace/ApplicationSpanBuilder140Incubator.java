/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import application.io.opentelemetry.api.incubator.trace.SpanCallable;
import application.io.opentelemetry.api.incubator.trace.SpanRunnable;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanContext;
import application.io.opentelemetry.api.trace.SpanKind;
import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import application.io.opentelemetry.context.propagation.TextMapGetter;
import application.io.opentelemetry.context.propagation.TextMapPropagator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpan;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpanBuilder;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

public final class ApplicationSpanBuilder140Incubator extends ApplicationSpanBuilder
    implements ExtendedSpanBuilder {

  private final io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder agentBuilder;

  public ApplicationSpanBuilder140Incubator(io.opentelemetry.api.trace.SpanBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = (io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder) agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setParent(Context applicationContext) {
    return (ExtendedSpanBuilder) super.setParent(applicationContext);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setNoParent() {
    return (ExtendedSpanBuilder) super.setNoParent();
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder addLink(SpanContext applicationSpanContext) {
    return (ExtendedSpanBuilder) super.addLink(applicationSpanContext);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder addLink(
      SpanContext applicationSpanContext, Attributes applicationAttributes) {
    return (ExtendedSpanBuilder) super.addLink(applicationSpanContext, applicationAttributes);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setAttribute(String key, String value) {
    return (ExtendedSpanBuilder) super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setAttribute(String key, long value) {
    return (ExtendedSpanBuilder) super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setAttribute(String key, double value) {
    return (ExtendedSpanBuilder) super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setAttribute(String key, boolean value) {
    return (ExtendedSpanBuilder) super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public <T> ExtendedSpanBuilder setAttribute(AttributeKey<T> applicationKey, T value) {
    return (ExtendedSpanBuilder) super.setAttribute(applicationKey, value);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setSpanKind(SpanKind applicationSpanKind) {
    return (ExtendedSpanBuilder) super.setSpanKind(applicationSpanKind);
  }

  @Override
  @CanIgnoreReturnValue
  public ExtendedSpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
    return (ExtendedSpanBuilder) super.setStartTimestamp(startTimestamp, unit);
  }

  @Override
  public ExtendedSpanBuilder setParentFrom(
      ContextPropagators contextPropagators, Map<String, String> map) {
    agentBuilder.setParentFrom(new AgentContextPropagators(contextPropagators), map);
    return this;
  }

  @Override
  public <T, E extends Throwable> T startAndCall(SpanCallable<T, E> spanCallable) throws E {
    return agentBuilder.startAndCall(spanCallable::callInSpan);
  }

  @Override
  public <T, E extends Throwable> T startAndCall(
      SpanCallable<T, E> spanCallable, BiConsumer<Span, Throwable> biConsumer) throws E {
    return agentBuilder.startAndCall(
        spanCallable::callInSpan,
        (span, throwable) -> biConsumer.accept(new ApplicationSpan(span), throwable));
  }

  @Override
  public <E extends Throwable> void startAndRun(SpanRunnable<E> spanRunnable) throws E {
    agentBuilder.startAndRun(spanRunnable::runInSpan);
  }

  @Override
  public <E extends Throwable> void startAndRun(
      SpanRunnable<E> spanRunnable, BiConsumer<Span, Throwable> biConsumer) throws E {
    agentBuilder.startAndRun(
        spanRunnable::runInSpan,
        (span, throwable) -> biConsumer.accept(new ApplicationSpan(span), throwable));
  }

  private static class AgentContextPropagators
      implements io.opentelemetry.context.propagation.ContextPropagators {

    private final ContextPropagators applicationContextPropagators;

    AgentContextPropagators(ContextPropagators applicationContextPropagators) {
      this.applicationContextPropagators = applicationContextPropagators;
    }

    @Override
    public io.opentelemetry.context.propagation.TextMapPropagator getTextMapPropagator() {
      return new AgentTextMapPropagator(applicationContextPropagators.getTextMapPropagator());
    }
  }

  private static class AgentTextMapPropagator
      implements io.opentelemetry.context.propagation.TextMapPropagator {

    private final TextMapPropagator applicationTextMapPropagator;

    AgentTextMapPropagator(TextMapPropagator applicationTextMapPropagator) {
      this.applicationTextMapPropagator = applicationTextMapPropagator;
    }

    @Override
    public Collection<String> fields() {
      return applicationTextMapPropagator.fields();
    }

    @Override
    public <C> void inject(
        io.opentelemetry.context.Context context,
        @Nullable C c,
        io.opentelemetry.context.propagation.TextMapSetter<C> textMapSetter) {
      applicationTextMapPropagator.inject(
          AgentContextStorage.toApplicationContext(context), c, textMapSetter::set);
    }

    @Override
    public <C> io.opentelemetry.context.Context extract(
        io.opentelemetry.context.Context context,
        @Nullable C c,
        io.opentelemetry.context.propagation.TextMapGetter<C> textMapGetter) {
      return AgentContextStorage.getAgentContext(
          applicationTextMapPropagator.extract(
              AgentContextStorage.toApplicationContext(context),
              c,
              new TextMapGetter<C>() {

                @Override
                public Iterable<String> keys(C c) {
                  return textMapGetter.keys(c);
                }

                @Nullable
                @Override
                public String get(@Nullable C c, String s) {
                  return textMapGetter.get(c, s);
                }
              }));
    }
  }
}
