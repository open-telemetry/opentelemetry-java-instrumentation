/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.v0_5;

import com.clickhouse.client.ClickHouseException;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import javax.annotation.Nullable;

public final class ClickHouseClientV1AttributesGetter extends ClickHouseAttributesGetter
    implements DbClientAttributesGetter<ClickHouseDbRequest, Void> {

  public static ClickHouseClientV1AttributesGetter create() {
    return new ClickHouseClientV1AttributesGetter();
  }

  @Nullable
  @Override
  public String getResponseStatus(@Nullable Void response, @Nullable Throwable error) {
    if (error instanceof ClickHouseException) {
      return Integer.toString(((ClickHouseException) error).getErrorCode());
    }
    return null;
  }
}
