/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The HTTP headers captured by an attributes extractor, resolved either from an {@link
 * IncludeExclude} selector or, for legacy callers, from a list of exact header names.
 *
 * <p>HTTP header names are case-insensitive, so the selector patterns are lowercased here and every
 * header name is lowercased before it is matched.
 */
final class CapturedHttpHeaders {

  private final String type;
  @Nullable private final IncludeExclude selector;
  // the exact header names configured by legacy callers, which are matched literally
  @Nullable private final Set<String> exactOnlyNames;
  // the header names that the selector includes literally, which are looked up directly so that
  // getters which do not enumerate header names keep working
  private final List<String> exactNames;
  private final Map<String, AttributeKey<List<String>>> exactAttributeKeys;
  // whether the selector can match header names that are not listed in exactNames, which requires
  // enumerating the header names of each request or response
  private final boolean enumerateNames;

  static CapturedHttpHeaders create(String type, @Nullable IncludeExclude headers) {
    return new CapturedHttpHeaders(
        type, headers == null || headers.isEmpty() ? null : headers, null);
  }

  /**
   * Creates the headers captured by legacy callers, whose values are exact header names rather than
   * glob patterns.
   */
  static CapturedHttpHeaders createExact(String type, @Nullable Collection<String> headerNames) {
    if (headerNames == null || headerNames.isEmpty()) {
      return new CapturedHttpHeaders(type, null, null);
    }
    Set<String> exactOnlyNames = new LinkedHashSet<>();
    for (String headerName : headerNames) {
      exactOnlyNames.add(lowercase(headerName));
    }
    return new CapturedHttpHeaders(type, null, exactOnlyNames);
  }

  private CapturedHttpHeaders(
      String type, @Nullable IncludeExclude headers, @Nullable Set<String> exactOnlyNames) {
    this.type = type;
    this.selector = headers == null ? null : lowercase(headers);
    this.exactOnlyNames = exactOnlyNames;

    Set<String> names = new LinkedHashSet<>();
    boolean enumerate = false;
    if (exactOnlyNames != null) {
      names.addAll(exactOnlyNames);
    } else if (selector != null) {
      List<String> included = selector.getIncluded();
      // a selector without included patterns matches every header name that is not excluded
      enumerate = included.isEmpty();
      for (String pattern : included) {
        if (pattern.indexOf('*') != -1 || pattern.indexOf('?') != -1) {
          enumerate = true;
        } else if (matches(pattern)) {
          names.add(pattern);
        }
      }
    }
    this.exactNames = unmodifiableList(new ArrayList<>(names));
    this.exactAttributeKeys = createAttributeKeys(type, exactNames);
    this.enumerateNames = enumerate;
  }

  boolean isEmpty() {
    return selector == null && exactOnlyNames == null;
  }

  boolean enumerateNames() {
    return enumerateNames;
  }

  List<String> exactNames() {
    return exactNames;
  }

  /** Returns the lowercase names of the captured headers among {@code enumeratedNames}. */
  Collection<String> matchingNames(Collection<String> enumeratedNames) {
    Set<String> names = new LinkedHashSet<>(exactNames);
    for (String name : enumeratedNames) {
      String lowercased = lowercase(name);
      if (matches(lowercased)) {
        names.add(lowercased);
      }
    }
    return names;
  }

  AttributeKey<List<String>> attributeKey(String lowercaseName) {
    AttributeKey<List<String>> attributeKey = exactAttributeKeys.get(lowercaseName);
    return attributeKey != null ? attributeKey : createAttributeKey(type, lowercaseName);
  }

  private boolean matches(String lowercaseName) {
    if (exactOnlyNames != null) {
      return exactOnlyNames.contains(lowercaseName);
    }
    return selector != null && selector.matches(lowercaseName);
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

  private static String lowercase(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private static IncludeExclude lowercase(IncludeExclude headers) {
    return IncludeExclude.builder()
        .setIncluded(lowercase(headers.getIncluded()))
        .setExcluded(lowercase(headers.getExcluded()))
        .build();
  }

  private static List<String> lowercase(List<String> values) {
    List<String> lowercased = new ArrayList<>(values.size());
    for (String value : values) {
      lowercased.add(lowercase(value));
    }
    return lowercased;
  }
}
