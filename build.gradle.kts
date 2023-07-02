plugins {
    id("java")
}

group = "org.pidu.poolingex"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    implementation("org.postgresql:r2dbc-postgresql:1.0.1.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.0.RELEASE")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(platform("org.testcontainers:testcontainers-bom:1.18.3"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}