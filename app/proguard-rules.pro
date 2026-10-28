# kotlinx.serialization - modele sejfu
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class pl.wolter.ostoja.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class pl.wolter.ostoja.data.** { *; }
-dontwarn com.google.zxing.**
-keep class pl.wolter.ostoja.sync.** { *; }
