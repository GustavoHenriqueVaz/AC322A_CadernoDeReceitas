plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cadernoreceitas"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cadernoreceitas"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // A TheMealDB oferece a chave PUBLICA de teste "1" (documentada no site deles), entao o
        // projeto roda sem nenhum segredo. Se alguem tiver uma chave propria, basta defini-la em
        // ~/.gradle/gradle.properties (MEALDB_API_KEY=...) ou com -PMEALDB_API_KEY=...:
        // assim ela nunca entra no repositorio.
        val mealDbKey = providers.gradleProperty("MEALDB_API_KEY").getOrElse("1")
        require(mealDbKey.matches(Regex("[A-Za-z0-9]+"))) { "MEALDB_API_KEY invalida" }
        buildConfigField(
            "String",
            "MEALDB_BASE_URL",
            "\"https://www.themealdb.com/api/json/v1/$mealDbKey/\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Necessario para o Robolectric enxergar recursos/manifest nos testes JVM
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    // Exporta o schema do Room (boa pratica para migracoes e revisao)
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose (versoes controladas pelo BOM)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Persistencia local
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Trabalho em segundo plano
    implementation(libs.androidx.work.runtime.ktx)

    // Rede e serializacao
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Imagens
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Testes unitarios (JVM + Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)

    // Testes instrumentados (rodam em emulador/dispositivo: ./gradlew connectedDebugAndroidTest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
