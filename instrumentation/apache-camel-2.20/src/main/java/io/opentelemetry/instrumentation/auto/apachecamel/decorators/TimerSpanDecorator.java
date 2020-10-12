/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class TimerSpanDecorator extends BaseSpanDecorator {

  @Override
  public String getOperationName(Exchange exchange, Endpoint endpoint) {
    Object name = exchange.getProperty(Exchange.TIMER_NAME);
    if (name instanceof String) {
      return (String) name;
    }

    return super.getOperationName(exchange, endpoint);
  }
}
