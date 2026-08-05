// deps_stubs.cpp — Stub implementations for unit testing WITHOUT real libraries.
//
// This file is compiled ONLY when PYSTUDIO_MLRUNTIME_USE_STUBS=ON.
// It provides realistic (but fake) behavior so that unit tests can exercise
// the mlruntime code paths without linking against TFLite, OpenCV, or LibTorch.
//
// IMPORTANT: These stubs allocate real memory and simulate plausible return
// values. They must NEVER be used in production builds.

#include "tensorflow/lite/c/c_api.h"
#include "opencv2/opencv.hpp"

#include <cstdlib>
#include <cstring>
#include <string>

// ─── TFLite Stubs ───────────────────────────────────────────────────────────
//
// Internal structures that mirror the opaque TFLite types just enough
// to track tensor sizes and pass data through the inference pipeline.

namespace {

// Internal tensor storage used by the stub interpreter
struct StubTensor {
    void* buffer = nullptr;
    size_t byte_size = 0;

    ~StubTensor() { std::free(buffer); }

    void allocate(size_t size) {
        std::free(buffer);
        byte_size = size;
        buffer = std::calloc(1, size);
    }
};

struct StubModel {
    bool valid = true;
};

struct StubInterpreterOptions {
    int32_t num_threads = 1;
};

struct StubInterpreter {
    StubTensor input_tensor;
    StubTensor output_tensor;
    bool tensors_allocated = false;

    void allocate_default() {
        // Default: 4 float inputs, 4 float outputs (configurable per-model in real TFLite)
        input_tensor.allocate(4 * sizeof(float));
        output_tensor.allocate(4 * sizeof(float));
        tensors_allocated = true;
    }
};

} // anonymous namespace

#ifdef __cplusplus
extern "C" {
#endif

TfLiteModel* TfLiteModelCreateFromFile(const char* model_path) {
    if (!model_path || model_path[0] == '\0') return nullptr;
    auto* model = new StubModel();
    return reinterpret_cast<TfLiteModel*>(model);
}

void TfLiteModelDelete(TfLiteModel* model) {
    delete reinterpret_cast<StubModel*>(model);
}

TfLiteInterpreterOptions* TfLiteInterpreterOptionsCreate() {
    auto* opts = new StubInterpreterOptions();
    return reinterpret_cast<TfLiteInterpreterOptions*>(opts);
}

void TfLiteInterpreterOptionsDelete(TfLiteInterpreterOptions* options) {
    delete reinterpret_cast<StubInterpreterOptions*>(options);
}

void TfLiteInterpreterOptionsSetNumThreads(TfLiteInterpreterOptions* options, int32_t num_threads) {
    if (options) {
        reinterpret_cast<StubInterpreterOptions*>(options)->num_threads = num_threads;
    }
}

TfLiteInterpreter* TfLiteInterpreterCreate(const TfLiteModel* model,
                                            const TfLiteInterpreterOptions* optional_options) {
    if (!model) return nullptr;
    auto* interp = new StubInterpreter();
    return reinterpret_cast<TfLiteInterpreter*>(interp);
}

void TfLiteInterpreterDelete(TfLiteInterpreter* interpreter) {
    delete reinterpret_cast<StubInterpreter*>(interpreter);
}

TfLiteStatus TfLiteInterpreterAllocateTensors(TfLiteInterpreter* interpreter) {
    if (!interpreter) return kTfLiteError;
    reinterpret_cast<StubInterpreter*>(interpreter)->allocate_default();
    return kTfLiteOk;
}

TfLiteStatus TfLiteInterpreterInvoke(TfLiteInterpreter* interpreter) {
    if (!interpreter) return kTfLiteError;
    auto* interp = reinterpret_cast<StubInterpreter*>(interpreter);
    if (!interp->tensors_allocated) return kTfLiteError;

    // Stub inference: copy input to output (identity model)
    size_t copy_size = std::min(interp->input_tensor.byte_size, interp->output_tensor.byte_size);
    if (copy_size > 0 && interp->input_tensor.buffer && interp->output_tensor.buffer) {
        std::memcpy(interp->output_tensor.buffer, interp->input_tensor.buffer, copy_size);
    }
    return kTfLiteOk;
}

TfLiteTensor* TfLiteInterpreterGetInputTensor(const TfLiteInterpreter* interpreter,
                                               int32_t input_index) {
    if (!interpreter || input_index != 0) return nullptr;
    auto* interp = const_cast<StubInterpreter*>(reinterpret_cast<const StubInterpreter*>(interpreter));
    return reinterpret_cast<TfLiteTensor*>(&interp->input_tensor);
}

const TfLiteTensor* TfLiteInterpreterGetOutputTensor(const TfLiteInterpreter* interpreter,
                                                      int32_t output_index) {
    if (!interpreter || output_index != 0) return nullptr;
    auto* interp = reinterpret_cast<const StubInterpreter*>(interpreter);
    return reinterpret_cast<const TfLiteTensor*>(&interp->output_tensor);
}

TfLiteStatus TfLiteTensorCopyFromBuffer(TfLiteTensor* tensor, const void* input_data,
                                         size_t input_data_size) {
    if (!tensor || !input_data) return kTfLiteError;
    auto* stub = reinterpret_cast<StubTensor*>(tensor);
    if (input_data_size > stub->byte_size) return kTfLiteError;
    std::memcpy(stub->buffer, input_data, input_data_size);
    return kTfLiteOk;
}

TfLiteStatus TfLiteTensorCopyToBuffer(const TfLiteTensor* tensor, void* output_data,
                                       size_t output_data_size) {
    if (!tensor || !output_data) return kTfLiteError;
    auto* stub = reinterpret_cast<const StubTensor*>(tensor);
    if (output_data_size > stub->byte_size) return kTfLiteError;
    std::memcpy(output_data, stub->buffer, output_data_size);
    return kTfLiteOk;
}

size_t TfLiteTensorByteSize(const TfLiteTensor* tensor) {
    if (!tensor) return 0;
    return reinterpret_cast<const StubTensor*>(tensor)->byte_size;
}

#ifdef __cplusplus
}
#endif

