/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseAsyncClientTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

class CouchbaseAsyncClient26Test extends AbstractCouchbaseAsyncClientTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return Couchbase26Util.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected List<AttributeAssertion> couchbaseAttributes() {
    return Couchbase26Util.couchbaseAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseQueryAttributes() {
    return Couchbase26Util.couchbaseQueryAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseClusterManagerAttributes() {
    return Couchbase26Util.couchbaseClusterManagerAttributes();
  }

  @Override
  protected List<AttributeAssertion> couchbaseN1qlAttributes() {
    return Couchbase26Util.couchbaseN1qlAttributes();
  }
}
