plugins {
  id("otel.javaagent-instrumentation")
}

// No muzzle check because this instrumentation is written in ASM and muzzle won't work with it.

dependencies {
  library("fish.payara.extras:payara-embedded-web:5.2021.2")
}
