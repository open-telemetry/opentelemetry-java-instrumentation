/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springdata

import io.opentelemetry.instrumentation.test.asserts.TraceAssert

class CouchbaseSpringRepository26Test extends AbstractCouchbaseSpringRepositoryTest {

  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, Object name, String bucketName = null, Object parentSpan = null, Object statement = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, parentSpan, statement)
  }
}
