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

package io.opentelemetry.instrumentation.jmxmetrics

import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.MONOTONIC_DOUBLE
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.MONOTONIC_LONG
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.NON_MONOTONIC_DOUBLE
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.NON_MONOTONIC_LONG
import static io.opentelemetry.sdk.metrics.data.MetricData.Descriptor.Type.SUMMARY

import io.opentelemetry.common.Labels
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.rules.TestRule

import spock.lang.Shared
import spock.lang.Specification

class OtelHelperSynchronousMetricTest extends Specification{

    @Shared
    GroovyUtils gutil

    @Shared
    OtelHelper otel

    @Rule public final TestRule name = new TestName()

    def setup() {
        // Set up a MeterSdk per test to be able to collect its metrics alone
        gutil = new GroovyUtils(
                new JmxConfig().tap {
                    exporterType = 'inmemory'
                },
                name.methodName, ''
        )
        otel = new OtelHelper(null, gutil)
    }

    def exportMetrics() {
        def provider = OpenTelemetrySdk.meterProvider.get(name.methodName, '')
        return provider.collectAll().sort { md1, md2 ->
            def p1 = md1.points[0]
            def p2 = md2.points[0]
            def s1 = p1.startEpochNanos
            def s2 = p2.startEpochNanos
            if (s1 == s2) {
                if (md1.descriptor.type == SUMMARY) {
                    return p1.percentileValues[0].value <=> p2.percentileValues[0].value
                }
                return p1.value <=> p2.value
            }
            s1 <=> s2
        }
    }

    def "double counter"() {
      when:
        def dc = otel.doubleCounter(
                'double-counter', 'a double counter',
                 'ms', [key1:'value1', key2:'value2']
        )
        dc.add(123.456, Labels.of('key', 'value'))

        dc = otel.doubleCounter('my-double-counter', 'another double counter', 'µs')
        dc.add(234.567, Labels.of('myKey', 'myValue'))

        dc = otel.doubleCounter('another-double-counter', 'double counter')
        dc.add(345.678, Labels.of('anotherKey', 'anotherValue'))

        dc = otel.doubleCounter('yet-another-double-counter')
        dc.add(456.789, Labels.of('yetAnotherKey', 'yetAnotherValue'))

        def metrics = exportMetrics()
      then:
        assert metrics.size() == 4

        def first = metrics[0]
        def second = metrics[1]
        def third = metrics[2]
        def fourth = metrics[3]

        assert first.descriptor.name == 'double-counter'
        assert first.descriptor.description == 'a double counter'
        assert first.descriptor.unit == 'ms'
        assert first.descriptor.constantLabels == Labels.of(
                'key1', 'value1', 'key2', 'value2'
        )
        assert first.descriptor.type == MONOTONIC_DOUBLE
        assert first.points.size() == 1
        assert first.points[0].value == 123.456
        assert first.points[0].labels == Labels.of('key', 'value')

        assert second.descriptor.name == 'my-double-counter'
        assert second.descriptor.description == 'another double counter'
        assert second.descriptor.unit == 'µs'
        assert second.descriptor.constantLabels == Labels.empty()
        assert second.descriptor.type == MONOTONIC_DOUBLE
        assert second.points.size() == 1
        assert second.points[0].value == 234.567
        assert second.points[0].labels == Labels.of('myKey', 'myValue')

        assert third.descriptor.name == 'another-double-counter'
        assert third.descriptor.description == 'double counter'
        assert third.descriptor.unit == '1'
        assert third.descriptor.constantLabels == Labels.empty()
        assert third.descriptor.type == MONOTONIC_DOUBLE
        assert third.points.size() == 1
        assert third.points[0].value == 345.678
        assert third.points[0].labels == Labels.of('anotherKey', 'anotherValue')

        assert fourth.descriptor.name == 'yet-another-double-counter'
        assert fourth.descriptor.description == ''
        assert fourth.descriptor.unit == '1'
        assert fourth.descriptor.constantLabels == Labels.empty()
        assert fourth.descriptor.type == MONOTONIC_DOUBLE
        assert fourth.points.size() == 1
        assert fourth.points[0].value == 456.789
        assert fourth.points[0].labels == Labels.of('yetAnotherKey', 'yetAnotherValue')
    }

