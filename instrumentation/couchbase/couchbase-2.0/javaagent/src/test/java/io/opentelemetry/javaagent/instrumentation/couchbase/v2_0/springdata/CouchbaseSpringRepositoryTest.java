/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.springdata;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringRepositoryTest;
import io.opentelemetry.instrumentation.couchbase.springdata.TestDocument;
import io.opentelemetry.instrumentation.couchbase.springdata.TestRepository;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseUtil;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;

class CouchbaseSpringRepositoryTest extends AbstractCouchbaseSpringRepositoryTest {

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

  @Override
  protected TestDocument findById(TestRepository repository, String id) {
    return repository.findOne(id);
  }

  @Override
  protected void deleteById(TestRepository repository, String id) {
    repository.delete(id);
  }
}
