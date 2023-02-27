val stableVersion = "1.24.0-SNAPSHOT"
val alphaVersion = "1.24.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