    def "double counter memoization"() {
      when:
        def dcOne = otel.doubleCounter('dc', 'double')
        dcOne.add(10.1, Labels.of('key', 'value'))
        def dcTwo = otel.doubleCounter('dc', 'double')
        dcTwo.add(10.1, Labels.of('key', 'value'))

      then:
        assert dcOne.is(dcTwo)

        def metrics = exportMetrics()
        assert metrics.size() == 1
        def metric = metrics[0]

        assert metric.descriptor.name == 'dc'
        assert metric.descriptor.description == 'double'
        assert metric.descriptor.unit == '1'
        assert metric.descriptor.constantLabels == Labels.empty()
        assert metric.descriptor.type == MONOTONIC_DOUBLE
        assert metric.points.size() == 1
        assert metric.points[0].value == 20.2
        assert metric.points[0].labels == Labels.of('key', 'value')
    }

    def "long counter"() {
      when:
        def lc = otel.longCounter(
                'long-counter', 'a long counter',
                'ms', [key1:'value1', key2:'value2']
        )
        lc.add(123, Labels.of('key', 'value'))

        lc = otel.longCounter('my-long-counter', 'another long counter', 'µs')
        lc.add(234, Labels.of('myKey', 'myValue'))

        lc = otel.longCounter('another-long-counter', 'long counter')
        lc.add(345, Labels.of('anotherKey', 'anotherValue'))

        lc = otel.longCounter('yet-another-long-counter')
        lc.add(456, Labels.of('yetAnotherKey', 'yetAnotherValue'))

        def metrics = exportMetrics()
      then:
        assert metrics.size() == 4

        def first = metrics[0]
        def second = metrics[1]
        def third = metrics[2]
        def fourth = metrics[3]

        assert first.descriptor.name == 'long-counter'
        assert first.descriptor.description == 'a long counter'
        assert first.descriptor.unit == 'ms'
        assert first.descriptor.constantLabels == Labels.of(
                'key1', 'value1', 'key2', 'value2'
        )
        assert first.descriptor.type == MONOTONIC_LONG
        assert first.points.size() == 1
        assert first.points[0].value == 123
        assert first.points[0].labels == Labels.of('key', 'value')

        assert second.descriptor.name == 'my-long-counter'
        assert second.descriptor.description == 'another long counter'
        assert second.descriptor.unit == 'µs'
        assert second.descriptor.constantLabels == Labels.empty()
        assert second.descriptor.type == MONOTONIC_LONG
        assert second.points.size() == 1
        assert second.points[0].value == 234
        assert second.points[0].labels == Labels.of('myKey', 'myValue')

        assert third.descriptor.name == 'another-long-counter'
        assert third.descriptor.description == 'long counter'
        assert third.descriptor.unit == '1'
        assert third.descriptor.constantLabels == Labels.empty()
        assert third.descriptor.type == MONOTONIC_LONG
        assert third.points.size() == 1
        assert third.points[0].value == 345
        assert third.points[0].labels == Labels.of('anotherKey', 'anotherValue')

        assert fourth.descriptor.name == 'yet-another-long-counter'
        assert fourth.descriptor.description == ''
        assert fourth.descriptor.unit == '1'
        assert fourth.descriptor.constantLabels == Labels.empty()
        assert fourth.descriptor.type == MONOTONIC_LONG
        assert fourth.points.size() == 1
        assert fourth.points[0].value == 456
        assert fourth.points[0].labels == Labels.of('yetAnotherKey', 'yetAnotherValue')
    }

    def "long counter memoization"() {
      when:
        def lcOne = otel.longCounter('lc', 'long')
        lcOne.add(10, Labels.of('key', 'value'))
        def lcTwo = otel.longCounter('lc', 'long')
        lcTwo.add(10, Labels.of('key', 'value'))

      then:
        assert lcOne.is(lcTwo)

        def metrics = exportMetrics()
        assert metrics.size() == 1
        def metric = metrics[0]

        assert metric.descriptor.name == 'lc'
        assert metric.descriptor.description == 'long'
        assert metric.descriptor.unit == '1'
        assert metric.descriptor.constantLabels == Labels.empty()
        assert metric.descriptor.type == MONOTONIC_LONG
        assert metric.points.size() == 1
        assert metric.points[0].value == 20
        assert metric.points[0].labels == Labels.of('key', 'value')
    }

