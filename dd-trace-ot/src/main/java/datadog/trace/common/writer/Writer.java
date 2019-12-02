package datadog.trace.common.writer;

import datadog.opentracing.DDSpan;
import datadog.trace.api.Config;
import java.io.Closeable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** A writer is responsible to send collected spans to some place */
public interface Writer extends Closeable {

  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param trace the list of spans to write
   */
  void write(List<DDSpan> trace);

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  @Override
  void close();

  /** Count that a trace was captured for stats, but without reporting it. */
  void incrementTraceCount();

  @Slf4j
  final class Builder {

    public static Writer forConfig(final Config config) {
      final Writer writer;

      if (config != null) {
        final String configuredType = config.getWriterType();
        if (Config.LOGGING_WRITER_TYPE.equals(configuredType)) {
          writer = new LoggingWriter();
        } else {
          log.warn(
              "Writer type not configured correctly: Type {} not recognized. Defaulting to LoggingWriter.",
              configuredType);
          writer = new LoggingWriter();
        }
      } else {
        log.warn(
            "Writer type not configured correctly: No config provided! Defaulting to LoggingWriter.");
        writer = new LoggingWriter();
      }

      return writer;
    }

    private Builder() {}
  }
}
