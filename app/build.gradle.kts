import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Credenciais do Supabase: lidas de local.properties (dev) ou de variáveis de ambiente (CI).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Valores padrão do projeto Supabase "healthcheck". A chave anon é pública por natureza
// (vai dentro do APK); a proteção dos dados é feita pelas regras RLS do banco.
val defaults = mapOf(
    "SUPABASE_URL" to "https://fdjfgswjpitlzavbniez.supabase.co",
    "SUPABASE_ANON_KEY" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZkamZnc3dqcGl0bHphdmJuaWV6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM1Mzc4MzgsImV4cCI6MjEwOTExMzgzOH0.0obkr8LObZiuYeXUd0lMRZZsMOrnII3mK4oFHxWl9-A",
)
fun config(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaults[name] ?: "").trim()

android {
    namespace = "com.healthcheck.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.healthcheck.app"
        minSdk = 26
        targetSdk = 35
        // No GitHub Actions o número da versão sobe a cada build, para a atualização instalar por cima.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${config("SUPABASE_ANON_KEY")}\"")
    }

    // Assinatura fixa: todo APK gerado (no GitHub ou no computador) usa a mesma chave,
    // então uma versão nova instala por cima da anterior sem precisar desinstalar.
    // A chave fica no repositório por escolha (uso pessoal). Para publicar na Play Store,
    // gere uma chave nova e guarde-a fora do repositório.
    signingConfigs {
        create("fixed") {
            storeFile = file("release.keystore")
            storePassword = "healthcheck"
            keyAlias = "healthcheck"
            keyPassword = "healthcheck"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
