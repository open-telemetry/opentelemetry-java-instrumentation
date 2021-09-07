/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

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
        "${SemanticAttributes.DB_SYSTEM.key}" "couchbase"
        "${SemanticAttributes.DB_NAME.key}" bucketName
        "${SemanticAttributes.DB_STATEMENT.key}" statement
        "${SemanticAttributes.DB_OPERATION.key}"(operation ?: spanName)

        // Because of caching, not all requests hit the server so these attributes may be absent
        "${SemanticAttributes.NET_PEER_NAME.key}" { it == "localhost" || it == "127.0.0.1" || it == null }
        "${SemanticAttributes.NET_PEER_PORT.key}" { it == null || Number }

        // Because of caching, not all requests hit the server so this tag may be absent
        "couchbase.local.address" { it == null || String }

        // Not all couchbase operations have operation id.  Notably, 'ViewQuery's do not
        // We assign a spanName of 'Bucket.query' and this is shared with n1ql queries
        // that do have operation ids
        "couchbase.operation_id" { it == null || String }
      }
    }
  }
}
