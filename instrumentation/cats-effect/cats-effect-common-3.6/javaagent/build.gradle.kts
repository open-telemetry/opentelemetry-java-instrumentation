plugins {
  id("otel.javaagent-instrumentation")
}

val scalaVersion = "2.13"
val catsEffectVersion = "3.6.0"

dependencies {
  // we need access to the "application.io.opentelemetry.context.Context"
  // to properly bridge fiber and agent context storages
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  compileOnly(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly("org.typelevel:cats-effect_$scalaVersion:$catsEffectVersion")
}
