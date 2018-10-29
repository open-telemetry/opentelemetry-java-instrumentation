package datadog.trace.agent.test.context;

import datadog.trace.bootstrap.InstrumentationContext;
import java.util.concurrent.Callable;

/** A class which incorrectly uses the context api. */
public class BadClassToRemap {
  /**
   * Callable does not have context class of type State. This should throw an exception during class
   * transform.
   */
  public static int unmappedObjectError(Callable callable) {
    final State state = InstrumentationContext.get(callable, Callable.class, State.class);
    return ++state.anInt;
  }

  public static class State {
    int anInt = 0;
  }
}
