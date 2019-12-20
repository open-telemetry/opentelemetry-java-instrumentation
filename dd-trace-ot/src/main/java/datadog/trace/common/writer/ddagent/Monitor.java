package datadog.trace.common.writer.ddagent;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.common.writer.DDAgentWriter;
import java.util.List;

/**
 * Callback interface for monitoring the health of the DDAgentWriter. Provides hooks for major
 * lifecycle events...
 *
 * <ul>
 *   <li>start
 *   <li>shutdown
 *   <li>publishing to disruptor
 *   <li>serializing
 *   <li>sending to agent
 * </ul>
 */
public interface Monitor {
  void onStart(final DDAgentWriter agentWriter);

  void onShutdown(final DDAgentWriter agentWriter, final boolean flushSuccess);

  void onPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace);

  void onFailedPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace);

  void onFlush(final DDAgentWriter agentWriter, final boolean early);

  void onScheduleFlush(final DDAgentWriter agentWriter, final boolean previousIncomplete);

  void onSerialize(
      final DDAgentWriter agentWriter, final List<DDSpan> trace, final byte[] serializedTrace);

  void onFailedSerialize(
      final DDAgentWriter agentWriter, final List<DDSpan> trace, final Throwable optionalCause);

  void onSend(
      final DDAgentWriter agentWriter,
      final int representativeCount,
      final int sizeInBytes,
      final DDAgentApi.Response response);

  void onFailedSend(
      final DDAgentWriter agentWriter,
      final int representativeCount,
      final int sizeInBytes,
      final DDAgentApi.Response response);

  final class StatsD implements Monitor {
    public static final String PREFIX = "datadog.tracer";

    public static final String LANG_TAG = "lang";
    public static final String LANG_VERSION_TAG = "lang_version";
    public static final String LANG_INTERPRETER_TAG = "lang_interpreter";
    public static final String LANG_INTERPRETER_VENDOR_TAG = "lang_interpreter_vendor";
    public static final String TRACER_VERSION_TAG = "tracer_version";

    private final String hostInfo;
    private final StatsDClient statsd;

    // DQH - Made a conscious choice to not take a Config object here.
    // Letting the creating of the Monitor take the Config,
    // so it can decide which Monitor variant to create.
    public StatsD(final String host, final int port) {
      hostInfo = host + ":" + port;
      statsd = new NonBlockingStatsDClient(PREFIX, host, port, getDefaultTags());
    }

    // Currently, intended for testing
    private StatsD(final StatsDClient statsd) {
      hostInfo = null;
      this.statsd = statsd;
    }

    protected static final String[] getDefaultTags() {
      return new String[] {
        tag(LANG_TAG, "java"),
        tag(LANG_VERSION_TAG, DDTraceOTInfo.JAVA_VERSION),
        tag(LANG_INTERPRETER_TAG, DDTraceOTInfo.JAVA_VM_NAME),
        tag(LANG_INTERPRETER_VENDOR_TAG, DDTraceOTInfo.JAVA_VM_VENDOR),
        tag(TRACER_VERSION_TAG, DDTraceOTInfo.VERSION)
      };
    }

    private static String tag(final String tagPrefix, final String tagValue) {
      return tagPrefix + ":" + tagValue;
    }

    @Override
    public void onStart(final DDAgentWriter agentWriter) {
      statsd.recordGaugeValue("queue.max_length", agentWriter.getDisruptorCapacity());
    }

    @Override
    public void onShutdown(final DDAgentWriter agentWriter, final boolean flushSuccess) {}

    @Override
    public void onPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {
      statsd.incrementCounter("queue.accepted");
      statsd.count("queue.accepted_lengths", trace.size());
    }

    @Override
    public void onFailedPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {
      statsd.incrementCounter("queue.dropped");
    }

    @Override
    public void onScheduleFlush(final DDAgentWriter agentWriter, final boolean previousIncomplete) {
      // not recorded
    }

    @Override
    public void onFlush(final DDAgentWriter agentWriter, final boolean early) {}

    @Override
    public void onSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final byte[] serializedTrace) {
      // DQH - Because of Java tracer's 2 phase acceptance and serialization scheme, this doesn't
      // map precisely
      statsd.count("queue.accepted_size", serializedTrace.length);
    }

    @Override
    public void onFailedSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final Throwable optionalCause) {
      // TODO - DQH - make a new stat for serialization failure -- or maybe count this towards
      // api.errors???
    }

    @Override
    public void onSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDAgentApi.Response response) {
      onSendAttempt(agentWriter, representativeCount, sizeInBytes, response);
    }

    @Override
    public void onFailedSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDAgentApi.Response response) {
      onSendAttempt(agentWriter, representativeCount, sizeInBytes, response);
    }

    private void onSendAttempt(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDAgentApi.Response response) {
      statsd.incrementCounter("api.requests");
      statsd.recordGaugeValue("queue.length", representativeCount);
      // TODO: missing queue.spans (# of spans being sent)
      statsd.recordGaugeValue("queue.size", sizeInBytes);

      if (response.exception() != null) {
        // covers communication errors -- both not receiving a response or
        // receiving malformed response (even when otherwise successful)
        statsd.incrementCounter("api.errors");
      }

      if (response.status() != null) {
        statsd.incrementCounter("api.responses", "status: " + response.status());
      }
    }

    @Override
    public String toString() {
      if (hostInfo == null) {
        return "StatsD";
      } else {
        return "StatsD { host=" + hostInfo + " }";
      }
    }
  }

  final class Noop implements Monitor {
    @Override
    public void onStart(final DDAgentWriter agentWriter) {}

    @Override
    public void onShutdown(final DDAgentWriter agentWriter, final boolean flushSuccess) {}

    @Override
    public void onPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {}

    @Override
    public void onFailedPublish(final DDAgentWriter agentWriter, final List<DDSpan> trace) {}

    @Override
    public void onFlush(final DDAgentWriter agentWriter, final boolean early) {}

    @Override
    public void onScheduleFlush(
        final DDAgentWriter agentWriter, final boolean previousIncomplete) {}

    @Override
    public void onSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final byte[] serializedTrace) {}

    @Override
    public void onFailedSerialize(
        final DDAgentWriter agentWriter, final List<DDSpan> trace, final Throwable optionalCause) {}

    @Override
    public void onSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDAgentApi.Response response) {}

    @Override
    public void onFailedSend(
        final DDAgentWriter agentWriter,
        final int representativeCount,
        final int sizeInBytes,
        final DDAgentApi.Response response) {}

    @Override
    public String toString() {
      return "NoOp";
    }
  }
}
