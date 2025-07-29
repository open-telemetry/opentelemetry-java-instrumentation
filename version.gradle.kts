val stableVersion = "2.19.0-SNAPSHOT"
val alphaVersion = "2.19.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
