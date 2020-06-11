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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.OpenTelemetrySdk
import unshaded.io.opentelemetry.OpenTelemetry
import unshaded.io.opentelemetry.metrics.AsynchronousInstrument
import unshaded.io.opentelemetry.metrics.DoubleSumObserver
import unshaded.io.opentelemetry.metrics.DoubleUpDownSumObserver
import unshaded.io.opentelemetry.metrics.DoubleValueObserver
import unshaded.io.opentelemetry.metrics.LongSumObserver
import unshaded.io.opentelemetry.metrics.LongUpDownSumObserver
import unshaded.io.opentelemetry.metrics.LongValueObserver

import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.MONOTONIC_DOUBLE
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.MONOTONIC_LONG
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.NON_MONOTONIC_DOUBLE
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.NON_MONOTONIC_LONG
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.SUMMARY

class MeterTest extends AgentTestRunner {

  def "test counter #builderMethod bound=#bind"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = OpenTelemetry.getMeterProvider().get(instrumentationName, "1.2.3")
    def instrument = meter."$builderMethod"("test")
      .setDescription("d")
      .setUnit("u")
      .setConstantLabels(["m": "n", "o": "p"])
      .build()
    if (bind) {
      instrument = instrument.bind()
    }
    if (bind) {
      instrument.add(value1)
      instrument.add(value2)
    } else {
      instrument.add(value1, "q", "r")
      instrument.add(value2, "q", "r")
    }

    then:
    def metricData = metricData(instrumentationName, "test")
    metricData != null
    metricData.descriptor.description == "d"
    metricData.descriptor.unit == "u"
    metricData.descriptor.type == expectedType
    metricData.descriptor.constantLabels == ["m": "n", "o": "p"]
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    metricData.points.size() == 1
    def point = metricData.points.iterator().next()
    if (bind) {
      point.labels == ["w": "x", "y": "z"]
    } else {
      point.labels == ["q": "r"]
    }
    point.value == expectedValue

