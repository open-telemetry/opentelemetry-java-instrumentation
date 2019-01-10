package datadog.trace.tracer.writer;

import datadog.trace.api.Config;
import datadog.trace.tracer.Trace;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/** A writer sends traces to some place. */
public interface Writer {

  /**
   * Write a trace represented by the entire list of all the finished spans.
   *
   * <p>It is up to the tracer to decide if the trace should be written (e.g. for invalid traces).
   *
   * <p>This call doesn't increment trace counter, see {@code incrementTraceCount} for that
   *
   * @param trace the trace to write
   */
  void write(Trace trace);

  /**
   * Inform the writer that a trace occurred but will not be written. Used by tracer-side sampling.
   */
  void incrementTraceCount();

  /** @return Most up to date {@link SampleRateByService} instance. */
  SampleRateByService getSampleRateByService();

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  void close();

  @Slf4j
  final class Builder {

    public static Writer forConfig(final Config config) {
      if (config == null) {
        // There is no way config is not create so getting here must be a code bug
        throw new NullPointerException("Config is required to create writer");
      }

      final Writer writer;

      final String configuredType = config.getWriterType();
      if (Config.DD_AGENT_WRITER_TYPE.equals(configuredType)) {
        writer = createAgentWriter(config);
      } else if (Config.LOGGING_WRITER_TYPE.equals(configuredType)) {
        writer = new LoggingWriter();
      } else {
        log.warn(
            "Writer type not configured correctly: Type {} not recognized. Defaulting to AgentWriter.",
            configuredType);
        writer = createAgentWriter(config);
      }

      return writer;
    }

    public static Writer forConfig(final Properties config) {
      return forConfig(Config.get(config));
    }

    private static Writer createAgentWriter(final Config config) {
      return new AgentWriter(new AgentClient(config.getAgentHost(), config.getAgentPort()));
    }

    private Builder() {}
  }
}
