/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.instrumentation.annotations.WithSpan;
import application.io.opentelemetry.instrumentation.annotations.Counted;
import application.io.opentelemetry.instrumentation.annotations.MetricAttribute;
import application.io.opentelemetry.instrumentation.annotations.Timed;
import com.google.common.base.Stopwatch;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.annotation.support.MethodSpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.annotation.support.SpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.SpanNames;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public final class AnnotationSingletons {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-1.16";

  private static final Logger logger = Logger.getLogger(AnnotationSingletons.class.getName());
  private static final Instrumenter<Method, Object> INSTRUMENTER = createInstrumenter();
  private static final Instrumenter<MethodRequest, Object> INSTRUMENTER_WITH_ATTRIBUTES =
      createInstrumenterWithAttributes();
  private static final SpanAttributesExtractor ATTRIBUTES = createAttributesExtractor();

  private static final ConcurrentMap<String, DoubleHistogram> HISTOGRAMS =
      new ConcurrentHashMap<>();

  private static final ConcurrentMap<String, LongCounter> COUNTERS = new ConcurrentHashMap<>();

  private static final Meter METER = GlobalOpenTelemetry.get().getMeter(INSTRUMENTATION_NAME);

  public static Instrumenter<Method, Object> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<MethodRequest, Object> instrumenterWithAttributes() {
    return INSTRUMENTER_WITH_ATTRIBUTES;
  }

  public static SpanAttributesExtractor attributes() {
    return ATTRIBUTES;
  }

  public static void recordHistogramWithAttributes(
      MethodRequest methodRequest, Stopwatch stopwatch) {
    Timed timedAnnotation = methodRequest.method().getAnnotation(Timed.class);
    AttributesBuilder attributesBuilder = Attributes.builder();
    extractMetricAttributes(methodRequest, attributesBuilder);
    extractAdditionAttributes(timedAnnotation, attributesBuilder);
    getHistogram(timedAnnotation)
        .record(stopwatch.stop().elapsed().toMillis(), attributesBuilder.build());
  }

  private static void extractMetricAttributes(
      MethodRequest methodRequest, AttributesBuilder attributesBuilder) {
    Parameter[] parameters = methodRequest.method().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].isAnnotationPresent(MetricAttribute.class)) {
        MetricAttribute annotation = parameters[i].getAnnotation(MetricAttribute.class);
        String attributeKey = "";
        if (!StringUtils.isNullOrEmpty(annotation.value())) {
          attributeKey = annotation.value();
        } else if (!StringUtils.isNullOrEmpty(parameters[i].getName())) {
          attributeKey = parameters[i].getName();
        } else {
          continue;
        }
        attributesBuilder.put(attributeKey, methodRequest.args()[i].toString());
      }
    }
  }

  private static void extractAdditionAttributes(
      Timed timedAnnotation, AttributesBuilder attributesBuilder) {
    int length = timedAnnotation.attributes().length;
    for (int i = 0; i < length / 2; i++) {
      attributesBuilder.put(
          timedAnnotation.attributes()[i],
          i + 1 > length ? "" : timedAnnotation.attributes()[i + 1]);
    }
  }

  public static void recordHistogram(Method method, Stopwatch stopwatch) {
    Timed timedAnnotation = method.getAnnotation(Timed.class);
    AttributesBuilder attributesBuilder = Attributes.builder();
    extractAdditionAttributes(timedAnnotation, attributesBuilder);
    getHistogram(timedAnnotation)
        .record(stopwatch.stop().elapsed().toMillis(), attributesBuilder.build());
  }

  private static LongCounter getCounter(MethodRequest methodRequest) {
    Counted countedAnnotation = methodRequest.method().getAnnotation(Counted.class);
    if (!COUNTERS.containsKey(countedAnnotation.value())) {
      synchronized (countedAnnotation.value()) {
        if (!COUNTERS.containsKey(countedAnnotation.value())) {
          COUNTERS.put(
              countedAnnotation.value(),
              METER
                  .counterBuilder(countedAnnotation.value())
                  .setDescription(countedAnnotation.description())
                  .setUnit(countedAnnotation.unit())
                  .build());
        }
      }
    }
    return COUNTERS.get(countedAnnotation.value());
  }

  private static Instrumenter<Method, Object> createInstrumenter() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            AnnotationSingletons::spanNameFromMethod)
        .addAttributesExtractor(CodeAttributesExtractor.create(MethodCodeAttributesGetter.INSTANCE))
        .buildInstrumenter(AnnotationSingletons::spanKindFromMethod);
  }

  private static Instrumenter<MethodRequest, Object> createInstrumenterWithAttributes() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            AnnotationSingletons::spanNameFromMethodRequest)
        .addAttributesExtractor(
            CodeAttributesExtractor.create(MethodRequestCodeAttributesGetter.INSTANCE))
        .addAttributesExtractor(
            MethodSpanAttributesExtractor.create(
                MethodRequest::method,
                WithSpanParameterAttributeNamesExtractor.INSTANCE,
                MethodRequest::args))
        .buildInstrumenter(AnnotationSingletons::spanKindFromMethodRequest);
  }

  private static SpanAttributesExtractor createAttributesExtractor() {
    return SpanAttributesExtractor.create(WithSpanParameterAttributeNamesExtractor.INSTANCE);
  }

  private static SpanKind spanKindFromMethodRequest(MethodRequest request) {
    return spanKindFromMethod(request.method());
  }

  private static SpanKind spanKindFromMethod(Method method) {
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    if (annotation == null) {
      return SpanKind.INTERNAL;
    }
    return toAgentOrNull(annotation.kind());
  }

  private static SpanKind toAgentOrNull(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    try {
      return SpanKind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "unexpected span kind: {0}", applicationSpanKind.name());
      return SpanKind.INTERNAL;
    }
  }

  private static String spanNameFromMethodRequest(MethodRequest request) {
    return spanNameFromMethod(request.method());
  }

  private static String spanNameFromMethod(Method method) {
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      spanName = SpanNames.fromMethod(method);
    }
    return spanName;
  }

  private static DoubleHistogram getHistogram(Timed timedAnnotation) {
    if (!HISTOGRAMS.containsKey(timedAnnotation.value())) {
      synchronized (timedAnnotation.value()) {
        if (!HISTOGRAMS.containsKey(timedAnnotation.value())) {
          HISTOGRAMS.put(
              timedAnnotation.value(),
              METER
                  .histogramBuilder(timedAnnotation.value())
                  .setDescription(timedAnnotation.description())
                  .setUnit(timedAnnotation.unit())
                  .build());
        }
      }
    }
    return HISTOGRAMS.get(timedAnnotation.value());
  }

  private AnnotationSingletons() {}
}
