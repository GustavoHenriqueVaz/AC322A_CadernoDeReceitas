# Regras de ofuscacao (R8). O build de release desta entrega nao usa minificacao
# (isMinifyEnabled = false); este arquivo existe para evitar surpresas caso seja ativada.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.cadernoreceitas.data.remote.dto.** { kotlinx.serialization.KSerializer serializer(...); }
