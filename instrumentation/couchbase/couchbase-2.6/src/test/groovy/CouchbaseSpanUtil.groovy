/*
 * Copyright 2020, OpenTelemetry Authors
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
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData

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
      tags {

        // Because of caching, not all requests hit the server so these tags may be absent
        "$MoreTags.NET_PEER_NAME" { it == "localhost" || it == "127.0.0.1" || it == null }
        "$MoreTags.NET_PEER_PORT" { it == null || Number }

        "$Tags.DB_TYPE" "couchbase"
        if (bucketName != null) {
          "$Tags.DB_INSTANCE" bucketName
        }

        // Because of caching, not all requests hit the server so this tag may be absent
        "local.address" { it == null || String }

        // Not all couchbase operations have operation id.  Notably, 'ViewQuery's do not
        // We assign a resourceName of 'Bucket.query' and this is shared with n1ql queries
        // that do have operation ids
        "couchbase.operation_id" { it == null || String }

        "$Tags.DB_STATEMENT" name
      }
    }
  }
}
