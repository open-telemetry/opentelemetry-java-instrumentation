/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import org.openjdk.jmh.annotations.Fork;

@Fork(jvmArgsAppend = "-Dotel.javaagent.enabled=false")
public class ServletWithAgentDisabledBenchmark extends ServletBenchmark {}
