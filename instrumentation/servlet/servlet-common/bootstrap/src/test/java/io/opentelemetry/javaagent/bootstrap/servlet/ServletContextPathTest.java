/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class ServletContextPathTest {

  @Test
  void shouldAddSlashBetweenContextAndSpanName() {
    Context contextWithEmptyPath = ServletContextPath.init(Context.root(), p -> p, "");
    Context contextWithPath = ServletContextPath.init(Context.root(), p -> p, "/context");

    assertThat(ServletContextPath.prepend(contextWithEmptyPath, "spanName")).isEqualTo("spanName");
    assertThat(ServletContextPath.prepend(contextWithPath, "spanName"))
        .isEqualTo("/context/spanName");
  }

  @Test
  void shouldNotResultInDuplicateSlash() {
    Context contextWithEmptyPath = ServletContextPath.init(Context.root(), p -> p, "");
    Context contextWithPath = ServletContextPath.init(Context.root(), p -> p, "/context");

    assertThat(ServletContextPath.prepend(contextWithEmptyPath, "/spanName"))
        .isEqualTo("/spanName");
    assertThat(ServletContextPath.prepend(contextWithPath, "/spanName"))
        .isEqualTo("/context/spanName");
  }

  @Test
  void shouldIgnoreEmptySpanName() {
    Context contextWithEmptyPath = ServletContextPath.init(Context.root(), p -> p, "");
    Context contextWithPath = ServletContextPath.init(Context.root(), p -> p, "/context");

    assertThat(ServletContextPath.prepend(contextWithEmptyPath, "")).isEqualTo("");
    assertThat(ServletContextPath.prepend(contextWithPath, "")).isEqualTo("/context");

    assertThat(ServletContextPath.prepend(contextWithEmptyPath, null)).isEqualTo(null);
    assertThat(ServletContextPath.prepend(contextWithPath, null)).isEqualTo("/context");
  }
}
