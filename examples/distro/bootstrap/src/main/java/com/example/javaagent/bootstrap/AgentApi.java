/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.bootstrap;

/**
 * Classes in bootstrap class loader are visible for both the agent classes in agent class loader
 * and helper classes that are injected into the class loader that contains the instrumented class.
 */
public final class AgentApi {

  public static void doSomething(int number) {}

  private AgentApi() {}
}
