# kotlinx.serialization (risposte API e rotte di navigazione type-safe)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nanobuo.mypodder.**$$serializer { *; }
-keepclassmembers class com.nanobuo.mypodder.** {
    *** Companion;
}
-keepclasseswithmembers class com.nanobuo.mypodder.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Rotte di navigazione: nomi stabili per lo stato salvato
-keepnames class com.nanobuo.mypodder.ui.nav.** { *; }
