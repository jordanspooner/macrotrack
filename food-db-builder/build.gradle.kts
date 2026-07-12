plugins {
    application
    kotlin("jvm")
}

application {
    mainClass.set("com.macrotrack.dbbuilder.MainKt")
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("org.apache.commons:commons-csv:1.10.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
