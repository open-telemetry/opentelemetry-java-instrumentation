/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
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

  public HbaseInstrumentationModule() {
    super("hbase-client", "hbase-client-1.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.hadoop.hbase.ipc.AbstractRpcClient")
        .and(not(hasClassesNamed("org.apache.hadoop.hbase.ipc.RpcConnection")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RetryingCallableInstrumentation(), new AbstractRpcClientInstrumentation());
  }
}
