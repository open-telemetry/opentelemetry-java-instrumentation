/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.RemoteInstrument;
import scala.collection.immutable.Vector;

/**
 * Registers {@link OtelRemoteInstrument} with the instruments that pekko creates from the {@code
 * pekko.remote.artery.advanced.instruments} configuration.
 */
class RemoteInstrumentsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.RemoteInstruments$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("create").and(returns(named("scala.collection.immutable.Vector"))),
        getClass().getName() + "$CreateAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateAdvice {

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Vector<RemoteInstrument> onExit(@Advice.Return Vector<RemoteInstrument> result) {
      return RemoteInstrumentsHelper.addOtelInstrument(result);
    }
  }
}
