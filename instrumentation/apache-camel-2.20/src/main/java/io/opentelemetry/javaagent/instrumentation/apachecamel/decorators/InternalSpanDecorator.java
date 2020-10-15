/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class InternalSpanDecorator extends BaseSpanDecorator {

  @Override
  public String getOperationName(Exchange exchange, Endpoint endpoint) {
    // Internal communications use descriptive names, so suitable
    // as an operation name, but need to strip the scheme and any options
    return stripSchemeAndOptions(endpoint);
  }
}
