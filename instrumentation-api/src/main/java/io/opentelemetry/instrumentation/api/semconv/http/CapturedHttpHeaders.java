/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** The exact, case-insensitive HTTP header names captured by an attributes extractor. */
final class CapturedHttpHeaders {

  private final List<String> exactNames;
  private final Map<String, AttributeKey<List<String>>> exactAttributeKeys;

  static CapturedHttpHeaders create(String type, @Nullable Collection<String> headerNames) {
    return new CapturedHttpHeaders(type, headerNames);
  }

  private CapturedHttpHeaders(String type, @Nullable Collection<String> headerNames) {
    Set<String> names = new LinkedHashSet<>();
    if (headerNames != null) {
      for (String name : headerNames) {
        names.add(name.toLowerCase(Locale.ROOT));
      }
    }
    this.exactNames = unmodifiableList(new ArrayList<>(names));
    this.exactAttributeKeys = createAttributeKeys(type, exactNames);
  }

  boolean isEmpty() {
    return exactNames.isEmpty();
  }

  List<String> exactNames() {
    return exactNames;
  }

  AttributeKey<List<String>> attributeKey(String lowercaseName) {
    return requireNonNull(exactAttributeKeys.get(lowercaseName));
  }

  private static Map<String, AttributeKey<List<String>>> createAttributeKeys(
      String type, Collection<String> lowercaseNames) {
    if (lowercaseNames.isEmpty()) {
      return emptyMap();
    }
    Map<String, AttributeKey<List<String>>> attributeKeys = new HashMap<>();
    for (String name : lowercaseNames) {
      attributeKeys.put(name, createAttributeKey(type, name));
    }
    return attributeKeys;
  }

  private static AttributeKey<List<String>> createAttributeKey(String type, String lowercaseName) {
    return AttributeKey.stringArrayKey("http." + type + ".header." + lowercaseName);
  }
}
