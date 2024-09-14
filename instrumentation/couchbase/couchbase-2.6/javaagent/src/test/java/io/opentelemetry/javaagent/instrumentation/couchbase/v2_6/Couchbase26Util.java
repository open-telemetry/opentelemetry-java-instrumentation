/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig;
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.NetworkAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Couchbase26Util {

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
    return Arrays.asList(
        equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
        equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
        satisfies(NetworkAttributes.NETWORK_PEER_PORT, val -> assertThat(val).isNotNull()),
        satisfies(
            AttributeKey.stringKey("couchbase.local.address"), val -> assertThat(val).isNotNull()),
        satisfies(
            AttributeKey.stringKey("couchbase.operation_id"), val -> assertThat(val).isNotNull()));
  }

  private Couchbase26Util() {}
}
