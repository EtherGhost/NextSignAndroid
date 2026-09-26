# Gson needs generic type info and annotations preserved to do reflection-based
# (de)serialization - R8 strips both by default.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# Gson matches JSON keys to field names via reflection, so our network/model
# payload classes need their fields (and the classes themselves) kept intact,
# not renamed or stripped as "unused".
-keep class se.cloudsite.nextsign.network.** { <fields>; }
-keep class se.cloudsite.nextsign.model.** { <fields>; }
-keep class se.cloudsite.nextsign.repository.** { <fields>; }
