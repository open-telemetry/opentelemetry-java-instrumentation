plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("fish.payara.extras")
    module.set("payara-embedded-web")
    versions.set("[5.182,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("fish.payara.extras:payara-embedded-web:5.2021.2")
}
