/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;

import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig;
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Couchbase26Util {

  private static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.couchbase.experimental-span-attributes";

  public static DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    // Couchbase seems to be really slow to start sometimes
    long timeout = TimeUnit.SECONDS.toMillis(20);
    return DefaultCouchbaseEnvironment.builder()
        .bootstrapCarrierDirectPort(carrierDirectPort)
        .bootstrapHttpDirectPort(httpDirectPort)
        // settings to try to reduce variability in the tests:
        .runtimeMetricsCollectorConfig(DefaultMetricsCollectorConfig.create(0, TimeUnit.DAYS))
        .networkLatencyMetricsCollectorConfig(
            DefaultLatencyMetricsCollectorConfig.create(0, TimeUnit.DAYS))
        .computationPoolSize(1)
        .connectTimeout(timeout)
        .disconnectTimeout(timeout)
        .kvTimeout(timeout)
        .managementTimeout(timeout)
        .queryTimeout(timeout)
        .viewTimeout(timeout)
        .keepAliveTimeout(timeout)
        .searchTimeout(timeout)
        .analyticsTimeout(timeout)
        .socketConnectTimeout((int) timeout);
  }

  public static List<AttributeAssertion> couchbaseAttributes() {
    return couchbaseKvAttributes();
  }

  public static List<AttributeAssertion> couchbaseKvAttributes() {
    return baseNetworkAttributes().withLocalAddress().withOperationId().build();
  }

  public static List<AttributeAssertion> couchbaseQueryAttributes() {
    return baseNetworkAttributes().withLocalAddress().build();
  }

  public static List<AttributeAssertion> couchbaseN1qlAttributes() {
    return baseNetworkAttributes().withOperationId().build();
  }

  public static List<AttributeAssertion> couchbaseClusterManagerAttributes() {
    return baseNetworkAttributes().build();
  }

  private static AttributeAssertionBuilder baseNetworkAttributes() {
    return new AttributeAssertionBuilder()
        .add(equalTo(NETWORK_TYPE, "ipv4"))
        .add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"))
        .add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
  }

  private static class AttributeAssertionBuilder {
    private final List<AttributeAssertion> assertions = new ArrayList<>();

    AttributeAssertionBuilder add(AttributeAssertion assertion) {
      assertions.add(assertion);
      return this;
    }

    AttributeAssertionBuilder withLocalAddress() {
      if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
        assertions.add(satisfies(stringKey("couchbase.local.address"), val -> val.isNotNull()));
      }
      return this;
    }

    AttributeAssertionBuilder withOperationId() {
      if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
        assertions.add(satisfies(stringKey("couchbase.operation_id"), val -> val.isNotNull()));
      }
      return this;
    }

    List<AttributeAssertion> build() {
      return new ArrayList<>(assertions);
    }
  }

  private Couchbase26Util() {}
}
