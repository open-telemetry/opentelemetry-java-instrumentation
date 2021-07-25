/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark;

import org.openjdk.jmh.annotations.Fork;

@Fork(
    jvmArgsPrepend = {
      "-javaagent:build/agent/opentelemetry-javaagent-all.jar",
      "-Dotel.traces.exporter=none", // note: without an exporter, toSpanData() won't even be called
      "-Dotel.metrics.exporter=none",
      "-Dotel.traces.sampler=traceidratio",
      "-Dotel.traces.sampler.arg=0.01"
    })
public class ServletWithAgentWithOnePercentSamplingBenchmark extends ServletBenchmark {}
