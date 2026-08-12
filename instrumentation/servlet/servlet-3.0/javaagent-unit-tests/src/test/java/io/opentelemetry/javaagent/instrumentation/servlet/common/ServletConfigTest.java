/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServletConfigTest {

  @AfterEach
  void clearWarnings() throws Exception {
    Field field = ServletConfig.class.getDeclaredField("warnedDeprecatedProperties");
    field.setAccessible(true);
    ((Set<?>) field.get(null)).clear();
  }

  @Test
  void readsNewSelector() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("request_parameters/development").getScalarList("included", String.class))
        .thenReturn(asList("exact", "prefix-*"));
    when(config.get("request_parameters/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));

    ServletConfig servletConfig = new ServletConfig(config, false);

    IncludeExclude requestParameters = servletConfig.getRequestParameters();
    assertThat(requestParameters).isNotNull();
    assertThat(requestParameters.getIncluded()).containsExactly("exact", "prefix-*");
    assertThat(requestParameters.getExcluded()).containsExactly("*-secret");
  }

  @Test
  void newSelectorTakesPrecedenceOverDeprecatedConfig() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("request_parameters/development").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_request_parameters/development", String.class))
        .thenReturn(singletonList("deprecated"));

    ServletConfig servletConfig = new ServletConfig(config, false);

    assertThat(servletConfig.getRequestParameters().getIncluded()).containsExactly("new");
  }

  @Test
  void deprecatedConfigIsIncludeOnlyFallbackAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_request_parameters/development", String.class))
        .thenReturn(singletonList("deprecated"));
    Logger logger = Logger.getLogger(ServletConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      ServletConfig first = new ServletConfig(config, false);
      ServletConfig second = new ServletConfig(config, false);

      assertThat(first.getRequestParameters().getIncluded()).containsExactly("deprecated");
      assertThat(first.getRequestParameters().getExcluded()).isEmpty();
      assertThat(second.getRequestParameters()).isEqualTo(first.getRequestParameters());
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.servlet.experimental.capture-request-parameters setting"
                  + " and the equivalent declarative configuration property are deprecated and"
                  + " may be removed in the next minor release. Use"
                  + " otel.instrumentation.servlet.experimental.request-parameters.included or"
                  + " equivalent declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void emptyDeprecatedConfigCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_request_parameters/development", String.class))
        .thenReturn(emptyList());

    ServletConfig servletConfig = new ServletConfig(config, false);

    assertThat(servletConfig.getRequestParameters()).isNull();
  }

  @Test
  void absentOrEmptySelectorCapturesNothing() {
    DeclarativeConfigProperties absentConfig = mockConfig();
    DeclarativeConfigProperties emptyConfig = mockConfig();
    when(emptyConfig.get("request_parameters/development").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(emptyConfig.get("request_parameters/development").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    assertThat(new ServletConfig(absentConfig, false).getRequestParameters()).isNull();
    assertThat(new ServletConfig(emptyConfig, false).getRequestParameters()).isNull();
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("request_parameters/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(config.get("request_parameters/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(config.getScalarList("capture_request_parameters/development", String.class))
        .thenReturn(null);
    return config;
  }

  private static final class TestHandler extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
