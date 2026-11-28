/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import static io.opentelemetry.containers.K6Container.WEBFLUX_RATE;
import static io.opentelemetry.containers.K6Container.WEBFLUX_SECONDS;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;
import io.opentelemetry.util.NamingConvention;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class WebFluxLoadReport {

  public static void write(Path path, TestConfig config, NamingConvention names)
      throws IOException {
    StringBuilder summary =
        new StringBuilder(
            config.getName().equals("standard")
                ? "### WebFlux standard comparison\n\n"
                : "### WebFlux Reactor experiment\n\n");
    summary.append(
        String.format(
            Locale.ROOT,
            "%,d requests/s offered for %d seconds after %d seconds of warmup at the same rate. "
                + "CPU is average app JVM cores, including user and system time.%n%n",
            WEBFLUX_RATE,
            WEBFLUX_SECONDS,
            config.getWarmupSeconds()));
    summary.append(
        "| Variant | Agent Reactor | App auto propagation | Completed | HTTP errors | Dropped | Request p95 (ms) | Avg. app CPU (cores) |\n");
    summary.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |\n");
    for (Agent agent : config.getAgents()) {
      var result =
          read(
              Files.readString(Path.of(names.k6Results(agent))),
              WEBFLUX_RATE * WEBFLUX_SECONDS,
              agent.getName());
      var mode = config.webFluxMode(agent);
      summary.append(
          String.format(
              Locale.ROOT,
              "| %s | %s | %s | %,d | %d | %d | %.3f | %.3f |%n",
              agent.getName(),
              agent == Agent.NONE ? "n/a" : onOff(mode.reactor()),
              onOff(mode.automatic()),
              result.completed(),
              result.errors(),
              result.dropped(),
              result.p95(),
              result.cpuCores()));
    }
    Files.writeString(path, summary);
  }

  static Measurement read(String json, long expected, String variant) {
    var metrics = JsonPath.parse(json);
    Number completed = metrics.read("$.metrics['http_reqs{phase:workload}'].values.count");
    Number errors = metrics.read("$.metrics['http_req_failed{phase:workload}'].values.passes");
    Number failedChecks = metrics.read("$.metrics.checks.values.fails");
    Number p95 = metrics.read("$.metrics['http_req_duration{phase:workload}'].values['p(95)']");
    Number cpu = metrics.read("$.metrics.app_cpu_cores.values.value");
    Number dropped;
    try {
      dropped = metrics.read("$.metrics.dropped_iterations.values.count");
    } catch (PathNotFoundException e) {
      // k6 does not emit a dropped_iterations metric if it never drops an iteration.
      dropped = 0;
    }
    if (completed.longValue() != expected
        || errors.longValue() != 0
        || failedChecks.longValue() != 0
        || dropped.longValue() != 0
        || !Double.isFinite(p95.doubleValue())
        || p95.doubleValue() < 0
        || !Double.isFinite(cpu.doubleValue())
        || cpu.doubleValue() <= 0) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "%s: %d of %d completed, %d HTTP errors, %d failed checks, %d drops, %s ms p95, %s JVM cores",
              variant,
              completed.longValue(),
              expected,
              errors.longValue(),
              failedChecks.longValue(),
              dropped.longValue(),
              p95,
              cpu));
    }
    return new Measurement(
        completed.longValue(),
        errors.longValue(),
        dropped.longValue(),
        p95.doubleValue(),
        cpu.doubleValue());
  }

  private static String onOff(boolean enabled) {
    return enabled ? "on" : "off";
  }

  record Measurement(long completed, long errors, long dropped, double p95, double cpuCores) {}

  private WebFluxLoadReport() {}
}
