import datadog.trace.agent.test.asserts.TraceAssert
import spock.lang.Retry

@Retry
class CouchbaseAsyncClient26Test extends CouchbaseAsyncClientTest {

  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object parentSpan = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, parentSpan)
  }
}
