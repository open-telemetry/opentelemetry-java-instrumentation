package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;

final class AerospikeMetricsAdvice {
  private AerospikeMetricsAdvice() {}

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(SemanticAttributes.NETWORK_TYPE);
      attributes.add(SemanticAttributes.NETWORK_TRANSPORT);
      attributes.add(SemanticAttributes.SERVER_ADDRESS);
      attributes.add(SemanticAttributes.SERVER_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_NAME);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_PORT);
    }

    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(attributes);
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(SemanticAttributes.NETWORK_TYPE);
      attributes.add(SemanticAttributes.NETWORK_TRANSPORT);
      attributes.add(SemanticAttributes.SERVER_ADDRESS);
      attributes.add(SemanticAttributes.SERVER_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_NAME);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_PORT);
    }

    ((ExtendedLongUpDownCounterBuilder) builder).setAttributesAdvice(attributes);
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(SemanticAttributes.NETWORK_TYPE);
      attributes.add(SemanticAttributes.NETWORK_TRANSPORT);
      attributes.add(SemanticAttributes.SERVER_ADDRESS);
      attributes.add(SemanticAttributes.SERVER_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_NAME);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_PORT);
    }

    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(attributes);
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(SemanticAttributes.NETWORK_TYPE);
      attributes.add(SemanticAttributes.NETWORK_TRANSPORT);
      attributes.add(SemanticAttributes.SERVER_ADDRESS);
      attributes.add(SemanticAttributes.SERVER_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_NAME);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_PORT);
    }

    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(attributes);
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(SemanticAttributes.NETWORK_TYPE);
      attributes.add(SemanticAttributes.NETWORK_TRANSPORT);
      attributes.add(SemanticAttributes.SERVER_ADDRESS);
      attributes.add(SemanticAttributes.SERVER_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_NAME);
      attributes.add(SemanticAttributes.NET_SOCK_PEER_PORT);
    }

    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(attributes);
  }
}
