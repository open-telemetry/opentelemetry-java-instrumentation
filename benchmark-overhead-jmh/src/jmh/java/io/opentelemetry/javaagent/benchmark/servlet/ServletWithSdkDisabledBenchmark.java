/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import org.openjdk.jmh.annotations.Fork;

@Fork(jvmArgsAppend = "-Dotel.experimental.sdk.enabled=false")
public class ServletWithSdkDisabledBenchmark extends ServletBenchmark {}
