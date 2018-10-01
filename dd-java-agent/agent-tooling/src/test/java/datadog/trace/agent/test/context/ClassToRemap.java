package datadog.trace.agent.test.context;

import datadog.trace.agent.tooling.context.InstrumentationContext;

/**
 * A class which correctly uses the context api.
 */
public class ClassToRemap {

  /**
   *  ClassToRemap has context class of type State.
   *  @return The value of State#anInt
   */
  public static int mapObject(final ClassToRemap classToRemap) {
    final State state = InstrumentationContext.get(classToRemap, State.class);
    return ++state.anInt;
  }

  /**
   *  Runnable has context class of type State.
   *  @return The value of State#anInt
   */
  public static int mapOtherObject(final Runnable runnable) {
    final State state = InstrumentationContext.get(runnable, State.class);
    return ++state.anInt;
  }

  public static class State {
    int anInt = 0;
  }
}
