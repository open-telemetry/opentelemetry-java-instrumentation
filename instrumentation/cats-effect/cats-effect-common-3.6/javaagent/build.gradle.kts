plugins {
  id("otel.javaagent-instrumentation")
}

val scalaVersion = "2.13"
val catsEffectVersion = "3.6.0"

muzzle {
  pass {
    group.set("org.typelevel")
    module.set("cats-effect_2.12")
    versions.set("[$catsEffectVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.typelevel")
    module.set("cats-effect_2.13")
    versions.set("[$catsEffectVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.typelevel")
    module.set("cats-effect_3")
    versions.set("[$catsEffectVersion,)")
    assertInverse.set(true)
  }
}

dependencies {
  // we need access to the "application.io.opentelemetry.context.Context"
  // to properly bridge fiber and agent context storages
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  compileOnly(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly("org.typelevel:cats-effect_$scalaVersion:$catsEffectVersion")
}
