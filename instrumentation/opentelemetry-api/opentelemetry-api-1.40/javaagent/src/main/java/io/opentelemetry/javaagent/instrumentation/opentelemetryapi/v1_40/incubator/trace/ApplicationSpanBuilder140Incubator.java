/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpan;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpanBuilder;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

public final class ApplicationSpanBuilder140Incubator extends ApplicationSpanBuilder
    implements application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder {

  private final ExtendedSpanBuilder agentBuilder;

  public ApplicationSpanBuilder140Incubator(SpanBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = (ExtendedSpanBuilder) agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setParent(
      application.io.opentelemetry.context.Context applicationContext) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setParent(applicationContext);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setNoParent() {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setNoParent();
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder addLink(
      application.io.opentelemetry.api.trace.SpanContext applicationSpanContext) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.addLink(applicationSpanContext);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder addLink(
      application.io.opentelemetry.api.trace.SpanContext applicationSpanContext,
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.addLink(applicationSpanContext, applicationAttributes);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setAttribute(
      String key, String value) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setAttribute(
      String key, long value) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setAttribute(
      String key, double value) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setAttribute(
      String key, boolean value) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public <T> application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setAttribute(
      application.io.opentelemetry.api.common.AttributeKey<T> applicationKey, T value) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setAttribute(applicationKey, value);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setSpanKind(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setSpanKind(applicationSpanKind);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setStartTimestamp(
      long startTimestamp, TimeUnit unit) {
    return (application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder)
        super.setStartTimestamp(startTimestamp, unit);
  }

  @Override
  public application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder setParentFrom(
      application.io.opentelemetry.context.propagation.ContextPropagators contextPropagators,
      Map<String, String> map) {
    agentBuilder.setParentFrom(new AgentContextPropagators(contextPropagators), map);
    return this;
  }

  @Override
  public <T, E extends Throwable> T startAndCall(
      application.io.opentelemetry.api.incubator.trace.SpanCallable<T, E> spanCallable) throws E {
    return agentBuilder.startAndCall(spanCallable::callInSpan);
  }

  @Override
  public <T, E extends Throwable> T startAndCall(
      application.io.opentelemetry.api.incubator.trace.SpanCallable<T, E> spanCallable,
      BiConsumer<application.io.opentelemetry.api.trace.Span, Throwable> biConsumer)
      throws E {
    return agentBuilder.startAndCall(
        spanCallable::callInSpan,
        (span, throwable) -> biConsumer.accept(new ApplicationSpan(span), throwable));
  }

  @Override
  public <E extends Throwable> void startAndRun(
      application.io.opentelemetry.api.incubator.trace.SpanRunnable<E> spanRunnable) throws E {
    agentBuilder.startAndRun(spanRunnable::runInSpan);
  }

  @Override
  public <E extends Throwable> void startAndRun(
      application.io.opentelemetry.api.incubator.trace.SpanRunnable<E> spanRunnable,
      BiConsumer<application.io.opentelemetry.api.trace.Span, Throwable> biConsumer)
      throws E {
    agentBuilder.startAndRun(
        spanRunnable::runInSpan,
        (span, throwable) -> biConsumer.accept(new ApplicationSpan(span), throwable));
  }

  private static class AgentContextPropagators implements ContextPropagators {

    private final application.io.opentelemetry.context.propagation.ContextPropagators
        applicationContextPropagators;

    AgentContextPropagators(
        application.io.opentelemetry.context.propagation.ContextPropagators
            applicationContextPropagators) {
      this.applicationContextPropagators = applicationContextPropagators;
    }

    @Override
    public TextMapPropagator getTextMapPropagator() {
      return new AgentTextMapPropagator(applicationContextPropagators.getTextMapPropagator());
    }
  }

  private static class AgentTextMapPropagator implements TextMapPropagator {

    private final application.io.opentelemetry.context.propagation.TextMapPropagator
        applicationTextMapPropagator;

    AgentTextMapPropagator(
        application.io.opentelemetry.context.propagation.TextMapPropagator
            applicationTextMapPropagator) {
      this.applicationTextMapPropagator = applicationTextMapPropagator;
    }

    @Override
    public Collection<String> fields() {
      return applicationTextMapPropagator.fields();
    }

    @Override
    public <C> void inject(Context context, @Nullable C c, TextMapSetter<C> textMapSetter) {
      applicationTextMapPropagator.inject(
          AgentContextStorage.toApplicationContext(context), c, textMapSetter::set);
    }

    @Override
    public <C> Context extract(Context context, @Nullable C c, TextMapGetter<C> textMapGetter) {
      return AgentContextStorage.getAgentContext(
          applicationTextMapPropagator.extract(
              AgentContextStorage.toApplicationContext(context),
              c,
              new application.io.opentelemetry.context.propagation.TextMapGetter<C>() {

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
