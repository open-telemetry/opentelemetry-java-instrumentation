/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.message;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;

public class MessageHeaderUtil {

  public static AttributeKey<List<String>> headerAttributeKey(String headerName) {
    return AttributeKey.stringArrayKey("messaging.header." + headerName);
  }

  private MessageHeaderUtil() {}
}
