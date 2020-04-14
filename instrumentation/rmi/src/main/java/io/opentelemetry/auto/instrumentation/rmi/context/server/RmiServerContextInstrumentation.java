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
package io.opentelemetry.auto.instrumentation.rmi.context.server;

import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator.CONTEXT_CALL_ID;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.transport.Target;

@AutoService(Instrumenter.class)
public class RmiServerContextInstrumentation extends Instrumenter.Default {

  public RmiServerContextInstrumentation() {
    super("rmi", "rmi-context-propagator", "rmi-server-context-propagator");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("sun.rmi.transport.ObjectTable"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload$InjectAdapter",
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload$ExtractAdapter",
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload",
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator",
      packageName + ".ContextDispatcher",
      packageName + ".ContextDispatcher$NoopRemote"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isStatic())
            .and(named("getTarget"))
            .and((takesArgument(0, named("sun.rmi.transport.ObjectEndpoint")))),
        getClass().getName() + "$ObjectTableAdvice");
  }

  public static class ObjectTableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final Object oe, @Advice.Return(readOnly = false) Target result) {
      // comparing toString() output allows us to avoid using reflection to be able to compare
      // ObjID and ObjectEndpoint objects
      // ObjectEndpoint#toString() only returns this.objId.toString() value which is exactly
      // what we're interested in here.
      if (!CONTEXT_CALL_ID.toString().equals(oe.toString())) {
        return;
      }
      result = ContextDispatcher.newDispatcherTarget();
    }
  }
}
