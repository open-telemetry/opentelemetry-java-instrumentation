/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.sdk.metrics.data.MetricDataType.DOUBLE_GAUGE
import static io.opentelemetry.sdk.metrics.data.MetricDataType.DOUBLE_SUM
import static io.opentelemetry.sdk.metrics.data.MetricDataType.LONG_GAUGE
import static io.opentelemetry.sdk.metrics.data.MetricDataType.LONG_SUM
import static io.opentelemetry.sdk.metrics.data.MetricDataType.SUMMARY
import static java.util.concurrent.TimeUnit.SECONDS

import com.google.common.base.Stopwatch
import io.opentelemetry.api.metrics.AsynchronousInstrument
import io.opentelemetry.api.metrics.GlobalMetricsProvider
import io.opentelemetry.api.metrics.common.Labels
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.PointData
import java.util.function.Consumer

class MeterTest extends AgentInstrumentationSpecification {

  def "test counter #builderMethod bound=#bind"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = GlobalMetricsProvider.getMeter(instrumentationName, "1.2.3")
    def instrument = meter."$builderMethod"("test")
      .setDescription("d")
      .setUnit("u")
      .build()
    if (bind) {
      instrument = instrument.bind(Labels.empty())
    }
    if (bind) {
      instrument.add(value1)
      instrument.add(value2)
    } else {
      instrument.add(value1, Labels.of("q", "r"))
      instrument.add(value2, Labels.of("q", "r"))
    }

    then:
    def metricData = findMetric(instrumentationName, "test")
    metricData != null
    metricData.description == "d"
    metricData.unit == "u"
    metricData.type == expectedType
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    points(metricData).size() == 1
    def point = points(metricData).iterator().next()
    if (bind) {
      point.labels == Labels.of("w", "x", "y", "z")
    } else {
      point.labels == Labels.of("q", "r")
    }
    point.value == expectedValue

