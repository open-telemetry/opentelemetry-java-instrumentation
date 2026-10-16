/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseAsyncClientTest;

class CouchbaseAsyncClientTest extends AbstractCouchbaseAsyncClientTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return CouchbaseUtil.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected boolean includesNetworkAttributes() {
    return true;
  }

  @Override
  protected boolean includesExperimentalLocalAddressAttribute() {
    // The core-io versions before 1.6.0 have no localSocket field to capture it from.
    return false;
  }

  @Override
  protected boolean includesExperimentalOperationIdAttribute() {
    // The core-io versions before 1.6.0 have no CouchbaseRequest.operationId() to correlate with.
    return false;
  }
}
