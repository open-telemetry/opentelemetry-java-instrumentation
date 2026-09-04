/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.remote.artery.RemoteInstrument;
import scala.collection.Iterable;
import scala.collection.JavaConverters;
import scala.collection.immutable.Vector;

public final class RemoteInstrumentsHelper {

  /**
   * Returns the given instruments with {@link OtelRemoteInstrument} added to them.
   *
   * <p>The instruments are kept in a scala {@code Vector} and appending to one is not the same in
   * every scala version, {@code :+} takes an implicit {@code CanBuildFrom} in 2.12 that 2.13
   * replaced with {@code appended}. The agent is compiled once and runs with all of them, so build
   * a new {@code Vector} with methods that have the same signature in each version instead.
   */
  public static Vector<RemoteInstrument> addOtelInstrument(Vector<RemoteInstrument> instruments) {
    List<RemoteInstrument> result = new ArrayList<>(JavaConverters.seqAsJavaList(instruments));
    for (RemoteInstrument instrument : result) {
      if (instrument instanceof OtelRemoteInstrument) {
        return instruments;
      }
    }
    result.add(new OtelRemoteInstrument());

    Iterable<RemoteInstrument> scalaIterable = JavaConverters.collectionAsScalaIterable(result);
    return scalaIterable.toVector();
  }

  private RemoteInstrumentsHelper() {}
}
