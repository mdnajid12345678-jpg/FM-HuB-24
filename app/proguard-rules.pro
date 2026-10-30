# Kotlin serialization and database/API models are used through generated adapters and metadata.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.fmhub24.android.data.model.** { *; }
-keepclassmembers class **$$serializer { *; }
