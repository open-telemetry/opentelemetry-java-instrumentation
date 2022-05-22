/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.util.Map;

/**
 * This listener can be registered in an agent extension by setting the
 * HelperInjector.helperInjectorListener field. It is used to process additional classes created by
 * the agent.
 */
public interface HelperInjectorListener {

  void onInjection(Map<String, byte[]> classnameToBytes);
}
