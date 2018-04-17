package datadog.trace.api.sampling;

public class PrioritySampling {
  /**
   * Implementation detail of the client. will not be sent to the agent or propagated.
   *
   * <p>Internal value used when the priority sampling flag has not been set on the span context.
   */
  public static final int UNSET = Integer.MIN_VALUE;
  /** The sampler has decided to drop the trace. */
  public static final int SAMPLER_DROP = 0;
  /** The sampler has decided to keep the trace. */
  public static final int SAMPLER_KEEP = 1;
  /** The user has decided to drop the trace. */
  public static final int USER_DROP = -1;
  /** The user has decided to keep the trace. */
  public static final int USER_KEEP = 2;

  private PrioritySampling() {}
}
