/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebFluxLoadReportTest {
  @Test
  void readsOnlyWorkloadRequests() {
    assertThat(WebFluxLoadReport.read(summary(72_000, 0, 0, ""), 72_000, "test"))
        .isEqualTo(new WebFluxLoadReport.Measurement(72_000, 0, 0, 2.5, 1.25));
  }

  @Test
  void rejectsDroppedLoad() {
    assertThatThrownBy(() ->
            WebFluxLoadReport.read(
                summary(71_999, 0, 0, ", \"dropped_iterations\": {\"values\": {\"count\": 1}}"),
                72_000,
                "test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContainingAll("71999 of 72000 completed", "1 drops");
  }

  @Test
  void rejectsFailedRequestsAndIncorrectResponses() {
    assertThatThrownBy(() -> WebFluxLoadReport.read(summary(72_000, 1, 2, ""), 72_000, "test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContainingAll("1 HTTP errors", "2 failed checks");
  }

  private static String summary(int completed, int errors, int failedChecks, String extra) {
    return """
    {"metrics": {
      "http_reqs": {"values": {"count": 72002}},
      "http_req_duration": {"values": {"p(95)": 100}},
      "http_reqs{phase:workload}": {"values": {"count": %d}},
      "http_req_duration{phase:workload}": {"values": {"p(95)": 2.5}},
      "http_req_failed{phase:workload}": {"values": {"passes": %d}},
      "checks": {"values": {"fails": %d}},
      "app_cpu_cores": {"values": {"value": 1.25}}%s
    }}
    """
        .formatted(completed, errors, failedChecks, extra);
  }
}
