package springdata


import datadog.trace.agent.test.asserts.TraceAssert

class CouchbaseSpringRepository26Test extends CouchbaseSpringRepositoryTest {
  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object parentSpan = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, parentSpan)
  }
}
