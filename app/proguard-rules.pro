# Reglas de ProGuard/R8.
# La build release tiene minificacion desactivada por ahora, pero si la activas
# estas reglas evitan que se rompan las clases de Shizuku y el AIDL.

-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepclassmembers class * extends android.app.Activity { *; }
