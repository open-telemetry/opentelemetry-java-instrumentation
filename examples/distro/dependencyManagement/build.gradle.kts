plugins {
  `java-platform`
}

val otelVersion = "1.51.0"
val otelAlphaVersion = otelVersion.replaceFirst("(-SNAPSHOT)?$".toRegex(), "-alpha$1")
val otelInstrumentationVersion = "2.18.0-SNAPSHOT"
val otelInstrumentationAlphaVersion = otelInstrumentationVersion.replaceFirst("(-SNAPSHOT)?$".toRegex(), "-alpha$1")

// Set version properties for use in other projects
rootProject.extra["otelInstrumentationVersion"] = otelInstrumentationVersion
rootProject.extra["otelInstrumentationAlphaVersion"] = otelInstrumentationAlphaVersion

javaPlatform {
  allowDependencies()
}

dependencies {
  api(enforcedPlatform("io.opentelemetry:opentelemetry-bom:$otelVersion"))
  api(enforcedPlatform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:$otelInstrumentationVersion"))
  api(enforcedPlatform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:$otelInstrumentationAlphaVersion"))
  api(enforcedPlatform("org.junit:junit-bom:5.12.2"))
  constraints {
    api("com.google.auto.service:auto-service:1.1.1")
    api("org.assertj:assertj-core:3.27.3")
    api("org.mockito:mockito-core:5.18.0")
    api("io.opentelemetry.javaagent:opentelemetry-javaagent-tooling:$otelInstrumentationAlphaVersion")
    api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support:$otelInstrumentationAlphaVersion")
    api("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:$otelInstrumentationAlphaVersion")
  }
} 
