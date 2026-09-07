# gomobile-биндинги дёргаются из Go по именам — R8 их переименовывать нельзя.
-keep class libXray.** { *; }
-keep class go.** { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
