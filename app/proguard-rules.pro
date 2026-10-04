# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
# For more information, see https://developer.android.com/build/shrink-code

# Keep the generated AI and wake-word code from being stripped in release builds.
-keep class com.google.ai.client.generativeai.** { *; }
-keep class ai.picovoice.** { *; }
