package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CouchbaseAttributesGetter
    implements DbClientAttributesGetter<CouchbaseRequestInfo, Void> {

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystemName(CouchbaseRequestInfo couchbaseRequest) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.COUCHBASE;
  }

  @Override
  @Nullable
  public String getDbNamespace(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.bucket();
  }

  @Override
  @Nullable
  public String getDbQueryText(CouchbaseRequestInfo couchbaseRequest) {
    SqlStatementInfo info = couchbaseRequest.getSqlStatementInfo();
    return info != null ? info.getQueryText() : null;
  }

  @Override
  @Nullable
  public String getDbQuerySummary(CouchbaseRequestInfo couchbaseRequest) {
    SqlStatementInfo info = couchbaseRequest.getSqlStatementInfo();
    return info != null ? info.getQuerySummary() : null;
  }

  @Override
  @Nullable
  public String getDbOperationName(CouchbaseRequestInfo couchbaseRequest) {
    SqlStatementInfo info = couchbaseRequest.getSqlStatementInfo();
    return info != null ? info.getOperationName() : null;
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CouchbaseRequestInfo request, @Nullable Void unused) {
    SocketAddress address = request.getPeerAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
