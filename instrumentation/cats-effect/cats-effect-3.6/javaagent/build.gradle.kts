plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.scala-conventions")
}

val scalaVersion = "2.13"
val catsEffectVersion = "3.6.0"

muzzle {
  pass {
    group.set("org.typelevel")
    module.set("cats-effect_2.12")
    versions.set("[$catsEffectVersion,)")
    assertInverse.set(true)
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
  }
  pass {
    group.set("org.typelevel")
    module.set("cats-effect_2.13")
    versions.set("[$catsEffectVersion,)")
    assertInverse.set(true)
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
  }
  pass {
    group.set("org.typelevel")
    module.set("cats-effect_3")
    versions.set("[$catsEffectVersion,)")
    assertInverse.set(true)
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
  }
}

dependencies {
  bootstrap(project(":instrumentation:cats-effect:cats-effect-3.6:bootstrap"))

  // we need access to the "application.io.opentelemetry.context.Context"
  // to properly bridge fiber and agent context storages
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  implementation(project(":instrumentation:cats-effect:cats-effect-common-3.6:javaagent"))

  compileOnly("org.typelevel:cats-effect_$scalaVersion:$catsEffectVersion")

  testImplementation("org.typelevel:cats-effect_$scalaVersion:$catsEffectVersion")

  latestDepTestLibrary("org.typelevel:cats-effect_$scalaVersion:latest.release")
}

tasks {
  withType<Test>().configureEach {
    // The agent context debug mechanism isn't compatible with the bridge approach
    jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")
    jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
    jvmArgs("-Dcats.effect.trackFiberContext=true")
  }
}
