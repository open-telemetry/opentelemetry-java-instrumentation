/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import java.util.List;

public interface ResultsPersister {
  void write(List<AppPerfResults> results);
}
