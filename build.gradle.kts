plugins {
    java
    `maven-publish`
    id("com.diffplug.spotless") version "7.0.3"
}

group = "com.github.paperorm"
version = "1.0.0-SNAPSHOT"

description = "API ORM simples para plugins Paper"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testCompileOnly("org.projectlombok:lombok:1.18.46")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.46")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    test {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

spotless {
    java {
        googleJavaFormat("1.27.0")
        target("src/**/*.java")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
