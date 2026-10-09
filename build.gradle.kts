// Arquivo de build raiz: apenas declara os plugins (sem aplica-los) para que
// todos os modulos usem exatamente as mesmas versoes (ver gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
}