    where:
    builderMethod                | bind  | value1 | value2 | expectedValue | expectedType
    "longCounterBuilder"         | false | 5      | 6      | 11            | LONG_SUM
    "longCounterBuilder"         | true  | 5      | 6      | 11            | LONG_SUM
    "longUpDownCounterBuilder"   | false | 5      | 6      | 11            | LONG_SUM
    "longUpDownCounterBuilder"   | true  | 5      | 6      | 11            | LONG_SUM
    "doubleCounterBuilder"       | false | 5.5    | 6.6    | 12.1          | DOUBLE_SUM
    "doubleCounterBuilder"       | true  | 5.5    | 6.6    | 12.1          | DOUBLE_SUM
    "doubleUpDownCounterBuilder" | false | 5.5    | 6.6    | 12.1          | DOUBLE_SUM
    "doubleUpDownCounterBuilder" | true  | 5.5    | 6.6    | 12.1          | DOUBLE_SUM
  }

  def "test recorder #builderMethod bound=#bind"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = GlobalMetricsProvider.getMeter(instrumentationName, "1.2.3")
    def instrument = meter."$builderMethod"("test")
      .setDescription("d")
      .setUnit("u")
      .build()
    if (bind) {
      instrument = instrument.bind(Labels.empty())
    }
    if (bind) {
      instrument.record(value1)
      instrument.record(value2)
    } else {
      instrument.record(value1, Labels.of("q", "r"))
      instrument.record(value2, Labels.of("q", "r"))
    }

    then:
    def metricData = findMetric(instrumentationName, "test")
    metricData != null
    metricData.description == "d"
    metricData.unit == "u"
    metricData.type == SUMMARY
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    points(metricData).size() == 1
    def point = points(metricData).iterator().next()
    if (bind) {
      point.labels == Labels.of("w", "x", "y", "z")
    } else {
      point.labels == Labels.of("q", "r")
    }

    where:
    builderMethod                | bind  | value1 | value2 | sum
    "longValueRecorderBuilder"   | false | 5      | 6      | 11
    "longValueRecorderBuilder"   | true  | 5      | 6      | 11
    "doubleValueRecorderBuilder" | false | 5.5    | 6.6    | 12.1
    "doubleValueRecorderBuilder" | true  | 5.5    | 6.6    | 12.1
  }

  def "test observer #builderMethod"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = GlobalMetricsProvider.getMeter(instrumentationName, "1.2.3")
    def instrument = meter."$builderMethod"("test")
      .setDescription("d")
      .setUnit("u")
    if (builderMethod == "longSumObserverBuilder") {
      instrument.setUpdater(new Consumer<AsynchronousInstrument.LongResult>() {
        @Override
        void accept(AsynchronousInstrument.LongResult resultLongSumObserver) {
          resultLongSumObserver.observe(123, Labels.of("q", "r"))
        }
      })
    } else if (builderMethod == "longUpDownSumObserverBuilder") {
      instrument.setUpdater(new Consumer<AsynchronousInstrument.LongResult>() {
        @Override
        void accept(AsynchronousInstrument.LongResult resultLongUpDownSumObserver) {
          resultLongUpDownSumObserver.observe(123, Labels.of("q", "r"))
        }
      })
    } else if (builderMethod == "longValueObserverBuilder") {
      instrument.setUpdater(new Consumer<AsynchronousInstrument.LongResult>() {
        @Override
        void accept(AsynchronousInstrument.LongResult resultLongObserver) {
          resultLongObserver.observe(123, Labels.of("q", "r"))
        }
      })
    } else if (builderMethod == "doubleSumObserverBuilder") {
      instrument.setUpdater(new Consumer<AsynchronousInstrument.DoubleResult>() {
        @Override
        void accept(AsynchronousInstrument.DoubleResult resultDoubleSumObserver) {
          resultDoubleSumObserver.observe(1.23, Labels.of("q", "r"))
        }
      })
    } else if (builderMethod == "doubleUpDownSumObserverBuilder") {
      instrument.setUpdater(new Consumer<AsynchronousInstrument.DoubleResult>() {
        @Override
        void accept(AsynchronousInstrument.DoubleResult resultDoubleUpDownSumObserver) {
          resultDoubleUpDownSumObserver.observe(1.23, Labels.of("q", "r"))
        }
      })
    } else if (builderMethod == "doubleValueObserverBuilder") {
      instrument.setUpdater(new Consumer<AsynchronousInstrument.DoubleResult>() {
        @Override
        void accept(AsynchronousInstrument.DoubleResult resultDoubleObserver) {
          resultDoubleObserver.observe(1.23, Labels.of("q", "r"))
        }
      })
    }
    instrument.build()

    then:
    def metricData = findMetric(instrumentationName, "test")
    metricData != null
    metricData.description == "d"
    metricData.unit == "u"
    metricData.type == expectedType
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    points(metricData).size() == 1
    def point = points(metricData).iterator().next()
    point.labels == Labels.of("q", "r")
    if (builderMethod.startsWith("long")) {
      point.value == 123
    } else {
      point.value == 1.23
    }

    where:
    builderMethod                    | valueMethod | expectedType
    "longSumObserverBuilder"         | "value"     | LONG_SUM
    "longUpDownSumObserverBuilder"   | "value"     | LONG_SUM
    "longValueObserverBuilder"       | "sum"       | LONG_GAUGE
    "doubleSumObserverBuilder"       | "value"     | DOUBLE_SUM
    "doubleUpDownSumObserverBuilder" | "value"     | DOUBLE_SUM
    "doubleValueObserverBuilder"     | "sum"       | DOUBLE_GAUGE
  }

  def "test batch recorder"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = GlobalMetricsProvider.getMeter(instrumentationName, "1.2.3")
    def longCounter = meter.longCounterBuilder("test")
      .setDescription("d")
      .setUnit("u")
      .build()
    def doubleMeasure = meter.doubleValueRecorderBuilder("test2")
      .setDescription("d")
      .setUnit("u")
      .build()

    meter.newBatchRecorder("q", "r")
      .put(longCounter, 5)
      .put(longCounter, 6)
      .put(doubleMeasure, 5.5)
      .put(doubleMeasure, 6.6)
      .record()

    then:
    def metricData = findMetric(instrumentationName, "test")
    metricData != null
    metricData.description == "d"
    metricData.unit == "u"
    metricData.type == LONG_SUM
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    points(metricData).size() == 1
    def point = points(metricData).iterator().next()
    point.labels == Labels.of("q", "r")
    point.value == 11

    def metricData2 = findMetric(instrumentationName, "test2")
    metricData2 != null
    metricData2.description == "d"
    metricData2.unit == "u"
    metricData2.type == SUMMARY
    metricData2.instrumentationLibraryInfo.name == instrumentationName
    metricData2.instrumentationLibraryInfo.version == "1.2.3"
    points(metricData2).size() == 1
    def point2 = points(metricData2).iterator().next()
    point2.labels == Labels.of("q", "r")
    point2.count == 2
    point2.sum == 12.1
  }

  def findMetric(instrumentationName, metricName) {
    Stopwatch stopwatch = Stopwatch.createStarted()
    while (stopwatch.elapsed(SECONDS) < 10) {
      for (def metric : metrics) {
        if (metric.instrumentationLibraryInfo.name == instrumentationName && metric.name == metricName) {
          return metric
        }
      }
    }
  }

  List<PointData> points(MetricData metricData) {
    def points = []
    points.addAll(metricData.getDoubleGaugeData().getPoints())
    points.addAll(metricData.getDoubleSumData().getPoints())
    points.addAll(metricData.getDoubleSummaryData().getPoints())
    points.addAll(metricData.getLongGaugeData().getPoints())
    points.addAll(metricData.getLongSumData().getPoints())
    return points
  }
}
