-keep class dadb.** { *; }
-keep class br.leve.launcher.** { *; }
# Bibliotecas opcionais referenciadas pelo okio/dadb que não existem no Android
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
