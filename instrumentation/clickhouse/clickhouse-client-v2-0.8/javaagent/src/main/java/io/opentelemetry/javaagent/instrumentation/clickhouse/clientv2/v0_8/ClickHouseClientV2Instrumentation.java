/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8.ClickHouseClientV2Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.QuerySettings;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPort;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseScope;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ClickHouseClientV2Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("query"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, isSubTypeOf(Map.class)))
            .and(takesArgument(2, named("com.clickhouse.client.api.query.QuerySettings"))),
        this.getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ClickHouseScope onEnter(
        @Advice.This Client client,
        @Advice.Argument(0) String sqlQuery,
        @Advice.Argument(1) Map<String, Object> queryParams,
        @Advice.Argument(2) QuerySettings querySettings) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.getAndIncrement() > 0 || sqlQuery == null) {
        return null;
      }

      // https://clickhouse.com/docs/integrations/language-clients/java/client#client-configuration
      // Currently, clientv2 supports only one endpoint. Since the endpoint is not going to change
      // we'll cache it in a virtual field.
      AddressAndPort addressAndPort = ClickHouseClientV2Singletons.getAddressAndPort(client);
      if (addressAndPort == null) {
        String endpoint = client.getEndpoints().stream().findFirst().orElse(null);
        addressAndPort = ClickHouseClientV2Singletons.setAddressAndPort(client, endpoint);
      }

      String database = client.getConfiguration().get("database");
      Context parentContext = currentContext();
      ClickHouseDbRequest request =
          ClickHouseDbRequest.create(
              addressAndPort.getAddress(), addressAndPort.getPort(), database, sqlQuery);

      return ClickHouseScope.start(instrumenter(), parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }
}
