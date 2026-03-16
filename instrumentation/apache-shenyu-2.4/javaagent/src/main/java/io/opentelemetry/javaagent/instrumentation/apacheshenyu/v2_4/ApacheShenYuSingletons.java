/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import org.apache.shenyu.common.dto.MetaData;

public final class ApacheShenYuSingletons {

  private static final HttpServerRouteGetter<MetaData> HTTP_ROUTE_GETTER =
      (context, metaData) -> metaData.getPath();

  private ApacheShenYuSingletons() {}

  public static HttpServerRouteGetter<MetaData> httpRouteGetter() {
    return HTTP_ROUTE_GETTER;
  }
}
