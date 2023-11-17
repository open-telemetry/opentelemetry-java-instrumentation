/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import groovy.transform.CompileStatic
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

@CompileStatic
class Aws2ClientDynamodbTest extends AbstractAws2ClientCoreTest implements LibraryTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(
            AwsSdkTelemetry.builder(getOpenTelemetry())
                .setCaptureExperimentalSpanAttributes(true)
                .setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled())
                .build()
                .newExecutionInterceptor())
  }
}
