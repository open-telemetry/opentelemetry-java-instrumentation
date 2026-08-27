/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo.Node;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CouchbaseAttributesGetter
    implements DbClientAttributesGetter<CouchbaseRequestInfo, Void>,
        AttributesExtractor<CouchbaseRequestInfo, Void> {

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
    // in old-semconv mode onEnd() reports the node that answered instead of the configured target
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    CouchbaseServerTarget target = couchbaseRequest.getServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(CouchbaseRequestInfo couchbaseRequest) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    CouchbaseServerTarget target = couchbaseRequest.getServerTarget();
    // a target that names several seeds already carries the port of each of them
    return target == null ? null : target.getPort();
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

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CouchbaseRequestInfo request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CouchbaseRequestInfo request,
      @Nullable Void unused,
      @Nullable Throwable error) {
    if (emitStableDatabaseSemconv() && request.getServerTarget() != null) {
      return;
    }
    Node node = request.getNode();
    if (node == null) {
      return;
    }
    attributes.put(SERVER_ADDRESS, node.getBackendAddress());
    int serverPort = node.getBackendPort();
    if (serverPort > 0) {
      attributes.put(SERVER_PORT, serverPort);
    }
  }
}
