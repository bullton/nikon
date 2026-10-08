# Keep PTP/IP & NAS protocol classes
-keep class com.camtonas.app.camera.** { *; }
-keep class com.camtonas.app.nas.** { *; }
-keep class com.camtonas.app.sync.** { *; }

# sshj uses reflection
-keep class com.hierynomus.sshj.** { *; }
-keep class net.schmizz.sshj.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# jcifs-ng
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# Apache Commons Net
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**
