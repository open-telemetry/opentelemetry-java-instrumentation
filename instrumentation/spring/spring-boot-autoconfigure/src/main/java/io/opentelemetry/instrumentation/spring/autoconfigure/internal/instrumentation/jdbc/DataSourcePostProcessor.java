package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

final class DataSourcePostProcessor implements BeanPostProcessor, Ordered {

  private static final Class<?> ROUTING_DATA_SOURCE_CLASS = getRoutingDataSourceClass();

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  DataSourcePostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  private static Class<?> getRoutingDataSourceClass() {
    try {
      return Class.forName("org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private static boolean isRoutingDatasource(Object bean) {
    return ROUTING_DATA_SOURCE_CLASS != null && ROUTING_DATA_SOURCE_CLASS.isInstance(bean);
  }

  @CanIgnoreReturnValue
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
  // Exclude scoped proxy beans to avoid double wrapping
  if (bean instanceof DataSource
      && !isRoutingDatasource(bean)
      && !ScopedProxyUtils.isScopedTarget(beanName)) {
    DataSource dataSource = (DataSource) bean;

    // Wrap the original DataSource with OpenTelemetry instrumentation
    DataSource wrapped = JdbcTelemetry.builder(openTelemetryProvider.getObject())
        .setStatementSanitizationEnabled(
            InstrumentationConfigUtil.isStatementSanitizationEnabled(
                configPropertiesProvider.getObject(),
                "otel.instrumentation.jdbc.statement-sanitizer.enabled"))
        .setCaptureQueryParameters(
            configPropertiesProvider
                .getObject()
                .getBoolean(
                    "otel.instrumentation.jdbc.experimental.capture-query-parameters", false))
        .setTransactionInstrumenterEnabled(
            configPropertiesProvider
                .getObject()
                .getBoolean("otel.instrumentation.jdbc.experimental.transaction.enabled", false))
        .build()
        .wrap(dataSource);

    /**
     * Spring Boot's configuration binding and rebinding mechanisms (such as those triggered by
     * Nacos configuration refresh) may attempt to reconstruct beans using their concrete class
     * constructors. If a custom DataSource implementation (such as OpenTelemetryDataSource)
     * is returned directly, Spring may not find a suitable constructor during rebinding, resulting
     * in errors like "ExistingValue must be an instance of com.zaxxer.hikari.HikariDataSource".
     *
     * To prevent this, we create a JDK dynamic proxy implementing only the DataSource interface.
     * The proxy delegates all method calls to the wrapped (instrumented) DataSource.
     * This approach "hides" the actual implementation class and ensures that Spring interacts only
     * with the DataSource interface, avoiding issues related to constructor resolution or type casting
     * during bean rebinding.
     */
    ProxyFactory proxyFactory = new ProxyFactory(DataSource.class);
    // Set the original bean as the target (important for AOP and bean lifecycle)
    proxyFactory.setTarget(bean);
    // Delegate all method calls to the wrapped, instrumented DataSource
    proxyFactory.addAdvice(
        new MethodInterceptor() {
          @Nullable
          @Override
          public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
            return AopUtils.invokeJoinpointUsingReflection(
                wrapped, invocation.getMethod(), invocation.getArguments());
          }
        });

    // Return the proxy instead of the instrumented DataSource instance
    // This ensures proper interaction with Spring's bean lifecycle and rebinding
    return proxyFactory.getProxy();
  }
  return bean;
}

  // To be one of the first bean post-processors to be executed
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 20;
  }
}
