/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import java.util.Locale;

// config property names are normalized to dot separated lowercase words
enum NamingConvention {
  DOT {
    @Override
    public String normalize(String key) {
      // many instrumentation names have dashes ('-')
      return key.toLowerCase(Locale.ROOT).replace('-', '.');
    }
  },
  ENV_VAR {
    @Override
    public String normalize(String key) {
      return key.toLowerCase(Locale.ROOT).replace('_', '.');
    }
  };

  abstract String normalize(String key);
}
