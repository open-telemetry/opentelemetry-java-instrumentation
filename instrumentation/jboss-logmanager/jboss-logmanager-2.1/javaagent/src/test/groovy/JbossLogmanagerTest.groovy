import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.logs.data.Severity
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.jboss.logmanager.MDC
import org.jboss.logmanager.Level
import org.jboss.logmanager.LogContext
import spock.lang.Unroll
import org.jboss.logmanager.Logger

import static org.assertj.core.api.Assertions.assertThat
import static org.awaitility.Awaitility.await;

class JbossLogmanagerTest extends AgentInstrumentationSpecification {
  private static final Logger logger = LogContext.getLogContext().getLogger("abc")
  static {
    logger.setLevel(Level.INFO)
  }

  @Unroll
  def "test "(Level testMethod, boolean exception, boolean parent) {
    when:
    if (parent) {
      runWithSpan("parent") {
        if (exception) {
          logger.log(testMethod, "xyz", new IllegalStateException("hello"))
        } else {
          logger.log(testMethod, "xyz")
        }
      }
    } else {
      if (exception) {
        logger.log(testMethod, "xyz", new IllegalStateException("hello"))
      } else {
        logger.log(testMethod, "xyz")
      }
    }

    then:
    if (parent) {
      waitForTraces(1)
    }

    if (severity != null) {
      await()
        .untilAsserted(
          () -> {
            assertThat(logs).hasSize(1)
          })
      def log = logs.get(0)
      assertThat(log.getBody().asString()).isEqualTo("xyz")
      assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
      assertThat(log.getSeverity()).isEqualTo(severity)
      assertThat(log.getSeverityText()).isEqualTo(severityText)
      if (exception) {
        assertThat(log.getAttributes().size()).isEqualTo(5)
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isEqualTo(IllegalStateException.getName())
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isEqualTo("hello")
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).contains(JbossLogmanagerTest.name)
      } else {
        assertThat(log.getAttributes().size()).isEqualTo(2)
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isNull()
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isNull()
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).isNull()
      }
      assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
      assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
      if (parent) {
        assertThat(log.getSpanContext()).isEqualTo(traces.get(0).get(0).getSpanContext())
      } else {
        assertThat(log.getSpanContext().isValid()).isFalse()
      }
    } else {
      Thread.sleep(500) // sleep a bit just to make sure no log is captured
      assertThat(logs.size() == 0).isTrue()
    }

    where:
    [args, exception, parent] << [
      [
        [Level.DEBUG, null, null],
        [Level.INFO, Severity.INFO, "INFO"],
        [Level.WARN, Severity.WARN, "WARN"],
        [Level.ERROR, Severity.ERROR, "ERROR"]
      ],
      [true, false],
      [true, false]
    ].combinations()

    testMethod = args[0] as Level
    severity = args[1]
    severityText = args[2]
  }

  def "test mdc"() {
    when:
    MDC.put("key1", "val1")
    MDC.put("key2", "val2")
    try {
      logger.info("xyz")
    } finally {
      MDC.remove("key1")
      MDC.remove("key2")
    }

    then:

    await()
      .untilAsserted(
        () -> {
          assertThat(logs).hasSize(1)
        })
    def log = logs.get(0)
    assertThat(log.getBody().asString()).isEqualTo("xyz")
    assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
    assertThat(log.getSeverity()).isEqualTo(Severity.INFO)
    assertThat(log.getSeverityText()).isEqualTo("INFO")
    assertThat(log.getAttributes().size()).isEqualTo(4)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("jboss-logmanager.mdc.key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(AttributeKey.stringKey("jboss-logmanager.mdc.key2"))).isEqualTo("val2")
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
  }
}
