plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.scala-conventions")
}

val otel4sVersion = "0.12-c1f8659-SNAPSHOT"
val scalaVersion = "2.13"

muzzle {
  pass {
    group.set("org.typelevel")
    module.set("otel4s-oteljava-context-storage_2.13")
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    versions.set("[$otel4sVersion,)")
    assertInverse.set(true)
    excludeInstrumentationName("opentelemetry-api")
  }
  pass {
    group.set("org.typelevel")
    module.set("otel4s-oteljava-context-storage_3")
    versions.set("[$otel4sVersion,)")
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    assertInverse.set(true)
    excludeInstrumentationName("opentelemetry-api")
  }
}

dependencies {
  bootstrap(project(":instrumentation:otel4s:otel4s-0.12:bootstrap"))

  // we need access to the "application.io.opentelemetry.context.Context"
  // to properly bridge fiber and agent context storages
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  // otel4s
  compileOnly("org.typelevel:otel4s-oteljava_$scalaVersion:0.12.0-RC3")
  compileOnly("org.typelevel:otel4s-oteljava-context-storage_$scalaVersion:$otel4sVersion")

  testImplementation("org.typelevel:otel4s-oteljava_$scalaVersion:0.12.0-RC3")
  testImplementation("org.typelevel:otel4s-oteljava-context-storage_$scalaVersion:$otel4sVersion")

  latestDepTestLibrary("org.typelevel:otel4s-oteljava-context-storage_$scalaVersion:+")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
    jvmArgs("-Dcats.effect.trackFiberContext=true")
  }
}
