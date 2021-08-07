/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet;

import org.openjdk.jmh.annotations.Fork;

@Fork(jvmArgsAppend = {"-Dotel.traces.sampler=traceidratio", "-Dotel.traces.sampler.arg=0.01"})
public class ServletWithOnePercentSamplingBenchmark extends ServletBenchmark {}
