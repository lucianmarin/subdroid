# Room instantiates the generated RoomDatabase_Impl reflectively through its
# no-arg constructor. Room 2.6.1 only ships "-keep class * extends
# androidx.room.RoomDatabase", which keeps the class but not its members, so R8
# full mode strips the constructor. WorkManager then crashes during startup in
# androidx.startup.InitializationProvider with:
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}

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
