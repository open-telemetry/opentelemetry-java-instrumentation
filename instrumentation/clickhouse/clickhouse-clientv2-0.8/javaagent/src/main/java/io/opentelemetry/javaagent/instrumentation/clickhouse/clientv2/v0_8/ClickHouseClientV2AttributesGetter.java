/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import com.clickhouse.client.api.ServerException;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import javax.annotation.Nullable;

public final class ClickHouseClientV2AttributesGetter extends ClickHouseAttributesGetter
    implements DbClientAttributesGetter<ClickHouseDbRequest, Void> {

  public static ClickHouseClientV2AttributesGetter create() {
    return new ClickHouseClientV2AttributesGetter();
  }

  @Nullable
  @Override
  public String getResponseStatus(@Nullable Void response, @Nullable Throwable error) {
    if (error instanceof ServerException) {
      return Integer.toString(((ServerException) error).getCode());
    }
    return null;
  }
}
