/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachecamel;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import org.apache.camel.CamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

public class CamelTracingService extends ServiceSupport
    implements RoutePolicyFactory, StaticService {

  private final CamelContext camelContext;
  private final CamelEventNotifier eventNotifier = new CamelEventNotifier();
  private final CamelRoutePolicy routePolicy = new CamelRoutePolicy();

  public CamelTracingService(CamelContext camelContext) {
    ObjectHelper.notNull(camelContext, "CamelContext", this);
    this.camelContext = camelContext;
  }

  @Override
  protected void doStart() throws Exception {
    camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
    if (!camelContext.getRoutePolicyFactories().contains(this)) {
      camelContext.addRoutePolicyFactory(this);
    }

    ServiceHelper.startServices(eventNotifier);
  }

  @Override
  protected void doStop() throws Exception {
    // stop event notifier
    camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
    ServiceHelper.stopService(eventNotifier);

    // remove route policy
    camelContext.getRoutePolicyFactories().remove(this);
  }

  @Override
  public RoutePolicy createRoutePolicy(
      CamelContext camelContext, String routeId, RouteDefinition route) {
    return routePolicy;
  }
}
