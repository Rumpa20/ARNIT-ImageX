# Keep generic signatures used by kotlinx.serialization reflection-free streams
-keepattributes Signature, InnerClasses, EnclosingMethod

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.arnit.imagex.**$$serializer { *; }
-keepclassmembers class com.arnit.imagex.** { *** Companion; }
-keepclasseswithmembers class com.arnit.imagex.** { kotlinx.serialization.KSerializer serializer(...); }

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }
