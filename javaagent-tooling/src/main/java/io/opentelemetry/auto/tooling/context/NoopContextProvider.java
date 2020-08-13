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

package io.opentelemetry.auto.tooling.context;

import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;

public class NoopContextProvider implements InstrumentationContextProvider {

  public static final NoopContextProvider INSTANCE = new NoopContextProvider();

  private NoopContextProvider() {}

  @Override
  public Extendable instrumentationTransformer(final Extendable builder) {
    return builder;
  }

  @Override
  public Extendable additionalInstrumentation(final Extendable builder) {
    return builder;
  }
}
