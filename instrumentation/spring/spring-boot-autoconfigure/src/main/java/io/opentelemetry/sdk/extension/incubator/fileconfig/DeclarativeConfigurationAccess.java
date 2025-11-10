/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DeclarativeConfigurationAccess {
  private DeclarativeConfigurationAccess() {}

  public static ObjectMapper getObjectMapper() {
    return DeclarativeConfiguration.MAPPER;
  }
}
