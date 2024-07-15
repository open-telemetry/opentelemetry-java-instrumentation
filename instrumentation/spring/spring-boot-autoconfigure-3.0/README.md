# Why do we need a separate module for Spring Boot 3 auto-configuration?

`RestClientInstrumentationAutoConfiguration` imports `RestClientCustomizer`,
which is part of Spring Boot 3 (rather than Spring framework).

If we were to include this in the `spring-boot-autoconfigure` module, we would have to
bump the Spring Boot version to 3, which would break compatibility with Spring Boot 2.