// ─── OpenCV Stubs ───────────────────────────────────────────────────────────

namespace cv {

Mat::Mat() : rows(0), cols(0), data(nullptr), owns_data_(false) {}

Mat::Mat(int r, int c, int type) : rows(r), cols(c), owns_data_(true) {
    // Allocate real pixel buffer (1 channel for simplicity in stubs)
    int channels = (type == 0) ? 1 : 3;
    size_t size = static_cast<size_t>(r) * c * channels;
    data = static_cast<unsigned char*>(std::calloc(1, size));
}

Mat::Mat(const Mat& other) : rows(other.rows), cols(other.cols), owns_data_(true) {
    size_t size = static_cast<size_t>(rows) * cols * 3;  // assume max channels
    data = static_cast<unsigned char*>(std::malloc(size));
    if (other.data) {
        std::memcpy(data, other.data, size);
    } else {
        std::memset(data, 0, size);
    }
}

Mat& Mat::operator=(const Mat& other) {
    if (this != &other) {
        if (owns_data_) std::free(data);
        rows = other.rows;
        cols = other.cols;
        owns_data_ = true;
        size_t size = static_cast<size_t>(rows) * cols * 3;
        data = static_cast<unsigned char*>(std::malloc(size));
        if (other.data) {
            std::memcpy(data, other.data, size);
        } else {
            std::memset(data, 0, size);
        }
    }
    return *this;
}

Mat::~Mat() {
    if (owns_data_) std::free(data);
}

bool Mat::empty() const {
    return (rows == 0 && cols == 0) || data == nullptr;
}

Mat imread(const std::string& filename, int flags) {
    // Stub: simulate a successful read with a small test image
    // Return empty Mat for non-existent paths (realistic behavior)
    if (filename.empty()) return Mat();
    Mat img(8, 8, 16);  // 8x8 BGR image
    return img;
}

bool imwrite(const std::string& filename, const Mat& img) {
    // Stub: report success if the image is valid
    return !img.empty() && !filename.empty();
}

void cvtColor(const Mat& src, Mat& dst, int code) {
    // Stub: create a grayscale output (single channel) from input
    dst = Mat(src.rows, src.cols, 0);
}

} // namespace cv
