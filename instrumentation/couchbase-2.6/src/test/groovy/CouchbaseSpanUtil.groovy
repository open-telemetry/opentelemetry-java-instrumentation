import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData

class CouchbaseSpanUtil {
  // Reusable span assertion method.  Cannot directly override AbstractCouchbaseTest.assertCouchbaseSpan because
  // Of the class hierarchy of these tests
  static void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object parentSpan = null) {
    trace.span(index) {
      operationName "couchbase.call"
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      tags {
        "$MoreTags.SERVICE_NAME" "couchbase"
        "$MoreTags.RESOURCE_NAME" name
        "$MoreTags.SPAN_TYPE" SpanTypes.COUCHBASE
        "$Tags.COMPONENT" "couchbase-client"
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT

        // Because of caching, not all requests hit the server so these tags may be absent
        "$Tags.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" || it == null }
        "$Tags.PEER_PORT" { it == null || Number }

        "$Tags.DB_TYPE" "couchbase"
        if (bucketName != null) {
          "bucket" bucketName
        }

        // Because of caching, not all requests hit the server so this tag may be absent
        "local.address" { it == null || String }

        // Not all couchbase operations have operation id.  Notably, 'ViewQuery's do not
        // We assign a resourceName of 'Bucket.query' and this is shared with n1ql queries
        // that do have operation ids
        "couchbase.operation_id" { it == null || String }
      }
    }
  }
}
