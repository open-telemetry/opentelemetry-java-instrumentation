/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

// Adding a package private interface to jdk proxy forces defining the proxy class in the package
// of the package private class. Usually proxy classes are defined in a package that we exclude from
// instrumentation. We use this class to force proxy into a different package so it would get
// instrumented.
interface TestInterface {}
