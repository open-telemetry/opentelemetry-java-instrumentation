/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.springdata;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringTemplateTest;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseUtil;

class CouchbaseSpringTemplateTest extends AbstractCouchbaseSpringTemplateTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return CouchbaseUtil.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }
}
