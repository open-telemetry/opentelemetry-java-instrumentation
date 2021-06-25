plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.dropwizard")
    module.set("dropwizard-views")
    versions.set("(,)")
  }
}

dependencies {
  compileOnly("io.dropwizard:dropwizard-views:0.7.0")

  testImplementation("io.dropwizard:dropwizard-views-freemarker:0.7.0")
  testImplementation("io.dropwizard:dropwizard-views-mustache:0.7.0")
}
