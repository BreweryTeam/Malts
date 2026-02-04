import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.papermc.hangarpublishplugin.model.Platforms
import org.apache.tools.ant.filters.ReplaceTokens
import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.5"
    id("io.papermc.hangar-publish-plugin") version "0.1.2"
    id("com.modrinth.minotaur") version "2.8.7"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.freefair.lombok") version "9.1.0"
}

group = "dev.jsinco.malts"
version = "1.0.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.jsinco.dev/releases")
    maven("https://storehouse.okaeri.eu/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.glaremasters.me/repository/towny/")
    maven("https://maven.playpro.com/")
    maven("https://jitpack.io")
    maven("https://repo.rosewooddev.io/repository/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT") // repo.papermc.io
    compileOnly("org.xerial:sqlite-jdbc:3.47.2.0") // mavenCentral
    compileOnly("org.jetbrains:annotations:26.0.2-1") // mavenCentral
    compileOnly("com.drtshock.playervaults:PlayerVaultsX:4.4.7") // repo.jsinco.dev
    compileOnly("org.popcraft:bolt-bukkit:1.1.33") /* repo.codemc.io */ {
        artifact { classifier = "" }
    }
    compileOnly("com.griefcraft:lwc:2.2.9-dev") // repo.codemc.io
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9-beta1") // maven.enginehub.org
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.9-beta1") // maven.enginehub.org
    compileOnly("com.palmergames.bukkit.towny:towny:0.101.2.0") /* repo.glaremasters.me */ {
        exclude(group = "com.palmergames.adventure")
    }
    compileOnly("net.coreprotect:coreprotect:23.0") // maven.playpro.com
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") /* jitpack.io */ {
        exclude(group = "org.bukkit")
    }
    compileOnly("org.black_ixx:playerpoints:3.3.3") // repo.rosewooddev.io
    compileOnly("com.artillexstudios.axvaults:AxVaults:2.10.1") /* repo.jsinco.dev */ {
        exclude(group = "org.jetbrains", module = "annotations")
    }


    implementation("com.zaxxer:HikariCP:6.3.0") // mavenCentral
    implementation("eu.okaeri:okaeri-configs-yaml-bukkit:5.0.5") // storehouse.okaeri.eu
    implementation("eu.okaeri:okaeri-configs-serdes-bukkit:5.0.5")  // storehouse.okaeri.eu
    implementation("org.bstats:bstats-bukkit:3.0.2") // mavenCentral

    // Tests
    testImplementation(platform("org.junit:junit-bom:5.10.0")) // mavenCentral
    testImplementation("org.junit.jupiter:junit-jupiter") // mavenCentral
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // mavenCentral
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.98.0") // mavenCentral
    testImplementation("org.xerial:sqlite-jdbc:3.47.0.0") // mavenCentral
    testImplementation("ch.qos.logback:logback-classic:1.5.13") // mavenCentral
    testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT") // repo.papermc.io
    testImplementation("org.yaml:snakeyaml:2.5") // mavenCentral
}

tasks {

    runServer {
        minecraftVersion("1.21.10")
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        val shaded = "dev.jsinco.malts.shaded"
        relocate("eu.okaeri", "$shaded.okaeri")
        relocate("com.zaxxer.hikari", "$shaded.hikari")
        relocate("org.bstats", "$shaded.bstats")
        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        outputs.upToDateWhen { false }
        filter<ReplaceTokens>(mapOf(
            "tokens" to mapOf("version" to project.version.toString()),
            "beginToken" to "\${",
            "endToken" to "}"
        )).filteringCharset = Charsets.UTF_8.name()
    }

    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }

    register("publishToDiscord") {
        val webhook = DiscordWebhook(System.getenv("DISCORD_WEBHOOK"))
        webhook.message = "<@&1425973814558326824>"
        webhook.embedTitle = "Malts - v${project.version}"
        webhook.embedDescription = System.getenv("CHANGE_LOG") ?: "No changelog provided."
        webhook.send()
    }

}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar() // FIXME for maven publish
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: run {
        return@modrinth
    })
    projectId.set("15AC4wG1")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    loaders.addAll("paper", "purpur")
    gameVersions.addAll(listOf("1.21.10", "1.21.9","1.21.8", "1.21.7", "1.21.4", "1.21.1", "1.20.6", "1.20.4"))
    changelog.set(System.getenv("CHANGE_LOG") ?: "No changelog provided.")
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version.toString())
        channel.set("Release")
        id.set(project.name)
        apiKey.set(System.getenv("HANGAR_TOKEN") ?: return@register)
        changelog.set(System.getenv("CHANGE_LOG") ?: "No changelog provided.")
        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                platformVersions.set(listOf("1.21.x", "1.20.x"))
            }
        }
    }
}

