/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo.Node;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CouchbaseAttributesGetter
    implements DbClientAttributesGetter<CouchbaseRequestInfo, Void> {

  @Override
  public String getDbSystemName(CouchbaseRequestInfo couchbaseRequest) {
    return DbSystemNameIncubatingValues.COUCHBASE;
  }

  @Override
  @Nullable
  public String getDbNamespace(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.getBucket();
  }

  @Override
  @Nullable
  public String getDbQueryText(CouchbaseRequestInfo couchbaseRequest) {
    if (couchbaseRequest.getSqlQueryWithSummary() != null) {
      return couchbaseRequest.getSqlQueryWithSummary().getQueryText();
    }
    if (couchbaseRequest.getSqlQuery() != null) {
      return couchbaseRequest.getSqlQuery().getQueryText();
    }
    return null;
  }

  @Override
  @Nullable
  public String getDbQuerySummary(CouchbaseRequestInfo couchbaseRequest) {
    if (couchbaseRequest.getSqlQueryWithSummary() != null) {
      return couchbaseRequest.getSqlQueryWithSummary().getQuerySummary();
    }
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(CouchbaseRequestInfo couchbaseRequest) {
    return couchbaseRequest.getOperation();
  }

  @Override
  @Nullable
  public String getServerAddress(CouchbaseRequestInfo couchbaseRequest) {
    // the old conventions never described a server for Couchbase, and they are frozen
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    CouchbaseServerTarget target = couchbaseRequest.getServerTarget();
    if (target != null) {
      return target.getAddress();
    }
    Node node = couchbaseRequest.getNode();
    return node == null ? null : node.getBackendAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(CouchbaseRequestInfo couchbaseRequest) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    CouchbaseServerTarget target = couchbaseRequest.getServerTarget();
    if (target != null) {
      // a target that names several seeds already carries the port of each of them
      return target.getPort();
    }
    Node node = couchbaseRequest.getNode();
    if (node == null || node.getBackendPort() == 0) {
      return null;
    }
    return node.getBackendPort();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CouchbaseRequestInfo request, @Nullable Void unused) {
    Node node = request.getNode();
    if (node == null) {
      return null;
    }
    SocketAddress address = node.getPeerAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
