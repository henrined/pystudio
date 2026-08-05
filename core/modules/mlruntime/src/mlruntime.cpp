#include "mlruntime.h"
#include "pystudio/logger.h"

#ifdef PYSTUDIO_HAS_OPENCV
#include "opencv2/opencv.hpp"
#endif

namespace pystudio {
namespace mlruntime {

MLRuntime::MLRuntime() {
    PS_LOG_I("MLRuntime", "Initialized PyStudio ML/Scientific Runtime");
}

MLRuntime::~MLRuntime() {
#ifdef PYSTUDIO_HAS_TFLITE
    if (tfliteInterpreter_) {
        TfLiteInterpreterDelete(tfliteInterpreter_);
    }
    if (tfliteOptions_) {
        TfLiteInterpreterOptionsDelete(tfliteOptions_);
    }
    if (tfliteModel_) {
        TfLiteModelDelete(tfliteModel_);
    }
#endif
}

bool MLRuntime::ProcessImageOpenCV(const std::string& inputPath, const std::string& outputPath) {
#ifdef PYSTUDIO_HAS_OPENCV
    PS_LOG_I("MLRuntime", "OpenCV: Processing image " + inputPath);
    cv::Mat img = cv::imread(inputPath, 1);
    if (img.empty()) {
        PS_LOG_E("MLRuntime", "OpenCV: Failed to load image");
        return false;
    }
    cv::Mat grayImg;
    cv::cvtColor(img, grayImg, cv::COLOR_BGR2GRAY);
    bool result = cv::imwrite(outputPath, grayImg);
    if (result) {
        PS_LOG_I("MLRuntime", "OpenCV: Image processed and saved to " + outputPath);
    } else {
        PS_LOG_E("MLRuntime", "OpenCV: Failed to save image");
    }
    return result;
#else
    PS_LOG_E("MLRuntime", "OpenCV: Not available");
    return false;
#endif
}

bool MLRuntime::LoadTFLiteModel(const std::string& modelPath) {
#ifdef PYSTUDIO_HAS_TFLITE
    PS_LOG_I("MLRuntime", "TFLite: Loading model " + modelPath);
    tfliteModel_ = TfLiteModelCreateFromFile(modelPath.c_str());
    if (!tfliteModel_) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to load model");
        return false;
    }
    tfliteOptions_ = TfLiteInterpreterOptionsCreate();
    TfLiteInterpreterOptionsSetNumThreads(tfliteOptions_, 4); // Example: NNAPI / thread count
    
    tfliteInterpreter_ = TfLiteInterpreterCreate(tfliteModel_, tfliteOptions_);
    if (!tfliteInterpreter_) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to create interpreter");
        return false;
    }
    if (TfLiteInterpreterAllocateTensors(tfliteInterpreter_) != kTfLiteOk) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to allocate tensors");
        return false;
    }
    PS_LOG_I("MLRuntime", "TFLite: Model loaded successfully");
    return true;
#else
    PS_LOG_E("MLRuntime", "TFLite: Not available");
    return false;
#endif
}

std::vector<float> MLRuntime::RunTFLiteInference(const std::vector<float>& inputData) {
#ifdef PYSTUDIO_HAS_TFLITE
    if (!tfliteInterpreter_) {
        PS_LOG_E("MLRuntime", "TFLite: Model not loaded!");
        return {};
    }
    PS_LOG_I("MLRuntime", "TFLite: Running inference");

    TfLiteTensor* inputTensor = TfLiteInterpreterGetInputTensor(tfliteInterpreter_, 0);
    if (!inputTensor) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to get input tensor");
        return {};
    }
    
    size_t inputSize = inputData.size() * sizeof(float);
    if (TfLiteTensorCopyFromBuffer(inputTensor, inputData.data(), inputSize) != kTfLiteOk) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to copy input data");
        return {};
    }
    
    if (TfLiteInterpreterInvoke(tfliteInterpreter_) != kTfLiteOk) {
        PS_LOG_E("MLRuntime", "TFLite: Inference failed");
        return {};
    }
    
    const TfLiteTensor* outputTensor = TfLiteInterpreterGetOutputTensor(tfliteInterpreter_, 0);
    if (!outputTensor) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to get output tensor");
        return {};
    }
    
    size_t outFloats = TfLiteTensorByteSize(outputTensor) / sizeof(float);
    std::vector<float> outputData(outFloats);
    if (TfLiteTensorCopyToBuffer(outputTensor, outputData.data(), outFloats * sizeof(float)) != kTfLiteOk) {
        PS_LOG_E("MLRuntime", "TFLite: Failed to copy output data");
        return {};
    }
    
    return outputData;
#else
    PS_LOG_E("MLRuntime", "TFLite: Not available");
    return {};
#endif
}

bool MLRuntime::LoadTorchModel(const std::string& modelPath) {
#ifdef PYSTUDIO_HAS_LIBTORCH
    PS_LOG_I("MLRuntime", "LibTorch: Loading model " + modelPath);
    try {
        torchModule_ = std::make_shared<torch::jit::script::Module>(torch::jit::load(modelPath));
    } catch (const std::exception& e) {
        PS_LOG_E("MLRuntime", "LibTorch: Failed to load model");
        return false;
    }
    PS_LOG_I("MLRuntime", "LibTorch: Model loaded successfully");
    return true;
#else
    PS_LOG_E("MLRuntime", "LibTorch: Not available");
    return false;
#endif
}

std::vector<float> MLRuntime::RunTorchInference(const std::vector<float>& inputData) {
#ifdef PYSTUDIO_HAS_LIBTORCH
    if (!torchModule_) {
        PS_LOG_E("MLRuntime", "LibTorch: Model not loaded!");
        return {};
    }
    PS_LOG_I("MLRuntime", "LibTorch: Running inference");
    
    std::vector<float> outputData;
    try {
        auto input_tensor = torch::from_blob(const_cast<float*>(inputData.data()), {1, static_cast<long>(inputData.size())});
        auto output_tensor = torchModule_->forward({input_tensor}).toTensor();
        const float* out_ptr = output_tensor.data_ptr<float>();
        outputData.assign(out_ptr, out_ptr + output_tensor.numel());
    } catch (const std::exception& e) {
        PS_LOG_E("MLRuntime", "LibTorch: Inference failed");
        return {};
    }
    
    return outputData;
#else
    PS_LOG_E("MLRuntime", "LibTorch: Not available");
    return {};
#endif
}

} // namespace mlruntime
} // namespace pystudio
