package io.opentelemetry.instrumentation.gradle

import org.gradle.api.provider.Property

abstract class OtelInstrumentationExtension {

  abstract val name : Property<String>

  abstract val version : Property<String>
}