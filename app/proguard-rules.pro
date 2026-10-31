# Persisted Gson field names must survive upgrades.
-keep class com.vxplayer.music.Song { *; }
-keep class com.vxplayer.privatefiles.PrivateEntry { *; }
-keepattributes Signature,*Annotation*
# Instantiated by Cast using manifest metadata.
-keep class com.vxplayer.cast.VXCastOptions { public <init>(); *; }
# Room, Media3 and Cast ship their own consumer rules.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
