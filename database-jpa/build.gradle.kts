plugins {
  `java-library`
}

dependencies {
  implementation(project(":shared"))

  api("org.springframework.boot:spring-boot-starter-data-jpa")
  api("org.liquibase:liquibase-core")

  annotationProcessor("org.hibernate.orm:hibernate-jpamodelgen:6.6.1.Final")

  testImplementation(project(":test-suite"))
  testImplementation("com.h2database:h2")
}
