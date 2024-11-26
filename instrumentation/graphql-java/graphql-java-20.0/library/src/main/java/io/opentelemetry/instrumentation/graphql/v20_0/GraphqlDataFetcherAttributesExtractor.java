/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class GraphqlDataFetcherAttributesExtractor
    implements AttributesExtractor<DataFetchingEnvironment, Void> {

  // NOTE: These are not part of the Semantic Convention and are subject to change
  private static final AttributeKey<String> GRAPHQL_FIELD_NAME =
      AttributeKey.stringKey("graphql.field.name");
  private static final AttributeKey<String> GRAPHQL_FIELD_PATH =
      AttributeKey.stringKey("graphql.field.path");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, DataFetchingEnvironment environment) {
    attributes
        .put(GRAPHQL_FIELD_NAME, environment.getExecutionStepInfo().getField().getName())
        .put(GRAPHQL_FIELD_PATH, environment.getExecutionStepInfo().getPath().toString());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      DataFetchingEnvironment environment,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
