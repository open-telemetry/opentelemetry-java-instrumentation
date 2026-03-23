val stableVersion = "2.26.1"
val alphaVersion = "2.26.1-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
