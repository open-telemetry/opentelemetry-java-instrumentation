/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static java.util.logging.Level.INFO;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/** A class responsible for maintaining the set of metrics to collect and report. */
class MetricRegistrar {

  private static final Logger logger = Logger.getLogger(MetricRegistrar.class.getName());

  private final Meter meter;

  MetricRegistrar(OpenTelemetry openTelemetry, String instrumentationScope) {
    meter = openTelemetry.getMeter(instrumentationScope);
  }

  /**
   * Accepts a MetricExtractor for registration and activation.
   *
   * @param connection the {@link MBeanServerConnection} to use to query for metric values
   * @param objectNames the {@link ObjectName} that are known to the server and that know the
   *     attribute that is required to get the metric values
   * @param extractor the {@link MetricExtractor} responsible for getting the metric values
   * @param attributeInfo the {@link AttributeInfo}
   */
  void enrollExtractor(
      MBeanServerConnection connection,
      Collection<ObjectName> objectNames,
      MetricExtractor extractor,
      AttributeInfo attributeInfo) {
    // For the first enrollment of the extractor we have to build the corresponding Instrument
    DetectionStatus status = new DetectionStatus(connection, objectNames);
    boolean firstEnrollment;
    synchronized (extractor) {
      firstEnrollment = extractor.getStatus() == null;
      // For successive enrollments, it is sufficient to refresh the status
      extractor.setStatus(status);
    }

    if (!firstEnrollment) {
      return;
    }

    MetricInfo metricInfo = extractor.getInfo();
    String metricName = metricInfo.getMetricName();
    MetricInfo.Type instrumentType = metricInfo.getType();
    String description =
        metricInfo.getDescription() != null
            ? metricInfo.getDescription()
            : attributeInfo.getDescription();
    String unit = metricInfo.getUnit();

    switch (instrumentType) {
      // CHECKSTYLE:OFF
      case COUNTER:
        {
          // CHECKSTYLE:ON
          LongCounterBuilder builder = meter.counterBuilder(metricName);
          Optional.ofNullable(description).ifPresent(builder::setDescription);
          Optional.ofNullable(unit).ifPresent(builder::setUnit);

          if (attributeInfo.usesDoubleValues()) {
            builder.ofDoubles().buildWithCallback(doubleTypeCallback(extractor));
          } else {
            builder.buildWithCallback(longTypeCallback(extractor));
          }
          logger.log(INFO, "Created Counter for {0}", metricName);
        }
        break;

      // CHECKSTYLE:OFF
      case UPDOWNCOUNTER:
        {
          // CHECKSTYLE:ON
          LongUpDownCounterBuilder builder = meter.upDownCounterBuilder(metricName);
          Optional.ofNullable(description).ifPresent(builder::setDescription);
          Optional.ofNullable(unit).ifPresent(builder::setUnit);

          if (attributeInfo.usesDoubleValues()) {
            builder.ofDoubles().buildWithCallback(doubleTypeCallback(extractor));
          } else {
            builder.buildWithCallback(longTypeCallback(extractor));
          }
          logger.log(INFO, "Created UpDownCounter for {0}", metricName);
        }
        break;

      // CHECKSTYLE:OFF
      case GAUGE:
        {
          // CHECKSTYLE:ON
          DoubleGaugeBuilder builder = meter.gaugeBuilder(metricName);
          Optional.ofNullable(description).ifPresent(builder::setDescription);
          Optional.ofNullable(unit).ifPresent(builder::setUnit);

          if (attributeInfo.usesDoubleValues()) {
            builder.buildWithCallback(doubleTypeCallback(extractor));
          } else {
            builder.ofLongs().buildWithCallback(longTypeCallback(extractor));
          }
          logger.log(INFO, "Created Gauge for {0}", metricName);
        }
        break;
      // CHECKSTYLE:OFF
      case STATE:
        {
          // CHECKSTYLE:ON
          throw new IllegalStateException("state metrics should not be registered");
        }
    }
  }

  /*
   * A method generating metric collection callback for asynchronous Measurement
   * of Double type.
   */
  static Consumer<ObservableDoubleMeasurement> doubleTypeCallback(MetricExtractor extractor) {
    return measurement -> {
      DetectionStatus status = extractor.getStatus();
      if (status != null) {
        MBeanServerConnection connection = status.getConnection();
        for (ObjectName objectName : status.getObjectNames()) {
          Number metricValue =
              extractor.getMetricValueExtractor().extractNumericalAttribute(connection, objectName);
          if (metricValue != null) {
            // get the metric attributes
            Attributes attr = createMetricAttributes(connection, objectName, extractor);
            measurement.record(metricValue.doubleValue(), attr);
          }
        }
      }
    };
  }

  /*
   * A method generating metric collection callback for asynchronous Measurement
   * of Long type.
   */
  static Consumer<ObservableLongMeasurement> longTypeCallback(MetricExtractor extractor) {
    return measurement -> {
      DetectionStatus status = extractor.getStatus();
      if (status != null) {
        MBeanServerConnection connection = status.getConnection();
        for (ObjectName objectName : status.getObjectNames()) {
          Number metricValue =
              extractor.getMetricValueExtractor().extractNumericalAttribute(connection, objectName);
          if (metricValue != null) {
            // get the metric attributes
            Attributes attr = createMetricAttributes(connection, objectName, extractor);
            measurement.record(metricValue.longValue(), attr);
          }
        }
      }
    };
  }

  /*
   * An auxiliary method for collecting measurement attributes to go along
   * the metric values
   */
  static Attributes createMetricAttributes(
      MBeanServerConnection connection, ObjectName objectName, MetricExtractor extractor) {
    AttributesBuilder attrBuilder = Attributes.builder();
    for (MetricAttribute metricAttribute : extractor.getAttributes()) {
      String attributeValue = metricAttribute.acquireAttributeValue(connection, objectName);
      if (attributeValue != null) {
        attrBuilder = attrBuilder.put(metricAttribute.getAttributeName(), attributeValue);
      }
    }
    return attrBuilder.build();
  }
}