    def "double up down counter"() {
      when:
        def dudc = otel.doubleUpDownCounter(
                'double-up-down-counter', 'a double up-down-counter',
                'ms', [key1:'value1', key2:'value2']
        )
        dudc.add(-234.567, Labels.of('key', 'value'))

        dudc = otel.doubleUpDownCounter('my-double-up-down-counter', 'another double up-down-counter', 'µs')
        dudc.add(-123.456, Labels.of('myKey', 'myValue'))

        dudc = otel.doubleUpDownCounter('another-double-up-down-counter', 'double up-down-counter')
        dudc.add(345.678, Labels.of('anotherKey', 'anotherValue'))

        dudc = otel.doubleUpDownCounter('yet-another-double-up-down-counter')
        dudc.add(456.789, Labels.of('yetAnotherKey', 'yetAnotherValue'))

        def metrics = exportMetrics()
      then:
        assert metrics.size() == 4

        def first = metrics[0]
        def second = metrics[1]
        def third = metrics[2]
        def fourth = metrics[3]

        assert first.descriptor.name == 'double-up-down-counter'
        assert first.descriptor.description == 'a double up-down-counter'
        assert first.descriptor.unit == 'ms'
        assert first.descriptor.constantLabels == Labels.of(
                'key1', 'value1', 'key2', 'value2'
        )
        assert first.descriptor.type == NON_MONOTONIC_DOUBLE
        assert first.points.size() == 1
        assert first.points[0].value == -234.567
        assert first.points[0].labels == Labels.of('key', 'value')

        assert second.descriptor.name == 'my-double-up-down-counter'
        assert second.descriptor.description == 'another double up-down-counter'
        assert second.descriptor.unit == 'µs'
        assert second.descriptor.constantLabels == Labels.empty()
        assert second.descriptor.type == NON_MONOTONIC_DOUBLE
        assert second.points.size() == 1
        assert second.points[0].value == -123.456
        assert second.points[0].labels == Labels.of('myKey', 'myValue')

        assert third.descriptor.name == 'another-double-up-down-counter'
        assert third.descriptor.description == 'double up-down-counter'
        assert third.descriptor.unit == '1'
        assert third.descriptor.constantLabels == Labels.empty()
        assert third.descriptor.type == NON_MONOTONIC_DOUBLE
        assert third.points.size() == 1
        assert third.points[0].value == 345.678
        assert third.points[0].labels == Labels.of('anotherKey', 'anotherValue')

        assert fourth.descriptor.name == 'yet-another-double-up-down-counter'
        assert fourth.descriptor.description == ''
        assert fourth.descriptor.unit == '1'
        assert fourth.descriptor.constantLabels == Labels.empty()
        assert fourth.descriptor.type == NON_MONOTONIC_DOUBLE
        assert fourth.points.size() == 1
        assert fourth.points[0].value == 456.789
        assert fourth.points[0].labels == Labels.of('yetAnotherKey', 'yetAnotherValue')
    }

  def "double up down counter memoization"() {
    when:
        def dudcOne = otel.doubleUpDownCounter('dudc', 'double up down')
        dudcOne.add(10.1, Labels.of('key', 'value'))
        def dudcTwo = otel.doubleUpDownCounter('dudc', 'double up down')
        dudcTwo.add(-10.1, Labels.of('key', 'value'))

    then:
        assert dudcOne.is(dudcTwo)

        def metrics = exportMetrics()
        assert metrics.size() == 1
        def metric = metrics[0]

        assert metric.descriptor.name == 'dudc'
        assert metric.descriptor.description == 'double up down'
        assert metric.descriptor.unit == '1'
        assert metric.descriptor.constantLabels == Labels.empty()
        assert metric.descriptor.type == NON_MONOTONIC_DOUBLE
        assert metric.points.size() == 1
        assert metric.points[0].value == 0
        assert metric.points[0].labels == Labels.of('key', 'value')
    }

