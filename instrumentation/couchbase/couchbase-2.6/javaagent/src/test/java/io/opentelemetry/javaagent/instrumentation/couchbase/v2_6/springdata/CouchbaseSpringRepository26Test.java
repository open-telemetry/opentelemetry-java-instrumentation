/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.springdata;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringRepositoryTest;
import io.opentelemetry.instrumentation.couchbase.springdata.TestDocument;
import io.opentelemetry.instrumentation.couchbase.springdata.TestRepository;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_6.Couchbase26Util;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

class CouchbaseSpringRepository26Test extends AbstractCouchbaseSpringRepositoryTest {

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
  protected TestDocument findById(TestRepository repository, String id) {
    return repository.findById(id).get();
  }

  @Override
  protected void deleteById(TestRepository repository, String id) {
    repository.deleteById(id);
  }
}
