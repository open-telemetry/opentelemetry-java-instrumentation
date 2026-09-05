/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.springdata;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.opentelemetry.instrumentation.couchbase.springdata.AbstractCouchbaseSpringRepositoryTest;
import io.opentelemetry.instrumentation.couchbase.springdata.TestDocument;
import io.opentelemetry.instrumentation.couchbase.springdata.TestRepository;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseUtil;

class CouchbaseSpringRepositoryTest extends AbstractCouchbaseSpringRepositoryTest {

  @Override
  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort) {
    return CouchbaseUtil.envBuilder(bucketSettings, carrierDirectPort, httpDirectPort);
  }

  @Override
  protected boolean includesNetworkAttributes() {
    return emitStableDatabaseSemconv();
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

  @Override
  protected boolean includesOldServerAddressAttribute() {
    // This module never resolves a node string to pair with the actual peer address.
    return false;
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
