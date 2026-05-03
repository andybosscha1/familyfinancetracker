# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Firebase Firestore data classes rely on reflection / default constructors.
-keepclassmembers class com.timmat.financetracker.data.model.** {
    <init>();
    <fields>;
    public *;
}

# Hilt / Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }

# Play Services / Credential Manager
-keep class com.google.android.libraries.identity.googleid.** { *; }
