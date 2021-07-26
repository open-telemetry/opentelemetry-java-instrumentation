/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import io.opentelemetry.agents.Agent;
import java.util.Map;

public interface ResultsPersister {
  void write(Map<Agent, AppPerfResults> results);
}
