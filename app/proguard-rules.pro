# Правила R8 для релизной сборки ABPlayer.
# Media3, Coil, Compose и корутины приносят свои consumer-правила; компоненты из манифеста
# (Application, Activity, Service) R8 сохраняет сам.

# Читаемые стектрейсы в отчётах о падениях
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 использует рефлексию при создании расширений/декодеров
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Guava: необязательные аннотации и классы, которых нет на Android
-dontwarn com.google.common.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
