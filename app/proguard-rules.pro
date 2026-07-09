# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class com.camelcreatives.rekodi.data.local.entity.** { *; }

# Keep RecordingState for process death recovery
-keep class com.camelcreatives.rekodi.recorder.service.RecordingState { *; }

# MediaProjection
-keep class android.media.projection.** { *; }
