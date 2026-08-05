#pragma once

#include <string>
#include <vector>
#include <memory>

#ifdef PYSTUDIO_HAS_TFLITE
#include "tensorflow/lite/c/c_api.h"
#endif

#ifdef PYSTUDIO_HAS_LIBTORCH
#include "torch/script.h"
#endif

namespace pystudio {
namespace mlruntime {

// S-9.6: Module C++ mlruntime — dispatch TFLite / LibTorch / OpenCV
class MLRuntime {
public:
    MLRuntime();
    ~MLRuntime();

    // OpenCV integration
    bool ProcessImageOpenCV(const std::string& inputPath, const std::string& outputPath);

    // TFLite integration
    bool LoadTFLiteModel(const std::string& modelPath);
    std::vector<float> RunTFLiteInference(const std::vector<float>& inputData);

    // LibTorch integration
    bool LoadTorchModel(const std::string& modelPath);
    std::vector<float> RunTorchInference(const std::vector<float>& inputData);

private:
#ifdef PYSTUDIO_HAS_TFLITE
    // TFLite handles
    TfLiteModel* tfliteModel_ = nullptr;
    TfLiteInterpreterOptions* tfliteOptions_ = nullptr;
    TfLiteInterpreter* tfliteInterpreter_ = nullptr;
#endif

#ifdef PYSTUDIO_HAS_LIBTORCH
    // LibTorch handle
    std::shared_ptr<torch::jit::script::Module> torchModule_;
#endif
};

} // namespace mlruntime
} // namespace pystudio