    where:
    builderMethod                | bind  | value1 | value2 | expectedValue | expectedType
    "longCounterBuilder"         | false | 5      | 6      | 11            | MONOTONIC_LONG
    "longCounterBuilder"         | true  | 5      | 6      | 11            | MONOTONIC_LONG
    "longUpDownCounterBuilder"   | false | 5      | 6      | 11            | NON_MONOTONIC_LONG
    "longUpDownCounterBuilder"   | true  | 5      | 6      | 11            | NON_MONOTONIC_LONG
    "doubleCounterBuilder"       | false | 5.5    | 6.6    | 12.1          | MONOTONIC_DOUBLE
    "doubleCounterBuilder"       | true  | 5.5    | 6.6    | 12.1          | MONOTONIC_DOUBLE
    "doubleUpDownCounterBuilder" | false | 5.5    | 6.6    | 12.1          | NON_MONOTONIC_DOUBLE
    "doubleUpDownCounterBuilder" | true  | 5.5    | 6.6    | 12.1          | NON_MONOTONIC_DOUBLE
  }

  def "test recorder #builderMethod bound=#bind"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = OpenTelemetry.getMeterProvider().get(instrumentationName, "1.2.3")
    def instrument = meter."$builderMethod"("test")
      .setDescription("d")
      .setUnit("u")
      .setConstantLabels(["m": "n", "o": "p"])
      .build()
    if (bind) {
      instrument = instrument.bind()
    }
    if (bind) {
      instrument.record(value1)
      instrument.record(value2)
    } else {
      instrument.record(value1, "q", "r")
      instrument.record(value2, "q", "r")
    }

    then:
    def metricData = metricData(instrumentationName, "test")
    metricData != null
    metricData.descriptor.description == "d"
    metricData.descriptor.unit == "u"
    metricData.descriptor.type == SUMMARY
    metricData.descriptor.constantLabels == ["m": "n", "o": "p"]
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    metricData.points.size() == 1
    def point = metricData.points.iterator().next()
    if (bind) {
      point.labels == ["w": "x", "y": "z"]
    } else {
      point.labels == ["q": "r"]
    }
    point.count == 2
    point.sum == sum

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
    def meter = OpenTelemetry.getMeterProvider().get(instrumentationName, "1.2.3")
    def instrument = meter."$builderMethod"("test")
      .setDescription("d")
      .setUnit("u")
      .setConstantLabels(["m": "n", "o": "p"])
      .build()
    if (builderMethod == "longSumObserverBuilder") {
      instrument.setCallback(new AsynchronousInstrument.Callback<LongSumObserver.ResultLongSumObserver>() {
        @Override
        void update(LongSumObserver.ResultLongSumObserver resultLongSumObserver) {
          resultLongSumObserver.observe(123, "q", "r")
        }
      })
    } else if (builderMethod == "longUpDownSumObserverBuilder") {
      instrument.setCallback(new AsynchronousInstrument.Callback<LongUpDownSumObserver.ResultLongUpDownSumObserver>() {
        @Override
        void update(LongUpDownSumObserver.ResultLongUpDownSumObserver resultLongUpDownSumObserver) {
          resultLongUpDownSumObserver.observe(123, "q", "r")
        }
      })
    } else if (builderMethod == "longValueObserverBuilder") {
      instrument.setCallback(new AsynchronousInstrument.Callback<LongValueObserver.ResultLongValueObserver>() {
        @Override
        void update(LongValueObserver.ResultLongValueObserver resultLongObserver) {
          resultLongObserver.observe(123, "q", "r")
        }
      })
    } else if (builderMethod == "doubleSumObserverBuilder") {
      instrument.setCallback(new AsynchronousInstrument.Callback<DoubleSumObserver.ResultDoubleSumObserver>() {
        @Override
        void update(DoubleSumObserver.ResultDoubleSumObserver resultDoubleSumObserver) {
          resultDoubleSumObserver.observe(1.23, "q", "r")
        }
      })
    } else if (builderMethod == "doubleUpDownSumObserverBuilder") {
      instrument.setCallback(new AsynchronousInstrument.Callback<DoubleUpDownSumObserver.ResultDoubleUpDownSumObserver>() {
        @Override
        void update(DoubleUpDownSumObserver.ResultDoubleUpDownSumObserver resultDoubleUpDownSumObserver) {
          resultDoubleUpDownSumObserver.observe(1.23, "q", "r")
        }
      })
    } else if (builderMethod == "doubleValueObserverBuilder") {
      instrument.setCallback(new AsynchronousInstrument.Callback<DoubleValueObserver.ResultDoubleValueObserver>() {
        @Override
        void update(DoubleValueObserver.ResultDoubleValueObserver resultDoubleObserver) {
          resultDoubleObserver.observe(1.23, "q", "r")
        }
      })
    }

    then:
    def metricData = metricData(instrumentationName, "test")
    metricData != null
    metricData.descriptor.description == "d"
    metricData.descriptor.unit == "u"
    metricData.descriptor.type == expectedType
    metricData.descriptor.constantLabels == ["m": "n", "o": "p"]
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    metricData.points.size() == 1
    def point = metricData.points.iterator().next()
    point.labels == ["q": "r"]
    if (builderMethod.startsWith("long")) {
      point."$valueMethod" == 123
    } else {
      point."$valueMethod" == 1.23
    }

    where:
    builderMethod                    | valueMethod | expectedType
    "longSumObserverBuilder"         | "value"     | MONOTONIC_LONG
    "longUpDownSumObserverBuilder"   | "value"     | NON_MONOTONIC_LONG
    "longValueObserverBuilder"       | "sum"       | SUMMARY
    "doubleSumObserverBuilder"       | "value"     | MONOTONIC_DOUBLE
    "doubleUpDownSumObserverBuilder" | "value"     | NON_MONOTONIC_DOUBLE
    "doubleValueObserverBuilder"     | "sum"       | SUMMARY
  }

  def "test batch recorder"() {
    given:
    // meters are global, and no way to unregister them, so tests use random name to avoid each other
    def instrumentationName = "test" + new Random().nextLong()

    when:
    def meter = OpenTelemetry.getMeterProvider().get(instrumentationName, "1.2.3")
    def longCounter = meter.longCounterBuilder("test")
      .setDescription("d")
      .setUnit("u")
      .setConstantLabels(["m": "n", "o": "p"])
      .build()
    def doubleMeasure = meter.doubleValueRecorderBuilder("test2")
      .setDescription("d")
      .setUnit("u")
      .setConstantLabels(["m": "n", "o": "p"])
      .build()

    def batchRecorder = meter.newBatchRecorder("q", "r")
    batchRecorder.put(longCounter, 5)
    batchRecorder.put(longCounter, 6)
    batchRecorder.put(doubleMeasure, 5.5)
    batchRecorder.put(doubleMeasure, 6.6)
    batchRecorder.record()

    then:
    def metricData = metricData(instrumentationName, "test")
    metricData != null
    metricData.descriptor.description == "d"
    metricData.descriptor.unit == "u"
    metricData.descriptor.type == MONOTONIC_LONG
    metricData.descriptor.constantLabels == ["m": "n", "o": "p"]
    metricData.instrumentationLibraryInfo.name == instrumentationName
    metricData.instrumentationLibraryInfo.version == "1.2.3"
    metricData.points.size() == 1
    def point = metricData.points.iterator().next()
    point.labels == ["q": "r"]
    point.value == 11

    def metricData2 = this.metricData(instrumentationName, "test2")
    metricData2 != null
    metricData2.descriptor.description == "d"
    metricData2.descriptor.unit == "u"
    metricData2.descriptor.type == SUMMARY
    metricData2.descriptor.constantLabels == ["m": "n", "o": "p"]
    metricData2.instrumentationLibraryInfo.name == instrumentationName
    metricData2.instrumentationLibraryInfo.version == "1.2.3"
    metricData2.points.size() == 1
    def point2 = metricData2.points.iterator().next()
    point2.labels == ["q": "r"]
    point2.count == 2
    point2.sum == 12.1
  }

  def metricData(instrumentationName, metricName) {
    for (def metric : OpenTelemetrySdk.getMeterProvider().getMetricProducer().getAllMetrics()) {
      if (metric.instrumentationLibraryInfo.name == instrumentationName && metric.descriptor.name == metricName) {
        return metric
      }
    }
  }
}
