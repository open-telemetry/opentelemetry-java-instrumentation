allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.12.0-alpha-SNAPSHOT"
  } else {
    version = "1.12.0-SNAPSHOT"
  }
}
