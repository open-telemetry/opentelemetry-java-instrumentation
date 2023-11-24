/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;

public class MapConverter implements Converter<String, Map<String, String>> {

  public static final String KEY = "key";

  @Override
  public Map<String, String> convert(String source) {
    DefaultConfigProperties properties =
        DefaultConfigProperties.createFromMap(Collections.singletonMap(KEY, source));

    return properties.getMap(KEY);
  }
}
