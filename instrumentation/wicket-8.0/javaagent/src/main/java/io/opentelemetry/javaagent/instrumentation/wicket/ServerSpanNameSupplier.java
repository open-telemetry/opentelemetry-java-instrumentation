/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;
import org.apache.wicket.core.request.handler.IPageClassRequestHandler;
import org.apache.wicket.request.cycle.RequestCycle;

public class ServerSpanNameSupplier implements Supplier<String> {

  private final Context context;
  private final IPageClassRequestHandler handler;

  public ServerSpanNameSupplier(Context context, IPageClassRequestHandler handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public String get() {
    // using class name as page name
    String pageName = ((IPageClassRequestHandler) handler).getPageClass().getName();
    // wicket filter mapping without wildcard, if wicket filter is mapped to /*
    // this will be an empty string
    String filterPath = RequestCycle.get().getRequest().getFilterPath();
    return ServletContextPath.prepend(context, filterPath + "/" + pageName);
  }
}
