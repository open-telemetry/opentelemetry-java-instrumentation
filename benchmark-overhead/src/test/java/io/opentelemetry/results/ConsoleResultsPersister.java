/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import java.util.List;

class ConsoleResultsPersister implements ResultsPersister {

  @Override
  public void write(List<AppPerfResults> results) {
    PrintStreamPersister delegate = new PrintStreamPersister(System.out);
    delegate.write(results);
  }
}
