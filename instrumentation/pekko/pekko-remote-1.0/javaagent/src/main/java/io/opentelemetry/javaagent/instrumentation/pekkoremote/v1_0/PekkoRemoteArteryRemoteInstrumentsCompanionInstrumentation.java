/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.PekkoRemoteSingletons.INSTRUMENT_IDENTIFIER;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.pekkoremote.v1_0.internal.OtelRemoteInstrument;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.RemoteInstrument;
import scala.collection.JavaConverters;
import scala.collection.immutable.Vector;

public class PekkoRemoteArteryRemoteInstrumentsCompanionInstrumentation
    implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.RemoteInstruments$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("create")
            .and(takesArgument(0, named("org.apache.pekko.actor.ExtendedActorSystem")))
            .and(takesArgument(1, named("org.apache.pekko.event.LoggingAdapter"))),
        getClass().getName() + "$CreateAdvice");
  }

  public static class CreateAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Vector<RemoteInstrument> exit(
        @Advice.Return Vector<RemoteInstrument> remoteInstruments) {
      RemoteInstrument[] updatedRemoteInstruments =
          new RemoteInstrument[remoteInstruments.length() + 1];
      for (int i = 0; i < remoteInstruments.length(); i++) {
        RemoteInstrument remoteInstrument = remoteInstruments.apply(i);

        if (remoteInstrument instanceof OtelRemoteInstrument) {
          // instrument already added
          return remoteInstruments;
        }

        if (remoteInstrument.identifier() == INSTRUMENT_IDENTIFIER) {
          // identifier already in use
          return remoteInstruments;
        }

        updatedRemoteInstruments[i] = remoteInstrument;
      }
      updatedRemoteInstruments[remoteInstruments.length()] =
          new OtelRemoteInstrument(INSTRUMENT_IDENTIFIER);
      return JavaConverters.asScalaBuffer(asList(updatedRemoteInstruments)).toVector();
    }
  }
}
