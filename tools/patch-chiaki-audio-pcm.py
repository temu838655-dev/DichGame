#!/usr/bin/env python3
"""Apply the DichGame PCM callback bridge to the pinned chiaki-ng Android JNI source."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CPP = ROOT / "third_party/chiaki-ng/android/app/src/main/cpp"
jni_path = CPP / "chiaki-jni.c"
jni = jni_path.read_text(encoding="utf-8")

def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Cannot safely patch Chiaki {label}: expected 1 match, found {count}")
    return source.replace(old, new, 1)

jni = replace_once(jni, "\tjmethodID java_session_event_rumble_meth;\n",
    "\tjmethodID java_session_event_rumble_meth;\n\tjmethodID java_session_audio_frame_meth;\n",
    "session audio callback field")
callback = """
/* Forward decoded game PCM without blocking the decoder output thread. */
static void android_chiaki_audio_frame_cb(int16_t *buf, size_t samples_count, void *user)
{
    AndroidChiakiSession *session = user;
    android_chiaki_audio_output_frame(buf, samples_count, session->audio_output);
    if(!session->java_session || !session->java_session_audio_frame_meth || samples_count > (size_t)0x7fffffff)
        return;

    JNIEnv *env = attach_thread_jni();
    if(!env)
        return;
    jshortArray samples = E->NewShortArray(env, (jsize)samples_count);
    if(!samples)
        return;
    E->SetShortArrayRegion(env, samples, 0, (jsize)samples_count, (const jshort *)buf);
    if(!E->ExceptionCheck(env))
        E->CallVoidMethod(env, session->java_session, session->java_session_audio_frame_meth,
                          samples, (jint)session->audio_decoder.audio_header.rate,
                          (jint)session->audio_decoder.audio_header.channels);
    if(E->ExceptionCheck(env))
        E->ExceptionClear(env);
    E->DeleteLocalRef(env, samples);
}

"""
jni = replace_once(jni, "static void android_chiaki_event_cb(ChiakiEvent *event, void *user)\n{",
    callback + "static void android_chiaki_event_cb(ChiakiEvent *event, void *user)\n{",
    "audio frame callback implementation")
jni = replace_once(jni,
    "android_chiaki_audio_decoder_set_cb(&session->audio_decoder, android_chiaki_audio_output_settings, android_chiaki_audio_output_frame, session->audio_output);",
    "android_chiaki_audio_decoder_set_cb(&session->audio_decoder, android_chiaki_audio_output_settings, android_chiaki_audio_frame_cb, session);",
    "audio callback registration")
jni = replace_once(jni,
    'session->java_session_event_rumble_meth = E->GetMethodID(env, session->java_session_class, "eventRumble", "(II)V");\n',
    'session->java_session_event_rumble_meth = E->GetMethodID(env, session->java_session_class, "eventRumble", "(II)V");\n'
    'session->java_session_audio_frame_meth = E->GetMethodID(env, session->java_session_class, "eventAudioFrame", "([SII)V");\n',
    "audio callback method lookup")
jni_path.write_text(jni, encoding="utf-8")
print("Patched Chiaki JNI PCM callback bridge.")
