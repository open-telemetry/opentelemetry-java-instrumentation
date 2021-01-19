/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import io.opentelemetry.api.DefaultOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator
import org.apache.camel.component.aws.sqs.SqsConfiguration
import org.apache.camel.component.aws.sqs.SqsEndpoint
import spock.lang.Specification

class MessageAttributeNamesFactoryTest extends Specification {

  def "should Add Single Field To Empty Names List"() {

    setup:
    // traceparent = single field
    OpenTelemetry.set(
      DefaultOpenTelemetry.builder()
        .setPropagators(ContextPropagators.create(W3CBaggagePropagator.getInstance())).build())

    def sqsConfiguration = Mock(SqsConfiguration) {
      getMessageAttributeNames() >> null
    }
    def sqsEndpoint = Mock(SqsEndpoint) {
      getConfiguration() >> sqsConfiguration
    }

    when:
    def names = MessageAttributeNamesFactory.withTextMapPropagatorFields(sqsEndpoint)

    then:
    names == "baggage"
  }

  def "should Add Single Field To Singleton Names List"() {
    setup:
    // traceparent = single field
    OpenTelemetry.set(
      DefaultOpenTelemetry.builder().
        setPropagators(ContextPropagators.create(W3CBaggagePropagator.getInstance())).build())

    def sqsConfiguration = Mock(SqsConfiguration) {
      getMessageAttributeNames() >> "myAttribute"
    }
    def sqsEndpoint = Mock(SqsEndpoint) {
      getConfiguration() >> sqsConfiguration
    }

    when:
    def names = MessageAttributeNamesFactory.withTextMapPropagatorFields(sqsEndpoint)

    then:
    names == "baggage,myAttribute"
  }

  def "should Add Single Field To Multiple Names List"() {
    setup:
    // traceparent = single field
    OpenTelemetry.set(
      DefaultOpenTelemetry.builder().
        setPropagators(ContextPropagators.create(W3CBaggagePropagator.getInstance())).build())

    def sqsConfiguration = Mock(SqsConfiguration) {
      getMessageAttributeNames() >> "myAttributeOne,myAttributeTwo"
    }
    def sqsEndpoint = Mock(SqsEndpoint) {
      getConfiguration() >> sqsConfiguration
    }

    when:
    def names = MessageAttributeNamesFactory.withTextMapPropagatorFields(sqsEndpoint)

    then:
    names == "baggage,myAttributeOne,myAttributeTwo"
  }

  def "should Add Multiple Fields To Empty Names List"() {

    setup:
    // traceparent = single field
    OpenTelemetry.set(
      DefaultOpenTelemetry.builder().
        setPropagators(ContextPropagators.create(B3Propagator.getInstance())).build())

    def sqsConfiguration = Mock(SqsConfiguration) {
      getMessageAttributeNames() >> null
    }
    def sqsEndpoint = Mock(SqsEndpoint) {
      getConfiguration() >> sqsConfiguration
    }

    when:
    def names = MessageAttributeNamesFactory.withTextMapPropagatorFields(sqsEndpoint)

    then:
    names == "X-B3-TraceId,X-B3-SpanId,X-B3-Sampled,b3"
  }

  def "should Add Multiple Fields To Singleton Names List"() {
    setup:
    // traceparent = single field
    OpenTelemetry.set(
      DefaultOpenTelemetry.builder()
        .setPropagators(ContextPropagators.create(B3Propagator.getInstance())).build())

    def sqsConfiguration = Mock(SqsConfiguration) {
      getMessageAttributeNames() >> "myAttribute"
    }
    def sqsEndpoint = Mock(SqsEndpoint) {
      getConfiguration() >> sqsConfiguration
    }

    when:
    def names = MessageAttributeNamesFactory.withTextMapPropagatorFields(sqsEndpoint)

    then:
    names == "X-B3-TraceId,X-B3-SpanId,X-B3-Sampled,b3,myAttribute"
  }

  def "should Add Multiple Fields To Multiple Names List"() {
    setup:
    // traceparent = single field
    OpenTelemetry.set(
      DefaultOpenTelemetry.builder()
        .setPropagators(ContextPropagators.create(B3Propagator.getInstance())).build())

    def sqsConfiguration = Mock(SqsConfiguration) {
      getMessageAttributeNames() >> "myAttributeOne,myAttributeTwo"
    }
    def sqsEndpoint = Mock(SqsEndpoint) {
      getConfiguration() >> sqsConfiguration
    }

    when:
    def names = MessageAttributeNamesFactory.withTextMapPropagatorFields(sqsEndpoint)

    then:
    names == "X-B3-TraceId,X-B3-SpanId,X-B3-Sampled,b3,myAttributeOne,myAttributeTwo"
  }
}
