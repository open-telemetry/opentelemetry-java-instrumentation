/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import org.apache.shenyu.common.dto.MetaData;

public final class ApacheShenYuSingletons {

  private ApacheShenYuSingletons() {}

  public static HttpServerRouteGetter<MetaData> httpRouteGetter() {
    return (context, metaData) -> metaData.getPath();
  }
}
