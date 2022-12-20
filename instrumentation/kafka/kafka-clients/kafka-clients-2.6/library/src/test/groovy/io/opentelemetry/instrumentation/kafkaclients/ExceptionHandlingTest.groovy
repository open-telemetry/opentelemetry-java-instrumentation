/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import java.lang.reflect.Proxy
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer

class ExceptionHandlingTest extends InstrumentationSpecification implements LibraryTestTrait {

  def "test consumer exception propagates to caller"() throws Exception {
    setup:
    def consumer = Proxy.newProxyInstance(ExceptionHandlingTest.getClassLoader(), new Class[] { Consumer }) { proxy, method, args ->
      throw new IllegalStateException("can't invoke")
    } as Consumer
    KafkaTelemetry telemetry = KafkaTelemetry.builder(getOpenTelemetry())
      .build()
    def wrappedConsumer = telemetry.wrap(consumer)

    when:
    wrappedConsumer.poll(Duration.ofMillis(1))
    then:
    thrown IllegalStateException
  }

  def "test producer exception propagates to caller"() throws Exception {
    setup:
    def producer = Proxy.newProxyInstance(ExceptionHandlingTest.getClassLoader(), new Class[] { Producer }) { proxy, method, args ->
      throw new IllegalStateException("can't invoke")
    } as Producer
    KafkaTelemetry telemetry = KafkaTelemetry.builder(getOpenTelemetry())
      .build()
    def wrappedProducer = telemetry.wrap(producer)

    when:
    wrappedProducer.flush()
    then:
    thrown IllegalStateException
  }
}
