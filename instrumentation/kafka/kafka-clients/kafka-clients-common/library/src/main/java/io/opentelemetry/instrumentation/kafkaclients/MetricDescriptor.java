package io.opentelemetry.instrumentation.kafkaclients;

import com.google.auto.value.AutoValue;

/** A description of an OpenTelemetry metric instrument. */
@AutoValue
abstract class MetricDescriptor {

  static final String INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE = "DOUBLE_OBSERVABLE_GAUGE";

  abstract String getName();

  abstract String getDescription();

  abstract String getUnit();

  abstract String getInstrumentType();

  static MetricDescriptor createDoubleGauge(String name, String description, String unit) {
    return new AutoValue_MetricDescriptor(
        name, description, unit, INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
  }
}
