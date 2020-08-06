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

package io.opentelemetry.auto.util.test

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.type.TypeDefinition
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.Transformer
import spock.lang.Specification

import static net.bytebuddy.description.modifier.FieldManifestation.VOLATILE
import static net.bytebuddy.description.modifier.Ownership.STATIC
import static net.bytebuddy.description.modifier.Visibility.PUBLIC
import static net.bytebuddy.matcher.ElementMatchers.named
import static net.bytebuddy.matcher.ElementMatchers.none

abstract class AgentSpecification extends Specification {
  private static final String CONFIG = "io.opentelemetry.instrumentation.api.config.Config"

  static {
    addByteBuddyRawSetting()
    makeConfigInstanceModifiable()
  }

  // Keep track of config instance already made modifiable
  private static isConfigInstanceModifiable = false

  // this is a copy of same method from AgentInstaller
  // it's needed here, because the test harness loads bytebuddy early
  // and then it's too late to set the property in AgentInstaller
  static void addByteBuddyRawSetting() {
    String savedPropertyValue = System.getProperty(TypeDefinition.RAW_TYPES_PROPERTY)
    try {
      System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, "true")
      boolean rawTypes = TypeDescription.AbstractBase.RAW_TYPES
      if (!rawTypes) {
        System.err.println("Too late to enable $TypeDefinition.RAW_TYPES_PROPERTY")
      }
    } finally {
      if (savedPropertyValue == null) {
        System.clearProperty(TypeDefinition.RAW_TYPES_PROPERTY)
      } else {
        System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, savedPropertyValue)
      }
    }
  }

  static void makeConfigInstanceModifiable() {
    if (isConfigInstanceModifiable) {
      return
    }

    def instrumentation = ByteBuddyAgent.install()
    new AgentBuilder.Default()
      .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
      .with(AgentBuilder.RedefinitionStrategy.Listener.ErrorEscalating.FAIL_FAST)
    // Config is injected into the bootstrap, so we need to provide a locator.
      .with(
        new AgentBuilder.LocationStrategy.Simple(
          ClassFileLocator.ForClassLoader.ofSystemLoader()))
      .ignore(none()) // Allow transforming bootstrap classes
      .type(named(CONFIG))
      .transform { builder, typeDescription, classLoader, module ->
        builder
          .field(named("INSTANCE"))
          .transform(Transformer.ForField.withModifiers(PUBLIC, STATIC, VOLATILE))
      }
      .installOn(instrumentation)
    isConfigInstanceModifiable = true
  }
}
