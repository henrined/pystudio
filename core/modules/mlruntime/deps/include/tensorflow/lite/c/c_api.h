#pragma once
#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum { kTfLiteOk = 0, kTfLiteError = 1 } TfLiteStatus;
typedef struct TfLiteModel TfLiteModel;
typedef struct TfLiteInterpreterOptions TfLiteInterpreterOptions;
typedef struct TfLiteInterpreter TfLiteInterpreter;
typedef struct TfLiteTensor TfLiteTensor;

TfLiteModel* TfLiteModelCreateFromFile(const char* model_path);
void TfLiteModelDelete(TfLiteModel* model);

TfLiteInterpreterOptions* TfLiteInterpreterOptionsCreate();
void TfLiteInterpreterOptionsDelete(TfLiteInterpreterOptions* options);
void TfLiteInterpreterOptionsSetNumThreads(TfLiteInterpreterOptions* options, int32_t num_threads);

TfLiteInterpreter* TfLiteInterpreterCreate(const TfLiteModel* model, const TfLiteInterpreterOptions* optional_options);
void TfLiteInterpreterDelete(TfLiteInterpreter* interpreter);

TfLiteStatus TfLiteInterpreterAllocateTensors(TfLiteInterpreter* interpreter);
TfLiteStatus TfLiteInterpreterInvoke(TfLiteInterpreter* interpreter);

TfLiteTensor* TfLiteInterpreterGetInputTensor(const TfLiteInterpreter* interpreter, int32_t input_index);
const TfLiteTensor* TfLiteInterpreterGetOutputTensor(const TfLiteInterpreter* interpreter, int32_t output_index);

TfLiteStatus TfLiteTensorCopyFromBuffer(TfLiteTensor* tensor, const void* input_data, size_t input_data_size);
TfLiteStatus TfLiteTensorCopyToBuffer(const TfLiteTensor* tensor, void* output_data, size_t output_data_size);

size_t TfLiteTensorByteSize(const TfLiteTensor* tensor);

#ifdef __cplusplus
}
#endif