publishing {
    val repoUrl = System.getenv("REPO_URL") ?: "https://repo.jsinco.dev/releases"
    val user = System.getenv("REPO_USERNAME")
    val pass = System.getenv("REPO_SECRET")


    repositories {
        if (user == null || pass == null) {
            return@repositories
        }
        maven {
            url = uri(repoUrl)
            credentials(PasswordCredentials::class) {
                username = user
                password = pass
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        if (user == null || pass == null) {
            return@publications
        }
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            artifact(tasks.shadowJar.get().archiveFile) {
                builtBy(tasks.shadowJar)
            }
        }
    }
}



class DiscordWebhook(
    val webhookUrl: String
) {

    companion object {
        private const val MAX_EMBED_DESCRIPTION_LENGTH = 4096
    }

    var message: String = "content"
    var username: String = "Malts Updates"
    var avatarUrl: String = "https://github.com/breweryteam.png"
    var embedTitle: String = "Embed Title"
    var embedDescription: String = "Embed Description"
    var embedColor: String = "2c2d45"
    var embedThumbnailUrl: String = "https://iili.io/KUX4gYQ.png"
    var embedImageUrl: String? = null

    private fun hexStringToInt(hex: String): Int {
        val hexWithoutPrefix = hex.removePrefix("#")
        return hexWithoutPrefix.toInt(16)
    }

    private fun buildToJson(): String {
        val json = JsonObject()
        json.addProperty("username", username)
        json.addProperty("avatar_url", avatarUrl)
        json.addProperty("content", message)

        val embed = JsonObject()
        embed.addProperty("title", embedTitle)
        embed.addProperty("description", embedDescription)
        embed.addProperty("color", hexStringToInt(embedColor))

        embedThumbnailUrl?.let {
            val thumbnail = JsonObject()
            thumbnail.addProperty("url", it)
            embed.add("thumbnail", thumbnail)
        }

        embedImageUrl?.let {
            val image = JsonObject()
            image.addProperty("url", it)
            embed.add("image", image)
        }

        val embeds = JsonArray()
        createEmbeds().forEach(embeds::add)

        json.add("embeds", embeds)
        return json.toString()
    }

    private fun createEmbeds(): List<JsonObject> {
        if (embedDescription.length <= MAX_EMBED_DESCRIPTION_LENGTH) {
            return listOf(JsonObject().apply {
                addProperty("title", embedTitle)
                addProperty("description", embedDescription)
                addProperty("color", embedColor.toInt(16))
                embedThumbnailUrl?.let {
                    val thumbnail = JsonObject()
                    thumbnail.addProperty("url", it)
                    add("thumbnail", thumbnail)
                }
                embedImageUrl?.let {
                    val image = JsonObject()
                    image.addProperty("url", it)
                    add("image", image)
                }
            })
        }
        val embeds = mutableListOf<JsonObject>()
        var description = embedDescription
        while (description.isNotEmpty()) {
            val chunkLength = minOf(MAX_EMBED_DESCRIPTION_LENGTH, description.length)
            val chunk = description.take(chunkLength)
            description = description.substring(chunkLength)
            embeds.add(JsonObject().apply {
                addProperty("title", embedTitle)
                addProperty("description", chunk)
                addProperty("color", embedColor.toInt(16))
                embedThumbnailUrl?.let {
                    val thumbnail = JsonObject()
                    thumbnail.addProperty("url", it)
                    add("thumbnail", thumbnail)
                }
                embedImageUrl?.let {
                    val image = JsonObject()
                    image.addProperty("url", it)
                    add("image", image)
                }
            })
        }
        return embeds
    }

    fun send() {
        val url = URI(webhookUrl).toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { outputStream ->
            outputStream.write(buildToJson().toByteArray())

            val responseCode = connection.responseCode
            println("POST Response Code :: $responseCode")
            if (responseCode in 200..299) {
                println("Message sent successfully.")
            } else {
                println("Failed to send message.")
            }
        }
    }
}