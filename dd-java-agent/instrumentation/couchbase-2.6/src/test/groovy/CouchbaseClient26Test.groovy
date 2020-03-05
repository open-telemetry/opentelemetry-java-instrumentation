import datadog.trace.agent.test.asserts.TraceAssert
import spock.lang.Retry

@Retry
class CouchbaseClient26Test extends CouchbaseClientTest {
  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object parentSpan = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, parentSpan)
  }
}
