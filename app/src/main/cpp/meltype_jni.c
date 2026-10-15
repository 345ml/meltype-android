// SPDX-License-Identifier: GPL-3.0-or-later
// JNI の橋渡し: Kotlin (NativeCore) ↔ libMeltypeNative.so (本家 Meltype.Mac.Native の C ABI + core-native/AndroidExports.cs)。
// libMeltypeNative.so にはリンクせず dlopen する (読み込めないときに IME ごと落とさず、Kotlin 側でエラーにするため)。
// 文字列は Kotlin 側で UTF-8 の NUL 終端 ByteArray にして渡す (JNI の modified UTF-8 は絵文字を壊すため)。
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

#define TAG "Meltype"

typedef int (*init_mozc_fn)(const char *, const char *);
typedef void *(*create_fn)(void);
typedef void (*destroy_fn)(void *);
typedef void (*session_void_fn)(void *);
typedef char *(*handle_key_fn)(void *, int, int, int, const char *, const char *);
typedef char *(*session_int_fn)(void *, int);
typedef char *(*session_fn)(void *);
typedef void (*set_flag_fn)(void *, int);
typedef char *(*string_fn)(void);
typedef void (*free_fn)(void *);

static struct {
    init_mozc_fn init_mozc;
    create_fn create;
    destroy_fn destroy;
    handle_key_fn handle_key;
    session_int_fn role_key;
    session_fn commit;
    session_int_fn select_candidate;
    set_flag_fn set_direct;
    set_flag_fn set_code_input;
    set_flag_fn set_can_delete;
    set_flag_fn set_private;
    session_void_fn forget_last_commit;
    string_fn version;
    free_fn free_text;
} core;

static int load_symbol(void *lib, void **slot, const char *name) {
    *slot = dlsym(lib, name);
    if (*slot == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "関数がありません: %s", name);
        return 0;
    }
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_dev_horota_meltype_core_NativeCore_load(JNIEnv *env, jobject thiz) {
    void *lib = dlopen("libMeltypeNative.so", RTLD_NOW);
    if (lib == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "libMeltypeNative.so を読み込めません: %s", dlerror());
        return JNI_FALSE;
    }
    int ok = load_symbol(lib, (void **)&core.init_mozc, "meltype_init_mozc")
        && load_symbol(lib, (void **)&core.create, "meltype_create")
        && load_symbol(lib, (void **)&core.destroy, "meltype_destroy")
        && load_symbol(lib, (void **)&core.handle_key, "meltype_handle_key")
        && load_symbol(lib, (void **)&core.role_key, "meltype_role_key")
        && load_symbol(lib, (void **)&core.commit, "meltype_commit")
        && load_symbol(lib, (void **)&core.select_candidate, "meltype_select_candidate")
        && load_symbol(lib, (void **)&core.set_direct, "meltype_set_direct")
        && load_symbol(lib, (void **)&core.set_code_input, "meltype_set_code_input")
        && load_symbol(lib, (void **)&core.set_can_delete, "meltype_set_can_delete")
        && load_symbol(lib, (void **)&core.set_private, "meltype_set_private")
        && load_symbol(lib, (void **)&core.forget_last_commit, "meltype_forget_last_commit")
        && load_symbol(lib, (void **)&core.version, "meltype_version")
        && load_symbol(lib, (void **)&core.free_text, "meltype_free");
    return ok ? JNI_TRUE : JNI_FALSE;
}

/** ByteArray (NUL 終端の UTF-8) を C の文字列として借りる。null なら NULL。 */
static const char *borrow(JNIEnv *env, jbyteArray bytes) {
    return bytes == NULL ? NULL : (const char *)(*env)->GetByteArrayElements(env, bytes, NULL);
}

static void give_back(JNIEnv *env, jbyteArray bytes, const char *text) {
    if (bytes != NULL) (*env)->ReleaseByteArrayElements(env, bytes, (jbyte *)text, JNI_ABORT);
}

/** Core が返した文字列を ByteArray にして、meltype_free で解放する。NULL なら null。 */
static jbyteArray take(JNIEnv *env, char *text) {
    if (text == NULL) return NULL;
    jsize length = (jsize)strlen(text);
    jbyteArray out = (*env)->NewByteArray(env, length);
    if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, length, (const jbyte *)text);
    core.free_text(text);
    return out;
}

static void *session(jlong handle) { return (void *)(intptr_t)handle; }

JNIEXPORT jint JNICALL
Java_dev_horota_meltype_core_NativeCore_initMozc(JNIEnv *env, jobject thiz, jbyteArray helper, jbyteArray profile) {
    const char *h = borrow(env, helper);
    const char *p = borrow(env, profile);
    int result = core.init_mozc(h, p);
    give_back(env, helper, h);
    give_back(env, profile, p);
    return result;
}

JNIEXPORT jlong JNICALL
Java_dev_horota_meltype_core_NativeCore_create(JNIEnv *env, jobject thiz) {
    return (jlong)(intptr_t)core.create();
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_destroy(JNIEnv *env, jobject thiz, jlong handle) {
    core.destroy(session(handle));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_handleKey(JNIEnv *env, jobject thiz, jlong handle, jint vk, jint ch,
                                                   jint modifiers, jbyteArray before, jbyteArray after) {
    const char *b = borrow(env, before);
    const char *a = borrow(env, after);
    char *result = core.handle_key(session(handle), vk, ch, modifiers, b, a);
    give_back(env, before, b);
    give_back(env, after, a);
    return take(env, result);
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_roleKey(JNIEnv *env, jobject thiz, jlong handle, jint vk) {
    return take(env, core.role_key(session(handle), vk));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_commit(JNIEnv *env, jobject thiz, jlong handle) {
    return take(env, core.commit(session(handle)));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_selectCandidate(JNIEnv *env, jobject thiz, jlong handle, jint index) {
    return take(env, core.select_candidate(session(handle), index));
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setDirect(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_direct(session(handle), on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setCodeInput(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_code_input(session(handle), on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setCanDelete(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_can_delete(session(handle), on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setPrivate(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_private(session(handle), on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_forgetLastCommit(JNIEnv *env, jobject thiz, jlong handle) {
    core.forget_last_commit(session(handle));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_version(JNIEnv *env, jobject thiz) {
    return take(env, core.version());
}
