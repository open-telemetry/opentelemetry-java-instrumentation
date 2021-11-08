/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package v2_2

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2ClientTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.builder.SdkClientBuilder

class Aws2ClientTest extends AbstractAws2ClientTest implements LibraryTestTrait {
  @Override
  void configureSdkClient(SdkClientBuilder builder) {
  }
}
