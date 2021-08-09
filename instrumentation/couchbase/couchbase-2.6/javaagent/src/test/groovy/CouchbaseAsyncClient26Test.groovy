/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData

class CouchbaseAsyncClient26Test extends AbstractCouchbaseAsyncClientTest {

  @Override
  void assertCouchbaseCall(TraceAssert trace,
                           int index,
                           Object name,
                           SpanData parentSpan = null,
                           String bucketName = null,
                           Object statement = null,
                           Object operation = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, parentSpan, bucketName, statement, operation)
  }
}
