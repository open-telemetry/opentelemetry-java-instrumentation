/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.data.annotation.Id;

public record Player(@Id Integer id, String name, Integer age) {
  public Player() {
    this(null, null, 0);
  }
}
