/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;

/**
 * The MapConverter class is used to convert a String to a Map. The String is expected to be in the
 * format of a comma separated list of key=value pairs, e.g. key1=value1,key2=value2.
 *
 * <p>This is the expected format for the <code>OTEL_RESOURCE_ATTRIBUTES</code> and <code>
 * OTEL_EXPORTER_OTLP_HEADERS</code> environment variables.
 *
 * <p>We need this converter, even if the SDK is disabled, because the properties are parsed before
 * the SDK is disabled.
 *
 * <p>We also need this converter if the OpenTelemetry bean is user supplied, because the
 * environment variables may still contain a value that needs to be converted, even if the SDK is
 * disabled (and the value thus ignored).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class OtelMapConverter implements Converter<String, Map<String, String>> {

  public static final String KEY = "key";

  @Override
  public Map<String, String> convert(String source) {
    DefaultConfigProperties properties =
        DefaultConfigProperties.createFromMap(Collections.singletonMap(KEY, source));

    return properties.getMap(KEY);
  }
}
