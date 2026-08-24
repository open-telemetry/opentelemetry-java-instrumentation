/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

public class ElasticsearchTransportAttributesGetter
    implements DbClientAttributesGetter<ElasticTransportRequest, ActionResponse> {

  @Override
  public String getDbSystemName(ElasticTransportRequest request) {
    return DbSystemNameIncubatingValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(ElasticTransportRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(ElasticTransportRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(ElasticTransportRequest request) {
    return request.getAction().getClass().getSimpleName();
  }

  @Override
  @Nullable
  public String getServerAddress(ElasticTransportRequest request) {
    // old semantic conventions record only the node that answered, as the network peer
    return emitStableDatabaseSemconv() ? request.getServerAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(ElasticTransportRequest request) {
    // a target that names several addresses already carries the port of each of them
    return emitStableDatabaseSemconv() ? request.getServerPort() : null;
  }
}
