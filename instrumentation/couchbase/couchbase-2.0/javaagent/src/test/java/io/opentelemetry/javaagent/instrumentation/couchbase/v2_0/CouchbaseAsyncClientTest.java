/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseAsyncClientTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

class CouchbaseAsyncClientTest extends AbstractCouchbaseAsyncClientTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return CouchbaseUtil.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected List<AttributeAssertion> couchbaseAttributes() {
    return CouchbaseUtil.couchbaseAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseQueryAttributes() {
    return CouchbaseUtil.couchbaseQueryAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseClusterManagerAttributes() {
    return CouchbaseUtil.couchbaseClusterManagerAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseN1qlAttributes() {
    return CouchbaseUtil.couchbaseN1qlAttributes();
  }
}
