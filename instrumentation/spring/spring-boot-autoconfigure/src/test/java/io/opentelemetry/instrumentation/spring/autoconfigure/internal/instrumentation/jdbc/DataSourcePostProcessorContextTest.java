package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataSourcePostProcessorTest {

  @Test
  void shouldWrapDataSourceWithProxy() throws Exception {
    // Set up mocks
    DataSource original = mock(DataSource.class);
    Connection mockConn = mock(Connection.class);
    when(original.getConnection()).thenReturn(mockConn);

    // Create mocks for OpenTelemetry and ConfigProperties
    OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
    ConfigProperties configProperties = mock(ConfigProperties.class);
    when(configProperties.getBoolean(anyString(), anyBoolean())).thenReturn(false);

    // Mock JdbcTelemetry builder chain to return a mock DataSource as 'wrapped'
    JdbcTelemetry.Builder builder = mock(JdbcTelemetry.Builder.class, RETURNS_SELF);
    JdbcTelemetry telemetry = mock(JdbcTelemetry.class);
    DataSource wrapped = mock(DataSource.class);
    when(wrapped.getConnection()).thenReturn(mockConn);
    when(telemetry.wrap(any(DataSource.class))).thenReturn(wrapped);
    when(builder.build()).thenReturn(telemetry);

    // Mock the static builder method (requires mockito-inline or PowerMockito)
    try (var mocked = Mockito.mockStatic(JdbcTelemetry.class)) {
      mocked.when(() -> JdbcTelemetry.builder(any())).thenReturn(builder);

      ObjectProvider<OpenTelemetry> openTelemetryProvider = () -> openTelemetry;
      ObjectProvider<ConfigProperties> configPropertiesProvider = () -> configProperties;

      DataSourcePostProcessor postProcessor = new DataSourcePostProcessor(
          openTelemetryProvider, configPropertiesProvider
      );

      // Act
      Object processed = postProcessor.postProcessAfterInitialization(original, "myDataSource");

      // Assert
      assertThat(processed).isInstanceOf(DataSource.class);
      assertThat(processed).isNotSameAs(original);

      // The proxy should delegate calls to the wrapped DataSource
      DataSource proxied = (DataSource) processed;
      Connection proxiedConnection = proxied.getConnection();
      assertThat(proxiedConnection).isEqualTo(mockConn);

      // The original DataSource should NOT be called directly by the proxy
      verify(original, never()).getConnection();
      // The wrapped DataSource should be called
      verify(wrapped, times(1)).getConnection();
    }
  }
}