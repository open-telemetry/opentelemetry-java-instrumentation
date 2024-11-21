/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class DynamoAttributesExtractor implements AttributesExtractor<Request<?>, Response<?>> {

  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  private static final AttributeKey<List<String>> AWS_TABLE_NAMES =
      AttributeKey.stringArrayKey("aws.dynamodb.table_names");

  private static final String DYNAMODB = "dynamodb";

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {
    AttributesExtractorUtil.internalSet(attributes, DB_SYSTEM, DYNAMODB);
    String tableName = RequestAccess.getTableName(request.getOriginalRequest());
    AttributesExtractorUtil.internalSet(
        attributes, AWS_TABLE_NAMES, Collections.singletonList(tableName));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {}
}