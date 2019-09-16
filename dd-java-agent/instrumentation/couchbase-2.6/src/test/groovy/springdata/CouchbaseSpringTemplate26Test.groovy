package springdata

import datadog.trace.agent.test.asserts.TraceAssert

class CouchbaseSpringTemplate26Test extends CouchbaseSpringTemplateTest {
  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object parentSpan = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, parentSpan)
  }
}
