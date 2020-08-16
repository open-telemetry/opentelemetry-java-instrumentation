/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.context.propagation;

import application.io.grpc.Context;
import application.io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApplicationHttpTextFormat implements HttpTextFormat {

  private static final Logger log = LoggerFactory.getLogger(ApplicationHttpTextFormat.class);

  private final io.opentelemetry.context.propagation.HttpTextFormat agentHttpTextFormat;
  private final ContextStore<Context, io.grpc.Context> contextStore;

  ApplicationHttpTextFormat(
      io.opentelemetry.context.propagation.HttpTextFormat agentHttpTextFormat,
      ContextStore<Context, io.grpc.Context> contextStore) {
    this.agentHttpTextFormat = agentHttpTextFormat;
    this.contextStore = contextStore;
  }

  @Override
  public List<String> fields() {
    return agentHttpTextFormat.fields();
  }

  @Override
  public <C> Context extract(
      Context applicationContext,
      C carrier,
      HttpTextFormat.Getter<C> applicationGetter) {
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return applicationContext;
    }
    io.grpc.Context agentUpdatedContext =
        agentHttpTextFormat.extract(agentContext, carrier, new AgentGetter<>(applicationGetter));
    if (agentUpdatedContext == agentContext) {
      return applicationContext;
    }
    contextStore.put(applicationContext, agentUpdatedContext);
    return applicationContext;
  }

  @Override
  public <C> void inject(
      Context applicationContext,
      C carrier,
      HttpTextFormat.Setter<C> applicationSetter) {
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return;
    }
    agentHttpTextFormat.inject(agentContext, carrier, new AgentSetter<>(applicationSetter));
  }

  private static class AgentGetter<C>
      implements io.opentelemetry.context.propagation.HttpTextFormat.Getter<C> {

    private final HttpTextFormat.Getter<C> applicationGetter;

    AgentGetter(HttpTextFormat.Getter<C> applicationGetter) {
      this.applicationGetter = applicationGetter;
    }

    @Override
    public String get(C carrier, String key) {
      return applicationGetter.get(carrier, key);
    }
  }

  private static class AgentSetter<C>
      implements io.opentelemetry.context.propagation.HttpTextFormat.Setter<C> {

    private final HttpTextFormat.Setter<C> applicationSetter;

    AgentSetter(Setter<C> applicationSetter) {
      this.applicationSetter = applicationSetter;
    }

    @Override
    public void set(C carrier, String key, String value) {
      applicationSetter.set(carrier, key, value);
    }
  }
}