  def "long up down counter"() {
    when:
        def ludc = otel.longUpDownCounter(
                'long-up-down-counter', 'a long up-down-counter',
                'ms', [key1:'value1', key2:'value2']
        )
        ludc.add(-234, Labels.of('key', 'value'))

        ludc = otel.longUpDownCounter('my-long-up-down-counter', 'another long up-down-counter', 'µs')
        ludc.add(-123, Labels.of('myKey', 'myValue'))

        ludc = otel.longUpDownCounter('another-long-up-down-counter', 'long up-down-counter')
        ludc.add(345, Labels.of('anotherKey', 'anotherValue'))

        ludc = otel.longUpDownCounter('yet-another-long-up-down-counter')
        ludc.add(456, Labels.of('yetAnotherKey', 'yetAnotherValue'))

        def metrics = exportMetrics()
    then:
        assert metrics.size() == 4

        def first = metrics[0]
        def second = metrics[1]
        def third = metrics[2]
        def fourth = metrics[3]

        assert first.descriptor.name == 'long-up-down-counter'
        assert first.descriptor.description == 'a long up-down-counter'
        assert first.descriptor.unit == 'ms'
        assert first.descriptor.constantLabels == Labels.of(
                'key1', 'value1', 'key2', 'value2'
        )
        assert first.descriptor.type == NON_MONOTONIC_LONG
        assert first.points.size() == 1
        assert first.points[0].value == -234
        assert first.points[0].labels == Labels.of('key', 'value')

        assert second.descriptor.name == 'my-long-up-down-counter'
        assert second.descriptor.description == 'another long up-down-counter'
        assert second.descriptor.unit == 'µs'
        assert second.descriptor.constantLabels == Labels.empty()
        assert second.descriptor.type == NON_MONOTONIC_LONG
        assert second.points.size() == 1
        assert second.points[0].value == -123
        assert second.points[0].labels == Labels.of('myKey', 'myValue')

        assert third.descriptor.name == 'another-long-up-down-counter'
        assert third.descriptor.description == 'long up-down-counter'
        assert third.descriptor.unit == '1'
        assert third.descriptor.constantLabels == Labels.empty()
        assert third.descriptor.type == NON_MONOTONIC_LONG
        assert third.points.size() == 1
        assert third.points[0].value == 345
        assert third.points[0].labels == Labels.of('anotherKey', 'anotherValue')

        assert fourth.descriptor.name == 'yet-another-long-up-down-counter'
        assert fourth.descriptor.description == ''
        assert fourth.descriptor.unit == '1'
        assert fourth.descriptor.constantLabels == Labels.empty()
        assert fourth.descriptor.type == NON_MONOTONIC_LONG
        assert fourth.points.size() == 1
        assert fourth.points[0].value == 456
        assert fourth.points[0].labels == Labels.of('yetAnotherKey', 'yetAnotherValue')
    }

  def "long up down counter memoization"() {
    when:
        def ludcOne = otel.longUpDownCounter('ludc', 'long up down')
        ludcOne.add(10, Labels.of('key', 'value'))
        def ludcTwo = otel.longUpDownCounter('ludc', 'long up down')
        ludcTwo.add(-10, Labels.of('key', 'value'))

    then:
        assert ludcOne.is(ludcTwo)

        def metrics = exportMetrics()
        assert metrics.size() == 1
        def metric = metrics[0]

        assert metric.descriptor.name == 'ludc'
        assert metric.descriptor.description == 'long up down'
        assert metric.descriptor.unit == '1'
        assert metric.descriptor.constantLabels == Labels.empty()
        assert metric.descriptor.type == NON_MONOTONIC_LONG
        assert metric.points.size() == 1
        assert metric.points[0].value == 0
        assert metric.points[0].labels == Labels.of('key', 'value')
    }

