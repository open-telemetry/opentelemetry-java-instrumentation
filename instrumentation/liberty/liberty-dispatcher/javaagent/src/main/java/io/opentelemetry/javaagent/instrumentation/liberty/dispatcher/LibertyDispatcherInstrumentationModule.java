/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
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
    public void transform(TypeTransformer transformer) {
      // https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
      transformer.applyAdviceToMethod(
          named("sendResponse")
              .and(takesArgument(0, named("com.ibm.wsspi.http.channel.values.StatusCodes")))
              .and(takesArgument(1, named(String.class.getName())))
              .and(takesArgument(2, named(Exception.class.getName())))
              .and(takesArgument(3, named(boolean.class.getName()))),
          LibertyHttpDispatcherLinkAdvice.class.getName());
    }
  }
}
