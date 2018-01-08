package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.DDTraceConfig;
import com.datadoghq.trace.Service;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/** A writer is responsible to send collected spans to some place */
public interface Writer {
  static final String DD_AGENT_WRITER_TYPE = DDAgentWriter.class.getSimpleName();
  static final String LOGGING_WRITER_TYPE = LoggingWriter.class.getSimpleName();

  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param trace the list of spans to write
   */
  void write(List<DDBaseSpan<?>> trace);

  /**
   * Report additional service information to the endpoint
   *
   * @param services a list of extra information about services
   */
  void writeServices(Map<String, Service> services);

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  void close();

  @Slf4j
  final class Builder {
    public static Writer forConfig(final Properties config) {
      final Writer writer;

      if (config != null) {
        final String configuredType = config.getProperty(DDTraceConfig.WRITER_TYPE);
        if (DD_AGENT_WRITER_TYPE.equals(configuredType)) {
          writer =
              new DDAgentWriter(
                  new DDApi(
                      config.getProperty(DDTraceConfig.AGENT_HOST),
                      Integer.parseInt(config.getProperty(DDTraceConfig.AGENT_PORT))));
        } else if (LOGGING_WRITER_TYPE.equals(configuredType)) {
          writer = new LoggingWriter();
        } else {
          log.warn(
              "Writer type not configured correctly: Type {} not recognized. Defaulting to DDAgentWriter.",
              configuredType);
          writer =
              new DDAgentWriter(
                  new DDApi(
                      config.getProperty(DDTraceConfig.AGENT_HOST),
                      Integer.parseInt(config.getProperty(DDTraceConfig.AGENT_PORT))));
        }
      } else {
        log.warn(
            "Writer type not configured correctly: No config provided! Defaulting to DDAgentWriter.");
        writer = new DDAgentWriter();
      }

      return writer;
    }

    private Builder() {}
  }
}
