/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkAttributes;
import io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.AerospikeSemanticAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;

final class AerospikeMetricsAdvice {
  private AerospikeMetricsAdvice() {}

  static void applyRequestCounterAdvice(LongCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongCounterBuilder)) {
      return;
    }

    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.DB_SYSTEM);
    attributes.add(SemanticAttributes.DB_OPERATION);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY);
    attributes.add(SemanticAttributes.NETWORK_TYPE);
    attributes.add(NetworkAttributes.NETWORK_PEER_PORT);
    attributes.add(NetworkAttributes.NETWORK_PEER_ADDRESS);

    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(attributes);
  }

  static void applyConcurrencyUpDownCounterAdvice(LongUpDownCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongUpDownCounterBuilder)) {
      return;
    }

    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.DB_SYSTEM);
    attributes.add(SemanticAttributes.DB_OPERATION);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY);
    attributes.add(SemanticAttributes.NETWORK_TYPE);
    attributes.add(NetworkAttributes.NETWORK_PEER_PORT);
    attributes.add(NetworkAttributes.NETWORK_PEER_ADDRESS);

    ((ExtendedLongUpDownCounterBuilder) builder).setAttributesAdvice(attributes);
  }

  static void applyResponseCounterAdvice(LongCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongCounterBuilder)) {
      return;
    }

    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.DB_SYSTEM);
    attributes.add(SemanticAttributes.DB_OPERATION);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_STATUS);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE);
    attributes.add(SemanticAttributes.NETWORK_TYPE);
    attributes.add(NetworkAttributes.NETWORK_PEER_PORT);
    attributes.add(NetworkAttributes.NETWORK_PEER_ADDRESS);

    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(attributes);
  }

  static void applyClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }

    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.DB_SYSTEM);
    attributes.add(SemanticAttributes.DB_OPERATION);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_STATUS);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE);
    attributes.add(SemanticAttributes.NETWORK_TYPE);
    attributes.add(NetworkAttributes.NETWORK_PEER_PORT);
    attributes.add(NetworkAttributes.NETWORK_PEER_ADDRESS);

    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(attributes);
  }

  static void applyRecordSizeAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }

    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.DB_SYSTEM);
    attributes.add(SemanticAttributes.DB_OPERATION);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE);
    attributes.add(AerospikeSemanticAttributes.AEROSPIKE_STATUS);
    attributes.add(SemanticAttributes.NETWORK_TYPE);
    attributes.add(NetworkAttributes.NETWORK_PEER_PORT);
    attributes.add(NetworkAttributes.NETWORK_PEER_ADDRESS);

    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(attributes);
  }
}
