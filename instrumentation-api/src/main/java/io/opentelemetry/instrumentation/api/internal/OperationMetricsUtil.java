/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OperationMetricsUtil {
  private static final Logger logger = Logger.getLogger(OperationMetricsUtil.class.getName());
  public static final OperationListener NOOP_OPERATION_LISTENER =
      new OperationListener() {

        @Override
        public Context onStart(Context context, Attributes startAttributes, long startNanos) {
          return context;
        }

        @Override
        public void onEnd(Context context, Attributes endAttributes, long endNanos) {}
      };

  public static OperationMetrics create(
      String description, Function<Meter, OperationListener> factory) {
    return create(
        description,
        factory,
        (s, histogramBuilder) ->
            logger.log(
                Level.WARNING,
                "Disabling {0} metrics because {1} does not implement {2}. This prevents using "
                    + "metrics advice, which could result in {0} metrics having high cardinality "
                    + "attributes.",
                new Object[] {
                  description,
                  histogramBuilder.getClass().getName(),
                  ExtendedDoubleHistogramBuilder.class.getName()
                }));
  }

  // visible for testing
  static OperationMetrics create(
      String description,
      Function<Meter, OperationListener> factory,
      BiConsumer<String, DoubleHistogramBuilder> warningEmitter) {
    return meter -> {
      DoubleHistogramBuilder histogramBuilder = meter.histogramBuilder("compatibility-test");
      if (!(histogramBuilder instanceof ExtendedDoubleHistogramBuilder)
          && !histogramBuilder.getClass().getName().contains("NoopDoubleHistogram")) {
        warningEmitter.accept(description, histogramBuilder);
        return NOOP_OPERATION_LISTENER;
      }
      return factory.apply(meter);
    };
  }

  private OperationMetricsUtil() {}
}
