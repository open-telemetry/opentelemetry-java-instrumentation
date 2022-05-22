plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.squareup.okhttp3")
    module.set("okhttp")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  implementation(project(":instrumentation:okhttp:okhttp-3.0:library"))

  library("com.squareup.okhttp3:okhttp:3.0.0")

  testImplementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))
}
