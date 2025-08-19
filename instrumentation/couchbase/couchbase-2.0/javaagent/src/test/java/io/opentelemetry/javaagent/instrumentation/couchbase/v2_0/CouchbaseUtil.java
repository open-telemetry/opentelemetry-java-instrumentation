/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static java.util.Collections.emptyList;

import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig;
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CouchbaseUtil {

  public static List<AttributeAssertion> couchbaseAttributes() {
    return couchbaseKvAttributes();
  }

  public static List<AttributeAssertion> couchbaseKvAttributes() {
    return emptyList();
  }

  public static List<AttributeAssertion> couchbaseQueryAttributes() {
    return emptyList();
  }

  public static List<AttributeAssertion> couchbaseClusterManagerAttributes() {
    return emptyList();
  }

  public static List<AttributeAssertion> couchbaseN1qlAttributes() {
    return emptyList();
  }

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

  private CouchbaseUtil() {}
}
