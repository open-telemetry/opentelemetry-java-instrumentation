/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.javaagent.instrumentation.api.db.normalizer.ParseException;
import io.opentelemetry.javaagent.instrumentation.api.db.normalizer.SqlNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeodeQueryNormalizer {
  private static final Logger log = LoggerFactory.getLogger(GeodeQueryNormalizer.class);

  public static String normalize(String query) {
    if (query == null) {
      return null;
    }
    try {
      return SqlNormalizer.normalize(query);
    } catch (ParseException e) {
      log.debug("Could not normalize Geode query", e);
      return null;
    }
  }

  private GeodeQueryNormalizer() {}
}
