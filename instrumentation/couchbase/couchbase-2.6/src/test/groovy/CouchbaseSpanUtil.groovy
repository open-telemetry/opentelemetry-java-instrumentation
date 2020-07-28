/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes

import static io.opentelemetry.trace.Span.Kind.CLIENT

class CouchbaseSpanUtil {
  // Reusable span assertion method.  Cannot directly override AbstractCouchbaseTest.assertCouchbaseSpan because
  // Of the class hierarchy of these tests
  static void assertCouchbaseCall(TraceAssert trace, int index, Object name, String bucketName = null, Object parentSpan = null) {
    trace.span(index) {
      operationName name
      spanKind CLIENT
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {

        // Because of caching, not all requests hit the server so these attributes may be absent
        "${SemanticAttributes.NET_PEER_NAME.key()}" { it == "localhost" || it == "127.0.0.1" || it == null }
        "${SemanticAttributes.NET_PEER_PORT.key()}" { it == null || Number }

        "${SemanticAttributes.DB_SYSTEM.key()}" "couchbase"
        if (bucketName != null) {
          "${SemanticAttributes.DB_NAME.key()}" bucketName
        }

        // Because of caching, not all requests hit the server so this tag may be absent
        "local.address" { it == null || String }

        // Not all couchbase operations have operation id.  Notably, 'ViewQuery's do not
        // We assign a spanName of 'Bucket.query' and this is shared with n1ql queries
        // that do have operation ids
        "couchbase.operation_id" { it == null || String }

        "${SemanticAttributes.DB_STATEMENT.key()}" name
      }
    }
  }
}
