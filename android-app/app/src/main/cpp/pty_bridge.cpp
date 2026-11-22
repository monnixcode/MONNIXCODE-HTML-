#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#include <cstdlib>
#include <string>
#include <vector>

namespace {
constexpr char kLogTag[] = "MonnixcodePTY";
int g_master_fd = -1;
pid_t g_child_pid = -1;

void close_master() {
    if (g_master_fd >= 0) {
        close(g_master_fd);
        g_master_fd = -1;
    }
}

void stop_child() {
    if (g_child_pid > 0) {
        kill(g_child_pid, SIGHUP);
        kill(g_child_pid, SIGTERM);
        waitpid(g_child_pid, nullptr, 0);
        g_child_pid = -1;
    }
}

bool set_window_size(int fd, int columns, int rows) {
    struct winsize size{};
    size.ws_col = static_cast<unsigned short>(columns > 0 ? columns : 120);
    size.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 40);
    return ioctl(fd, TIOCSWINSZ, &size) == 0;
}

std::vector<std::string> to_strings(JNIEnv* env, jobjectArray values) {
    std::vector<std::string> result;
    if (values == nullptr) return result;
    const jsize count = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        if (value == nullptr) continue;
        const char* chars = env->GetStringUTFChars(value, nullptr);
        result.emplace_back(chars == nullptr ? "" : chars);
        if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }
    return result;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_monnixcode_workspace_PtyBridge_nativeStart(
    JNIEnv* env,
    jobject /* self */,
    jstring shell_path,
    jstring working_directory,
    jobjectArray environment,
    jint columns,
    jint rows) {
    if (g_master_fd >= 0 || g_child_pid > 0) return -2;
    if (shell_path == nullptr || working_directory == nullptr) return -1;

    const char* shell_chars = env->GetStringUTFChars(shell_path, nullptr);
    const char* cwd_chars = env->GetStringUTFChars(working_directory, nullptr);
    if (shell_chars == nullptr || cwd_chars == nullptr) {
        if (shell_chars != nullptr) env->ReleaseStringUTFChars(shell_path, shell_chars);
        if (cwd_chars != nullptr) env->ReleaseStringUTFChars(working_directory, cwd_chars);
        return -1;
    }
    const std::string shell(shell_chars);
    const std::string cwd(cwd_chars);
    env->ReleaseStringUTFChars(shell_path, shell_chars);
    env->ReleaseStringUTFChars(working_directory, cwd_chars);

    const auto env_values = to_strings(env, environment);
    const int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0 || grantpt(master) != 0 || unlockpt(master) != 0) {
        if (master >= 0) close(master);
        return -errno;
    }

    char slave_name[256]{};
    if (ptsname_r(master, slave_name, sizeof(slave_name)) != 0) {
        close(master);
        return -errno;
    }

    const pid_t child = fork();
    if (child < 0) {
        close(master);
        return -errno;
    }

    if (child == 0) {
        const int slave = open(slave_name, O_RDWR | O_NOCTTY);
        if (slave < 0) _exit(126);
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        if (chdir(cwd.c_str()) != 0) _exit(125);
        for (const auto& item : env_values) {
            const auto separator = item.find('=');
            if (separator != std::string::npos) {
                setenv(item.substr(0, separator).c_str(), item.substr(separator + 1).c_str(), 1);
            }
        }
        set_window_size(STDIN_FILENO, columns, rows);
        execl(shell.c_str(), shell.c_str(), "-i", static_cast<char*>(nullptr));
        _exit(127);
    }

    g_master_fd = master;
    g_child_pid = child;
    fcntl(g_master_fd, F_SETFL, fcntl(g_master_fd, F_GETFL, 0) | O_NONBLOCK);
    set_window_size(g_master_fd, columns, rows);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "PTY started pid=%d", child);
    return static_cast<jint>(child);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_monnixcode_workspace_PtyBridge_nativeRead(
    JNIEnv* env,
    jobject /* self */,
    jint timeout_millis) {
    if (g_master_fd < 0) return env->NewByteArray(0);
    struct pollfd descriptor{g_master_fd, POLLIN | POLLHUP | POLLERR, 0};
    const int poll_result = poll(&descriptor, 1, timeout_millis);
    if (poll_result <= 0 || (descriptor.revents & (POLLIN | POLLHUP | POLLERR)) == 0) {
        return env->NewByteArray(0);
    }
    char buffer[8192];
    const ssize_t count = read(g_master_fd, buffer, sizeof(buffer));
    if (count <= 0) {
        return env->NewByteArray(0);
    }
    jbyteArray output = env->NewByteArray(static_cast<jsize>(count));
    env->SetByteArrayRegion(output, 0, static_cast<jsize>(count), reinterpret_cast<jbyte*>(buffer));
    return output;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_monnixcode_workspace_PtyBridge_nativeWrite(
    JNIEnv* env,
    jobject /* self */,
    jbyteArray input) {
    if (g_master_fd < 0 || input == nullptr) return -1;
    const jsize length = env->GetArrayLength(input);
    if (length == 0) return 0;
    jbyte* bytes = env->GetByteArrayElements(input, nullptr);
    if (bytes == nullptr) return -1;
    const ssize_t written = write(g_master_fd, bytes, static_cast<size_t>(length));
    env->ReleaseByteArrayElements(input, bytes, JNI_ABORT);
    return written < 0 ? -errno : static_cast<jint>(written);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_monnixcode_workspace_PtyBridge_nativeResize(
    JNIEnv* /* env */,
    jobject /* self */,
    jint columns,
    jint rows) {
    return g_master_fd >= 0 && set_window_size(g_master_fd, columns, rows);
}

extern "C" JNIEXPORT void JNICALL
Java_com_monnixcode_workspace_PtyBridge_nativeStop(
    JNIEnv* /* env */,
    jobject /* self */) {
    stop_child();
    close_master();
}
