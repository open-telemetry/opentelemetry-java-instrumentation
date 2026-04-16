/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.message;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;

// until message header normalization is dropped in 3.0
public class MessageHeaderUtil {

  public static AttributeKey<List<String>> headerAttributeKey(String headerName) {
    String key;
    if (v3Preview()) {
      key = "messaging.header." + headerName;
    } else {
      key = "messaging.header." + headerName.replace('-', '_');
    }
    return AttributeKey.stringArrayKey(key);
  }

  private MessageHeaderUtil() {}
}
