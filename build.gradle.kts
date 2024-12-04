plugins {
  java
  application
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

dependencies {
  // tag::bom[]
  implementation(platform("io.github.resilience4j:resilience4j-bom:2.1.0"))
  implementation(platform("io.vertx:vertx-stack-depchain:5.0.0.CR2"))
  // end::bom[]

  implementation("io.github.resilience4j:resilience4j-bulkhead")
  // tag::dependency-resilience4j[]
  implementation("io.github.resilience4j:resilience4j-circuitbreaker")
  // end::dependency-resilience4j[]
  implementation("io.github.resilience4j:resilience4j-ratelimiter")
  implementation("io.github.resilience4j:resilience4j-retry")
  implementation("io.github.resilience4j:resilience4j-timelimiter")

  // tag::dependency-vertx[]
  implementation("io.vertx:vertx-web")
  implementation("io.vertx:vertx-web-client")
  // end::dependency-vertx[]

  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
  testImplementation("org.mockito:mockito-core:5.4.0")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
  mainClass.set("io.vertx.howtos.resilience4j.CircuitBreakerVerticle")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
