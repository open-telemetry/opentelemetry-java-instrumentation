/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class HbaseInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  private static final String CALL_UTIL = "org.apache.hadoop.hbase.ipc.OpenTelemetryCallUtil";

  public HbaseInstrumentationModule() {
    super("hbase-client", "hbase-client-1.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.4.0
    return hasClassesNamed("org.apache.hadoop.hbase.ipc.RpcConnection")
        // added in 2.0.0
        .and(not(hasClassesNamed("org.apache.hadoop.hbase.client.AsyncAdmin")));
  }

  @Override
  public boolean isHelperClass(String className) {
    return CALL_UTIL.equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList(CALL_UTIL);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RpcConnectionInstrumentation(),
        new RetryingCallableInstrumentation(),
        new IpcCallInstrumentation(),
        new AbstractRpcClientInstrumentation());
  }
}
