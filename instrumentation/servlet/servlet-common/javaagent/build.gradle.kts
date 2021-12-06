plugins {
  id("otel.javaagent-instrumentation")
}

// This module is only used as a dependency for other javaagent modules and does not contain any
// non-abstract implementations of InstrumentationModule
