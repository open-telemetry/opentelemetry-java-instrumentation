/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CapturedMessageHeadersUtil {

  static Map<String, AttributeKey<List<String>>> createLiteralAttributeKeys(
      Collection<String> headerNames) {
    Map<String, AttributeKey<List<String>>> result = new HashMap<>();
    for (String headerName : headerNames) {
      result.put(headerName, createKey(headerName));
    }
    return result;
  }

  static AttributeKey<List<String>> attributeKey(
      String headerName, Map<String, AttributeKey<List<String>>> literalAttributeKeys) {
    AttributeKey<List<String>> attributeKey = literalAttributeKeys.get(headerName);
    return attributeKey != null ? attributeKey : createKey(headerName);
  }

  private static AttributeKey<List<String>> createKey(String headerName) {
    if (!SemconvStability.v3Preview()) {
      headerName = headerName.replace('-', '_');
    }
    return AttributeKey.stringArrayKey("messaging.header." + headerName);
  }

  private CapturedMessageHeadersUtil() {}
}
