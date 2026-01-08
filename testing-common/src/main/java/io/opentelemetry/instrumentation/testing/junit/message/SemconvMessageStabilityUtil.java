/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.message;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;

// until old message semconv are dropped in 3.0
public class SemconvMessageStabilityUtil {

  public static AttributeKey<List<String>> headerAttributeKey(String headerName) {
    String key;
    if (SemconvStability.isEmitOldMessageSemconv()) {
      key = "messaging.header." + headerName.replace('-', '_');
    } else {
      key = "messaging.header." + headerName;
    }
    return AttributeKey.stringArrayKey(key);
  }

  private SemconvMessageStabilityUtil() {}
}
