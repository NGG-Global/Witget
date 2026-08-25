# kotlinx.serialization keeps its generated serializers reachable via companion
# objects that R8 cannot see are used.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.softdread.widgets.** {
    *** Companion;
}
-keepclasseswithmembers class com.softdread.widgets.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.softdread.widgets.**$$serializer { *; }

# Glance resolves receivers and action callbacks reflectively by class name.
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }
