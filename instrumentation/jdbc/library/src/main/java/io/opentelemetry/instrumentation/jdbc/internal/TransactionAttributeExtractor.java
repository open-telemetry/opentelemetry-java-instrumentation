/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

enum TransactionAttributeExtractor implements AttributesExtractor<DbRequest, Void> {
  INSTANCE;

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_OPERATION_NAME =
      AttributeKey.stringKey("db.operation.name");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, DbRequest request) {
    if (SemconvStability.emitOldDatabaseSemconv()) {
      internalSet(attributes, DB_OPERATION, request.getOperation());
    }
    if (SemconvStability.emitStableDatabaseSemconv()) {
      internalSet(attributes, DB_OPERATION_NAME, request.getOperation());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      DbRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
