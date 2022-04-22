/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.util.Map;

/**
 * This hook can be registered in an agent extension by setting the
 * HelperInjector.staticInstrumenterHook field.
 * It is used to process additional classes created by the agent.
 */
public interface StaticInstrumenterHook {

  void injectClasses(Map<String, byte[]> classnameToBytes);
}
