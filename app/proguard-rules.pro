# Keep kotlinx.serialization descriptors
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.lucianmarin.sub.model.**$$serializer { *; }
-keepclassmembers class com.lucianmarin.sub.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.lucianmarin.sub.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
