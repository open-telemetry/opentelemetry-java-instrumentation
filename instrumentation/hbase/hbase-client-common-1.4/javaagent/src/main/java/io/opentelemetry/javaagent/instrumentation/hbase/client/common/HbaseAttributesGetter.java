/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;

final class HbaseAttributesGetter implements DbClientAttributesGetter<HbaseRequest, Void> {

  @Override
  public String getDbSystemName(HbaseRequest hbaseRequest) {
    return DbSystemNameIncubatingValues.HBASE;
  }

  @Nullable
  @Override
  public String getDbNamespace(HbaseRequest hbaseRequest) {
    TableName tableName = hbaseRequest.getTableName();
    return tableName == null ? null : tableName.getNamespaceAsString();
  }

  @Nullable
  @Override
  public String getDbCollectionName(HbaseRequest hbaseRequest) {
    TableName tableName = hbaseRequest.getTableName();
    return tableName == null ? null : tableName.getNameAsString();
  }

  @Nullable
  @Override
  @SuppressWarnings("deprecation") // using deprecated semconv
  public String getDbName(HbaseRequest hbaseRequest) {
    TableName tableName = hbaseRequest.getTableName();
    return tableName == null ? null : tableName.getNameAsString();
  }

  @Nullable
  @Override
  @SuppressWarnings("deprecation") // using deprecated semconv
  public String getUser(HbaseRequest hbaseRequest) {
    return hbaseRequest.getUser();
  }

  @Nullable
  @Override
  public String getDbQueryText(HbaseRequest hbaseRequest) {
    return null;
  }

  @Nullable
  @Override
  public String getDbOperationName(HbaseRequest hbaseRequest) {
    return hbaseRequest.getOperation();
  }

  @Nullable
  @Override
  public Long getDbOperationBatchSize(HbaseRequest hbaseRequest) {
    return hbaseRequest.getOperationBatchSize();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      HbaseRequest request, @Nullable Void unused) {
    if (request.getHost() == null || request.getPort() == null) {
      return null;
    }
    return InetSocketAddress.createUnresolved(request.getHost(), request.getPort());
  }

  @Nullable
  @Override
  public String getServerAddress(HbaseRequest request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(HbaseRequest request) {
    return request.getPort();
  }
}
