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

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.common.AttributeValue.stringAttributeValue;

import com.google.auto.service.AutoService;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.sdk.resources.ResourceProvider;

@AutoService(ResourceProvider.class)
public class AutoVersionResourceProvider extends ResourceProvider {
  @Override
  protected Attributes getAttributes() {
    return Attributes.of("telemetry.auto.version", stringAttributeValue(AgentVersion.VERSION));
  }
}
