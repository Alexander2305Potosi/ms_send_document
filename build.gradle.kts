plugins {
    java
    application
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("info.solidsoft.pitest") version "1.15.0"
}

group = "com.example"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
        exclude(group = "org.springframework", module = "spring-webmvc")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot - WebFlux only (excluyendo MVC)
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    }
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Reactive
    implementation("io.projectreactor:reactor-core")
    implementation("io.projectreactor.addons:reactor-extra")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // MapStruct
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")

    // Logging
    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic")

    // SOAP (solo JAXB, sin Spring Web Services que trae MVC)
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.4")

    // R2DBC + H2 (reactive database for traceability)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-h2")
    runtimeOnly("com.h2database:h2")

    // Monitoring & Tracing
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.projectreactor:reactor-tools")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

springBoot {
    buildInfo()
}

pitest {
    junit5PluginVersion.set("1.2.1")
    pitestVersion.set("1.15.0")
    targetClasses.set(listOf("com.example.fileprocessor.domain.*", "com.example.fileprocessor.infrastructure.soap.*", "com.example.fileprocessor.infrastructure.rest.*"))
    targetTests.set(listOf("com.example.fileprocessor.*"))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(50)
    coverageThreshold.set(60)
    mutators.set(listOf("DEFAULTS", "REMOVE_CONDITIONALS_EQUAL_IF", "REMOVE_CONDITIONALS_ORDER_IF", "REMOVE_INCREMENTS", "INVERT_NEGS", "MATH", "NEGATE_CONDITIONALS", "VOID_METHOD_CALLS", "NON_VOID_METHOD_CALLS"))
    excludedClasses.set(listOf("com.example.fileprocessor.Application", "com.example.fileprocessor.config.*", "com.example.fileprocessor.infrastructure.config.*", "com.example.fileprocessor.mock.*"))
    excludedMethods.set(listOf("toString", "hashCode", "equals", "log.*"))
}
