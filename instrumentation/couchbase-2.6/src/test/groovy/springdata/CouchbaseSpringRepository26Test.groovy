package springdata

import io.opentelemetry.auto.test.asserts.TraceAssert

class CouchbaseSpringRepository26Test extends CouchbaseSpringRepositoryTest {
  @Override
  def getFindAllStatememt() {
    return 'ViewQuery(doc/all){params="reduce=false&stale=false", includeDocs}'
  }

  @Override
  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, String dbStatement = null, Object parentSpan = null) {
    CouchbaseSpanUtil.assertCouchbaseCall(trace, index, name, bucketName, dbStatement, parentSpan)
  }
}
