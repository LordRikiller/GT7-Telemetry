# Keep telemetry model + update manifest field names (kotlinx.serialization
# reflects on UpdateManifest; the Frame fields are harmless to keep either way).
-keep class com.gt7telemetry.** { *; }
