/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionResponse;

public class ElasticsearchTransportAttributesGetter
    implements DbClientAttributesGetter<ElasticTransportRequest, ActionResponse>,
        AttributesExtractor<ElasticTransportRequest, ActionResponse> {

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
  public String getErrorType(
      ElasticTransportRequest request,
      @Nullable ActionResponse response,
      @Nullable Throwable error) {
    if (!(error instanceof ElasticsearchException)) {
      return null;
    }
    // Elasticsearch wraps the failure that actually occurred, such as IndexNotFoundException, in a
    // generic wrapper such as RemoteTransportException. unwrapCause() peels off the classes that
    // Elasticsearch itself marks as wrappers, leaving the exception that identifies the failure.
    Throwable cause = ((ElasticsearchException) error).unwrapCause();
    // Returning null lets DbClientAttributesExtractor fall back to the exception class name.
    return cause != error ? cause.getClass().getName() : null;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ElasticTransportRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ElasticTransportRequest request,
      @Nullable ActionResponse response,
      @Nullable Throwable error) {}
}