  def "double value recorder"() {
    when:
        def dvr = otel.doubleValueRecorder(
                'double-value-recorder', 'a double value-recorder',
                'ms', [key1:'value1', key2:'value2']
        )
        dvr.record(-234.567, Labels.of('key', 'value'))

        dvr = otel.doubleValueRecorder('my-double-value-recorder', 'another double value-recorder', 'µs')
        dvr.record(-123.456, Labels.of('myKey', 'myValue'))

        dvr = otel.doubleValueRecorder('another-double-value-recorder', 'double value-recorder')
        dvr.record(345.678, Labels.of('anotherKey', 'anotherValue'))

        dvr = otel.doubleValueRecorder('yet-another-double-value-recorder')
        dvr.record(456.789, Labels.of('yetAnotherKey', 'yetAnotherValue'))

        def metrics = exportMetrics()
    then:
        assert metrics.size() == 4

        def first = metrics[0]
        def second = metrics[1]
        def third = metrics[2]
        def fourth = metrics[3]

        assert first.descriptor.name == 'double-value-recorder'
        assert first.descriptor.description == 'a double value-recorder'
        assert first.descriptor.unit == 'ms'
        assert first.descriptor.constantLabels == Labels.of(
                'key1', 'value1', 'key2', 'value2'
        )
        assert first.descriptor.type == SUMMARY
        assert first.points.size() == 1
        assert first.points[0].count == 1
        assert first.points[0].sum == -234.567
        assert first.points[0].percentileValues[0].percentile == 0
        assert first.points[0].percentileValues[0].value ==  -234.567
        assert first.points[0].percentileValues[1].percentile == 100
        assert first.points[0].percentileValues[1].value == -234.567
        assert first.points[0].labels == Labels.of('key', 'value')

        assert second.descriptor.name == 'my-double-value-recorder'
        assert second.descriptor.description == 'another double value-recorder'
        assert second.descriptor.unit == 'µs'
        assert second.descriptor.constantLabels == Labels.empty()
        assert second.descriptor.type == SUMMARY
        assert second.points.size() == 1
        assert second.points[0].count == 1
        assert second.points[0].sum == -123.456
        assert second.points[0].percentileValues[0].percentile == 0
        assert second.points[0].percentileValues[0].value == -123.456
        assert second.points[0].percentileValues[1].percentile == 100
        assert second.points[0].percentileValues[1].value == -123.456
        assert second.points[0].labels == Labels.of('myKey', 'myValue')

        assert third.descriptor.name == 'another-double-value-recorder'
        assert third.descriptor.description == 'double value-recorder'
        assert third.descriptor.unit == '1'
        assert third.descriptor.constantLabels == Labels.empty()
        assert third.descriptor.type == SUMMARY
        assert third.points.size() == 1
        assert third.points[0].count == 1
        assert third.points[0].sum == 345.678
        assert third.points[0].percentileValues[0].percentile == 0
        assert third.points[0].percentileValues[0].value == 345.678
        assert third.points[0].percentileValues[1].percentile == 100
        assert third.points[0].percentileValues[1].value == 345.678
        assert third.points[0].labels == Labels.of('anotherKey', 'anotherValue')

        assert fourth.descriptor.name == 'yet-another-double-value-recorder'
        assert fourth.descriptor.description == ''
        assert fourth.descriptor.unit == '1'
        assert fourth.descriptor.constantLabels == Labels.empty()
        assert fourth.descriptor.type == SUMMARY
        assert fourth.points.size() == 1
        assert fourth.points[0].count == 1
        assert fourth.points[0].sum == 456.789
        assert fourth.points[0].percentileValues[0].percentile == 0
        assert fourth.points[0].percentileValues[0].value == 456.789
        assert fourth.points[0].percentileValues[1].percentile == 100
        assert fourth.points[0].percentileValues[1].value == 456.789
        assert fourth.points[0].labels == Labels.of('yetAnotherKey', 'yetAnotherValue')
    }

  def "double value recorder memoization"() {
    when:
        def dvrOne = otel.doubleValueRecorder('dvr', 'double value')
        dvrOne.record(10.1, Labels.of('key', 'value'))
        def dvrTwo = otel.doubleValueRecorder('dvr', 'double value')
        dvrTwo.record(-10.1, Labels.of('key', 'value'))

    then:
        assert dvrOne.is(dvrTwo)

        def metrics = exportMetrics()
        assert metrics.size() == 1
        def metric = metrics[0]

        assert metric.descriptor.name == 'dvr'
        assert metric.descriptor.description == 'double value'
        assert metric.descriptor.unit == '1'
        assert metric.descriptor.constantLabels == Labels.empty()
        assert metric.descriptor.type == SUMMARY
        assert metric.points.size() == 1
        assert metric.points[0].count == 2
        assert metric.points[0].sum == 0
        assert metric.points[0].percentileValues[0].percentile == 0
        assert metric.points[0].percentileValues[0].value == -10.1
        assert metric.points[0].percentileValues[1].percentile == 100
        assert metric.points[0].percentileValues[1].value == 10.1
        assert metric.points[0].labels == Labels.of('key', 'value')
    }

