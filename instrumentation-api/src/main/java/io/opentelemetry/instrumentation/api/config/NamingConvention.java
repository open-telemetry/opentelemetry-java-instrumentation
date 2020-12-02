/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

// config property names are normalized to underscore separated lowercase words
enum NamingConvention {
  DOT {
    @Override
    public String normalize(String key) {
      // many instrumentation names have dashes ('-')
      return key.toLowerCase().replace('-', '.');
    }
  },
  ENV_VAR {
    @Override
    public String normalize(String key) {
      return key.toLowerCase().replace('_', '.');
    }
  };

  abstract String normalize(String key);
}
