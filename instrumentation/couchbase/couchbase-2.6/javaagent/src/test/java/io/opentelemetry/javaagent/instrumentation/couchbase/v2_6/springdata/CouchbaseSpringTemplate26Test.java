/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.springdata;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringTemplateTest;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.Couchbase26Util;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

class CouchbaseSpringTemplate26Test extends AbstractCouchbaseSpringTemplateTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return Couchbase26Util.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected List<AttributeAssertion> couchbaseAttributes() {
    return Couchbase26Util.couchbaseAttributes();
  }
}
