# Shobdo Keyboard - release shrinker rules.
#
# There is no reflection in the app: JSON is decoded by kotlinx.serialization's generated
# serializers, so the only thing R8 must not strip is those generated members.

-keepattributes *Annotation*, InnerClasses, Signature

# Generated serializers for our model classes.
-keepclassmembers @kotlinx.serialization.Serializable class app.shobdo.keyboard.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.shobdo.keyboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the entry points Android binds to by name in the manifest.
-keep class app.shobdo.keyboard.ime.ShobdoIME { public <init>(...); }
-keep class app.shobdo.keyboard.settings.SettingsActivity { public <init>(...); }

-dontwarn kotlinx.serialization.**
