## 7. JVM Runtime Metrics

The OpenTelemetry Java Agent automatically collects JVM runtime metrics via JMX. These metrics are enabled by default and provide insight into JVM health — memory, threads, CPU, GC, and class loading.

### Stable Metrics (Enabled by Default)

These metrics follow the [OpenTelemetry JVM semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md) and are always collected:

| OTel Metric Name | Prometheus Name | Description |
|---|---|---|
| `jvm.memory.used` | `jvm_memory_used_bytes` | Measure of memory used |
| `jvm.memory.committed` | `jvm_memory_committed_bytes` | Measure of memory committed |
| `jvm.memory.limit` | `jvm_memory_limit_bytes` | Measure of max memory available |
| `jvm.memory.used_after_last_gc` | `jvm_memory_used_after_last_gc_bytes` | Memory used after last GC |
| `jvm.thread.count` | `jvm_thread_count` | Number of executing platform threads |
| `jvm.class.loaded` | `jvm_class_loaded_total` | Total number of classes loaded |
| `jvm.class.unloaded` | `jvm_class_unloaded_total` | Total number of classes unloaded |
| `jvm.class.count` | `jvm_class_count` | Current number of loaded classes |
| `jvm.cpu.time` | `jvm_cpu_time_seconds_total` | CPU time used by the JVM process |
| `jvm.cpu.count` | `jvm_cpu_count` | Number of available processors |
| `jvm.gc.duration` | `jvm_gc_duration_seconds` | Duration of GC pauses |

> **Note:** The Prometheus exporter automatically converts OTel metric names from dots (`.`) to underscores (`_`) and appends unit suffixes like `_bytes`, `_total`, `_seconds`.

### Experimental Metrics

Additional JVM metrics are available but must be explicitly enabled. These are considered experimental and may change in future releases.

#### How to Enable

```bash
# Command line
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.instrumentation.runtime-telemetry.emit-experimental-telemetry=true \
     -jar my-application.jar

# Environment variable
export OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_TELEMETRY=true
```

#### Experimental Metrics List

| OTel Metric Name | Prometheus Name | Description |
|---|---|---|
| `jvm.memory.init` | `jvm_memory_init_bytes` | Initial memory pool size |
| `jvm.buffer.memory.used` | `jvm_buffer_memory_used_bytes` | Memory used by buffers |
| `jvm.buffer.memory.limit` | `jvm_buffer_memory_limit_bytes` | Total memory capacity of buffers |
| `jvm.buffer.count` | `jvm_buffer_count` | Number of buffers in the pool |
| `jvm.system.cpu.load_1m` | `jvm_system_cpu_load_1m` | System CPU load average (1 min) |
| `jvm.system.cpu.utilization` | `jvm_system_cpu_utilization_ratio` | System CPU utilization |
| `jvm.file_descriptor.count` | `jvm_file_descriptor_count` | Number of open file descriptors |
| `jvm.thread.deadlock.count` | `jvm_thread_deadlock_count` | Threads in deadlock (monitors + ownable synchronizers) |
| `jvm.thread.monitor_deadlock.count` | `jvm_thread_monitor_deadlock_count` | Threads in deadlock (monitors only) |

### Deadlock Detection Metrics

The two deadlock metrics (`jvm.thread.deadlock.count` and `jvm.thread.monitor_deadlock.count`) use `ThreadMXBean.findDeadlockedThreads()` and `ThreadMXBean.findMonitorDeadlockedThreads()` — JMX **operations** that cannot be expressed via standard JMX YAML rules (which only support reading MBean attributes).

| Metric | What It Detects |
|---|---|
| `jvm.thread.deadlock.count` | Deadlocks involving **both** `synchronized` blocks and `java.util.concurrent` locks (e.g., `ReentrantLock`) |
| `jvm.thread.monitor_deadlock.count` | Deadlocks involving **only** `synchronized` blocks (object monitor locks) |

These are equivalent to Prometheus client_java's `jvm_threads_deadlocked` and `jvm_threads_deadlocked_monitor` gauges.

#### Verifying Deadlock Metrics

If you have the Prometheus exporter enabled, you can verify the metrics:

```bash
# Check for deadlock metrics
curl -s http://localhost:9464/metrics | grep jvm_thread_deadlock

# Expected output (0 means no deadlocks — which is healthy):
# HELP jvm_thread_deadlock_count Number of platform threads that are in deadlock...
# TYPE jvm_thread_deadlock_count gauge
# jvm_thread_deadlock_count{...} 0.0
# HELP jvm_thread_monitor_deadlock_count Number of platform threads that are in deadlock...
# TYPE jvm_thread_monitor_deadlock_count gauge
# jvm_thread_monitor_deadlock_count{...} 0.0
```

#### Alerting on Deadlocks

These metrics are particularly useful for alerting. A value greater than 0 indicates a deadlock in the JVM:

```yaml
# Example Prometheus alert rule
groups:
  - name: jvm_deadlock_alerts
    rules:
      - alert: JvmThreadDeadlock
        expr: jvm_thread_deadlock_count > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JVM thread deadlock detected on {{ $labels.instance }}"
          description: "{{ $value }} threads are in deadlock. Immediate investigation required."
```

### Complete Example with All JVM Metrics Enabled

```bash
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.service.name=my-java-service \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -Dotel.metrics.exporter=otlp \
     -Dotel.logs.exporter=otlp \
     -Dotel.traces.exporter=otlp \
     -Dotel.instrumentation.runtime-telemetry.emit-experimental-telemetry=true \
     -jar my-application.jar
```

For JBoss/WildFly, add to `standalone.conf`:

```bash
# =========================================
# OpenTelemetry Java Agent Configuration
# with experimental JVM metrics enabled
# =========================================

OTEL_AGENT_PATH="/opt/jboss/opentelemetry-javaagent.jar"

export OTEL_SERVICE_NAME="jboss-application"
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_EXPORTER_OTLP_PROTOCOL="grpc"
export OTEL_METRICS_EXPORTER="otlp"
export OTEL_LOGS_EXPORTER="otlp"
export OTEL_TRACES_EXPORTER="otlp"

# Enable experimental JVM metrics (buffer pools, CPU utilization,
# file descriptors, deadlock detection)
export OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_TELEMETRY=true

JAVA_OPTS="$JAVA_OPTS -javaagent:$OTEL_AGENT_PATH"
```
