/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class LibertyDispatcherInstrumentationModule extends InstrumentationModule {

  public LibertyDispatcherInstrumentationModule() {
    super("liberty", "liberty-dispatcher");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpDispatcherLinkInstrumentation());
  }

  public static class HttpDispatcherLinkInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      // https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
      return singletonMap(
          named("sendResponse")
              .and(takesArgument(0, named("com.ibm.wsspi.http.channel.values.StatusCodes")))
              .and(takesArgument(1, named(String.class.getName())))
              .and(takesArgument(2, named(Exception.class.getName())))
              .and(takesArgument(3, named(boolean.class.getName()))),
          LibertyHttpDispatcherLinkAdvice.class.getName());
    }
  }
}
