/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes
import io.opentelemetry.semconv.NetworkAttributes

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class CouchbaseSpanUtil {
  // Reusable span assertion method.  Cannot directly override AbstractCouchbaseTest.assertCouchbaseSpan because
  // Of the class hierarchy of these tests
  static void assertCouchbaseCall(TraceAssert trace,
                                  int index,
                                  Object spanName,
                                  SpanData parentSpan = null,
                                  String bucketName = null,
                                  Object statement = null,
                                  Object operation = null) {
    trace.span(index) {
      name spanName
      kind CLIENT
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "$DbIncubatingAttributes.DB_SYSTEM" "couchbase"
        "$DbIncubatingAttributes.DB_NAME" bucketName
        "$DbIncubatingAttributes.DB_STATEMENT" statement
        "$DbIncubatingAttributes.DB_OPERATION"(operation ?: spanName)

        // Because of caching, not all requests hit the server so these attributes may be absent
        "$NetworkAttributes.NETWORK_TYPE" { it == "ipv4" || it == null }
        "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == null }
        "$NetworkAttributes.NETWORK_PEER_PORT" { it instanceof Number || it == null }

        // Because of caching, not all requests hit the server so this tag may be absent
        "couchbase.local.address" { it == null || it instanceof String }

        // Not all couchbase operations have operation id.  Notably, 'ViewQuery's do not
        // We assign a spanName of 'Bucket.query' and this is shared with n1ql queries
        // that do have operation ids
        "couchbase.operation_id" { it == null || it instanceof String }
      }
    }
  }
}
