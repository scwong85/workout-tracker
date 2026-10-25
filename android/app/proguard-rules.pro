# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.workouttracker.**$$serializer { *; }
-keepclassmembers class com.workouttracker.** { *** Companion; }
-keepclasseswithmembers class com.workouttracker.** { kotlinx.serialization.KSerializer serializer(...); }
