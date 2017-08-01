package com.datadoghq.trace.resolver;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.AbstractDecorator;
import com.datadoghq.trace.sampling.AbstractSampler;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.sampling.Sampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.DDApi;
import com.datadoghq.trace.writer.LoggingWriter;
import com.datadoghq.trace.writer.Writer;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Create a tracer from a configuration file */
@Slf4j
public class DDTracerFactory {

  public static final String SYSTEM_PROPERTY_CONFIG_PATH = "dd.trace.configurationFile";
  public static final String CONFIG_PATH = "dd-trace";

  private static final String DD_AGENT_WRITER_TYPE = DDAgentWriter.class.getSimpleName();
  private static final String LOGGING_WRITER_TYPE = LoggingWriter.class.getSimpleName();
  private static final String ALL_SAMPLER_TYPE = AllSampler.class.getSimpleName();
  private static final String RATE_SAMPLER_TYPE = RateSampler.class.getSimpleName();

  /**
   * Create a tracer from a TracerConfig object
   *
   * @param config
   * @return the corresponding tracer
   */
  public static DDTracer create(final TracerConfig config) {
    final String defaultServiceName =
        config.getDefaultServiceName() != null
            ? config.getDefaultServiceName()
            : DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME;

    //Create writer
    final Writer writer;

    if (config.getWriter() != null) {
      final WriterConfig c = config.getWriter();
      if (DD_AGENT_WRITER_TYPE.equals(c.getType())) {
        writer =
            new DDAgentWriter(
                new DDApi(
                    c.getHost(DDAgentWriter.DEFAULT_HOSTNAME),
                    c.getPort(DDAgentWriter.DEFAULT_PORT)));
      } else if (LOGGING_WRITER_TYPE.equals(c.getType())) {
        writer = new LoggingWriter();
      } else {
        writer = DDTracer.UNASSIGNED_WRITER;
      }
    } else {
      writer = DDTracer.UNASSIGNED_WRITER;
    }

    //Create sampler
    final Sampler sampler;

    if (config.getSampler() != null) {
      if (RATE_SAMPLER_TYPE.equals(config.getSampler().getType())) {
        sampler = new RateSampler(config.getSampler().getRate());
      } else if (ALL_SAMPLER_TYPE.equals(config.getSampler().getType())) {
        sampler = new AllSampler();
      } else {
        sampler = DDTracer.UNASSIGNED_SAMPLER;
      }

    } else {
      sampler = DDTracer.UNASSIGNED_SAMPLER;
    }

    //Add sampled tags
    final Map<String, String> skipTagsPatterns = config.getSampler().getSkipTagsPatterns();
    if (skipTagsPatterns != null && sampler instanceof AbstractSampler) {
      final AbstractSampler aSampler = (AbstractSampler) sampler;
      for (final Map.Entry<String, String> entry : skipTagsPatterns.entrySet()) {
        aSampler.addSkipTagPattern(entry.getKey(), Pattern.compile(entry.getValue()));
      }
    }

    //Create tracer
    return new DDTracer(defaultServiceName, writer, sampler);
  }

  public static DDTracer createFromConfigurationFile() {
    final TracerConfig tracerConfig =
        FactoryUtils.loadConfigFromFilePropertyOrResource(
            SYSTEM_PROPERTY_CONFIG_PATH, CONFIG_PATH, TracerConfig.class);

    DDTracer tracer = null;
    if (tracerConfig == null) {
      log.info("No valid configuration file {} found. Loading default tracer.", CONFIG_PATH);
      tracer = new DDTracer();
    } else {
      tracer = DDTracerFactory.create(tracerConfig);
    }

    //Create decorators from resource files
    final List<AbstractDecorator> decorators = DDDecoratorsFactory.createFromResources();
    for (final AbstractDecorator decorator : decorators) {
      tracer.addDecorator(decorator);
    }

    return tracer;
  }
}
