package datadog.trace.api.sampling;

/**
 * Class exposing tag names to mark a span to be dropped or kept by the sampler based on user intent.
 */
final public class ForcedTracing {

  /**
   * The user has decided to drop the trace.
   */
  public static final String manual_DROP = "manual.drop";

  /**
   * The user has decided to keep the trace.
   */
  public static final String manual_KEEP = "manual.keep";

  private ForcedTracing() {}
}
