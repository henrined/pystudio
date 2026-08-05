#include <jni.h>
#include <string>
#include <vector>
#include "gitengine.h"
#include "pystudio/jni_utils.h"

using namespace pystudio::gitengine;

extern "C" {

// GitRepositoryService
JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeClone(JNIEnv* env, jobject thiz, jstring url, jstring destPath, jstring username, jstring token) {
    std::string cppUrl = pystudio::jni::jstring_to_cpp(env, url);
    std::string cppDestPath = pystudio::jni::jstring_to_cpp(env, destPath);
    std::string cppUser = pystudio::jni::jstring_to_cpp(env, username);
    std::string cppToken = pystudio::jni::jstring_to_cpp(env, token);
    GitEngine engine;
    return engine.Clone(cppUrl, cppDestPath, cppUser, cppToken) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeOpen(JNIEnv* env, jobject thiz, jstring repoPath) {
    std::string cppPath = pystudio::jni::jstring_to_cpp(env, repoPath);
    GitEngine engine;
    return engine.Open(cppPath) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeGetStatus(JNIEnv* env, jobject thiz, jstring repoPath) {
    std::string cppPath = pystudio::jni::jstring_to_cpp(env, repoPath);
    GitEngine engine;
    engine.Open(cppPath);
    GitStatus status = engine.GetStatus();

    jclass statusClass = env->FindClass("com/pystudio/core/GitStatus");
    jmethodID constructor = env->GetMethodID(statusClass, "<init>", "(Ljava/lang/String;IILjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;)V");

    jstring jBranch = env->NewStringUTF(status.branchName.c_str());
    jint jAhead = status.ahead;
    jint jBehind = status.behind;

    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    auto toJList = [&](const std::vector<std::string>& vec) {
        jobject jList = env->NewObject(arrayListClass, arrayListInit);
        for (const auto& s : vec) {
            jstring jStr = env->NewStringUTF(s.c_str());
            env->CallBooleanMethod(jList, arrayListAdd, jStr);
            env->DeleteLocalRef(jStr);
        }
        return jList;
    };

    jobject jMod = toJList(status.modifiedFiles);
    jobject jUntr = toJList(status.untrackedFiles);
    jobject jStag = toJList(status.stagedFiles);
    jobject jConf = toJList(status.conflictedFiles);

    jobject result = env->NewObject(statusClass, constructor, jBranch, jAhead, jBehind, jMod, jUntr, jStag, jConf);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeStageFile(JNIEnv* env, jobject thiz, jstring repoPath, jstring filePath) {
    std::string cppRepo = pystudio::jni::jstring_to_cpp(env, repoPath);
    std::string cppFile = pystudio::jni::jstring_to_cpp(env, filePath);
    GitEngine engine;
    if (!engine.Open(cppRepo)) return JNI_FALSE;
    return engine.StageFile(cppFile) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeUnstageFile(JNIEnv* env, jobject thiz, jstring repoPath, jstring filePath) {
    std::string cppRepo = pystudio::jni::jstring_to_cpp(env, repoPath);
    std::string cppFile = pystudio::jni::jstring_to_cpp(env, filePath);
    GitEngine engine;
    if (!engine.Open(cppRepo)) return JNI_FALSE;
    return engine.UnstageFile(cppFile) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeCommit(JNIEnv* env, jobject thiz, jstring repoPath, jstring message, jstring authorName, jstring authorEmail) {
    std::string cppRepo = pystudio::jni::jstring_to_cpp(env, repoPath);
    GitEngine engine;
    if (!engine.Open(cppRepo)) return JNI_FALSE;
    return engine.Commit(pystudio::jni::jstring_to_cpp(env, message), pystudio::jni::jstring_to_cpp(env, authorName), pystudio::jni::jstring_to_cpp(env, authorEmail)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeCreateBranch(JNIEnv* env, jobject thiz, jstring repoPath, jstring name) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.CreateBranch(pystudio::jni::jstring_to_cpp(env, name)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeCheckoutBranch(JNIEnv* env, jobject thiz, jstring repoPath, jstring name) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.CheckoutBranch(pystudio::jni::jstring_to_cpp(env, name)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeDeleteBranch(JNIEnv* env, jobject thiz, jstring repoPath, jstring name) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.DeleteBranch(pystudio::jni::jstring_to_cpp(env, name)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeListBranches(JNIEnv* env, jobject thiz, jstring repoPath) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return env->NewObject(env->FindClass("java/util/ArrayList"), env->GetMethodID(env->FindClass("java/util/ArrayList"), "<init>", "()V"));
    std::vector<std::string> branches = engine.ListBranches();
    
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject jList = env->NewObject(arrayListClass, arrayListInit);
    for (const auto& s : branches) {
        jstring jStr = env->NewStringUTF(s.c_str());
        env->CallBooleanMethod(jList, arrayListAdd, jStr);
        env->DeleteLocalRef(jStr);
    }
    return jList;
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeDiff(JNIEnv* env, jobject thiz, jstring repoPath, jstring filePath) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return env->NewStringUTF("");
    std::string diff = engine.GetDiff(pystudio::jni::jstring_to_cpp(env, filePath));
    return env->NewStringUTF(diff.c_str());
}

JNIEXPORT jobject JNICALL
Java_com_pystudio_core_GitRepositoryService_nativeLog(JNIEnv* env, jobject thiz, jstring repoPath, jint maxCount) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jobject jList = env->NewObject(arrayListClass, arrayListInit);
    
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return jList;
    
    auto commits = engine.GetLog(maxCount);
    
    jclass commitClass = env->FindClass("com/pystudio/core/CommitLog");
    jmethodID commitInit = env->GetMethodID(commitClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;J)V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    
    for (const auto& c : commits) {
        jstring jHash = env->NewStringUTF(c.oid.c_str());
        jstring jMsg = env->NewStringUTF(c.message.c_str());
        jstring jAuthor = env->NewStringUTF(c.author.c_str());
        jobject jCommit = env->NewObject(commitClass, commitInit, jHash, jMsg, jAuthor, (jlong)c.timestamp);
        
        env->CallBooleanMethod(jList, arrayListAdd, jCommit);
        
        env->DeleteLocalRef(jHash);
        env->DeleteLocalRef(jMsg);
        env->DeleteLocalRef(jAuthor);
        env->DeleteLocalRef(jCommit);
    }
    
    return jList;
}

// GitSyncService
JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitSyncService_nativePush(JNIEnv* env, jobject thiz, jstring repoPath, jstring remoteName, jstring username, jstring token) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.Push(pystudio::jni::jstring_to_cpp(env, remoteName), pystudio::jni::jstring_to_cpp(env, username), pystudio::jni::jstring_to_cpp(env, token)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitSyncService_nativePull(JNIEnv* env, jobject thiz, jstring repoPath, jstring remoteName, jstring username, jstring token) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.Pull(pystudio::jni::jstring_to_cpp(env, remoteName), pystudio::jni::jstring_to_cpp(env, username), pystudio::jni::jstring_to_cpp(env, token)) ? JNI_TRUE : JNI_FALSE;
}

// GitMergeService
JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitMergeService_nativeMerge(JNIEnv* env, jobject thiz, jstring repoPath, jstring sourceBranch) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.Merge(pystudio::jni::jstring_to_cpp(env, sourceBranch)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_core_GitMergeService_nativeRebase(JNIEnv* env, jobject thiz, jstring repoPath, jstring targetBranch) {
    GitEngine engine;
    if (!engine.Open(pystudio::jni::jstring_to_cpp(env, repoPath))) return JNI_FALSE;
    return engine.Rebase(pystudio::jni::jstring_to_cpp(env, targetBranch)) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