  def "long value recorder"() {
    when:
        def lvr = otel.longValueRecorder(
                'long-value-recorder', 'a long value-recorder',
                'ms', [key1:'value1', key2:'value2']
        )
        lvr.record(-234, Labels.of('key', 'value'))

        lvr = otel.longValueRecorder('my-long-value-recorder', 'another long value-recorder', 'µs')
        lvr.record(-123, Labels.of('myKey', 'myValue'))

        lvr = otel.longValueRecorder('another-long-value-recorder', 'long value-recorder')
        lvr.record(345, Labels.of('anotherKey', 'anotherValue'))

        lvr = otel.longValueRecorder('yet-another-long-value-recorder')
        lvr.record(456, Labels.of('yetAnotherKey', 'yetAnotherValue'))

        def metrics = exportMetrics()
    then:
        assert metrics.size() == 4

        def first = metrics[0]
        def second = metrics[1]
        def third = metrics[2]
        def fourth = metrics[3]

        assert first.descriptor.name == 'long-value-recorder'
        assert first.descriptor.description == 'a long value-recorder'
        assert first.descriptor.unit == 'ms'
        assert first.descriptor.constantLabels == Labels.of(
                'key1', 'value1', 'key2', 'value2'
        )
        assert first.descriptor.type == SUMMARY
        assert first.points.size() == 1
        assert first.points[0].count == 1
        assert first.points[0].sum == -234
        assert first.points[0].percentileValues[0].percentile == 0
        assert first.points[0].percentileValues[0].value == -234
        assert first.points[0].percentileValues[1].percentile == 100
        assert first.points[0].percentileValues[1].value == -234
        assert first.points[0].labels == Labels.of('key', 'value')

        assert second.descriptor.name == 'my-long-value-recorder'
        assert second.descriptor.description == 'another long value-recorder'
        assert second.descriptor.unit == 'µs'
        assert second.descriptor.constantLabels == Labels.empty()
        assert second.descriptor.type == SUMMARY
        assert second.points.size() == 1
        assert second.points[0].count == 1
        assert second.points[0].sum == -123
        assert second.points[0].percentileValues[0].percentile == 0
        assert second.points[0].percentileValues[0].value == -123
        assert second.points[0].percentileValues[1].percentile == 100
        assert second.points[0].percentileValues[1].value == -123
        assert second.points[0].labels == Labels.of('myKey', 'myValue')

        assert third.descriptor.name == 'another-long-value-recorder'
        assert third.descriptor.description == 'long value-recorder'
        assert third.descriptor.unit == '1'
        assert third.descriptor.constantLabels == Labels.empty()
        assert third.descriptor.type == SUMMARY
        assert third.points.size() == 1
        assert third.points[0].count == 1
        assert third.points[0].sum == 345
        assert third.points[0].percentileValues[0].percentile == 0
        assert third.points[0].percentileValues[0].value == 345
        assert third.points[0].percentileValues[1].percentile == 100
        assert third.points[0].percentileValues[1].value == 345
        assert third.points[0].labels == Labels.of('anotherKey', 'anotherValue')

        assert fourth.descriptor.name == 'yet-another-long-value-recorder'
        assert fourth.descriptor.description == ''
        assert fourth.descriptor.unit == '1'
        assert fourth.descriptor.constantLabels == Labels.empty()
        assert fourth.descriptor.type == SUMMARY
        assert fourth.points.size() == 1
        assert fourth.points[0].count == 1
        assert fourth.points[0].sum == 456
        assert fourth.points[0].percentileValues[0].percentile == 0
        assert fourth.points[0].percentileValues[0].value == 456
        assert fourth.points[0].percentileValues[1].percentile == 100
        assert fourth.points[0].percentileValues[1].value == 456
        assert fourth.points[0].labels == Labels.of('yetAnotherKey', 'yetAnotherValue')
    }

  def "long value recorder memoization"() {
    when:
        def lvrOne = otel.longValueRecorder('lvr', 'long value')
        lvrOne.record(10, Labels.of('key', 'value'))
        def lvrTwo = otel.longValueRecorder('lvr', 'long value')
        lvrTwo.record(-10, Labels.of('key', 'value'))

    then:
        assert lvrOne.is(lvrTwo)

        def metrics = exportMetrics()
        assert metrics.size() == 1
        def metric = metrics[0]

        assert metric.descriptor.name == 'lvr'
        assert metric.descriptor.description == 'long value'
        assert metric.descriptor.unit == '1'
        assert metric.descriptor.constantLabels == Labels.empty()
        assert metric.descriptor.type == SUMMARY
        assert metric.points.size() == 1
        assert metric.points[0].count == 2
        assert metric.points[0].sum == 0
        assert metric.points[0].percentileValues[0].percentile == 0
        assert metric.points[0].percentileValues[0].value == -10
        assert metric.points[0].percentileValues[1].percentile == 100
        assert metric.points[0].percentileValues[1].value == 10
        assert metric.points[0].labels == Labels.of('key', 'value')
    }

}
