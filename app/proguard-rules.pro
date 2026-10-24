# FFmpeg audio decoder (Jellyfin's build of the Media3 extension): DefaultRenderersFactory creates
# FfmpegAudioRenderer by reflection, and the decoder's Java side is called from its native code.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class org.jellyfin.media3.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
