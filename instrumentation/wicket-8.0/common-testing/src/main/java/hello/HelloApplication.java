/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import java.util.function.Supplier;
import org.apache.wicket.DefaultExceptionMapper;
import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.IExceptionMapper;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.RequestCycle;

public class HelloApplication extends WebApplication {
  @Override
  public Class<? extends Page> getHomePage() {
    return HelloPage.class;
  }

  @Override
  public Supplier<IExceptionMapper> getExceptionMapperProvider() {
    return () ->
        new DefaultExceptionMapper() {
          private boolean retried;

          @Override
          public IRequestHandler map(Exception e) {
            IRequestHandler handler = super.map(e);
            if (!retried
                && RequestCycle.get()
                    .getRequest()
                    .getRequestParameters()
                    .getParameterValue("retry")
                    .toBoolean(false)) {
              retried = true;
              return new IRequestHandler() {
                @Override
                public void respond(IRequestCycle requestCycle) {
                  throw new IllegalStateException("error page rendering failed");
                }

                @Override
                public void detach(IRequestCycle requestCycle) {}
              };
            }
            return handler;
          }
        };
  }

  @Override
  protected void init() {
    super.init();

    mountPage("/exception", ExceptionPage.class);
    mountResource("/resource", new HelloResource());
  }

  @Override
  public RuntimeConfigurationType getConfigurationType() {
    return RuntimeConfigurationType.DEPLOYMENT;
  }
}
