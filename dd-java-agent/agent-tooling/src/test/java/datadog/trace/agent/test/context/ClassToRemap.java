package datadog.trace.agent.test.context;

import datadog.trace.agent.tooling.context.InstrumentationContext;

/** A class which correctly uses the context api. */
public class ClassToRemap {

  /**
   * ClassToRemap has context class of type State.
   *
   * @return The value of State#anInt
   */
  public static int mapObject(final ClassToRemap classToRemap) {
    final State state = InstrumentationContext.get(classToRemap, ClassToRemap.class, State.class);
    return ++state.anInt;
  }

  /**
   * Runnable has context class of type State.
   *
   * @return The value of State#anInt
   */
  public static int mapOtherObject(final Runnable runnable) {
    State state = InstrumentationContext.get(runnable, Runnable.class, State.class);
    return ++state.anInt;
  }

  /**
   * Instance passed to the context api does not extend the user class. This will throw an
   * exception.
   */
  public static int mapIncorrectObject() {
    State state = InstrumentationContext.get(new Object(), Runnable.class, State.class);
    return state.anInt;
  }

  public static class State {
    public int anInt = 0;
    public Object anObject = new Object();
  }
}
