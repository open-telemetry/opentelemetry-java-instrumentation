/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.auto.test.asserts.TraceAssert

class CouchbaseAsyncClient26Test extends AbstractCouchbaseAsyncClientTest {

  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, Object name, String bucketName = null, Object parentSpan = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, parentSpan)
  }
}
