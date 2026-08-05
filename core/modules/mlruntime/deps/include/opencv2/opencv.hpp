#pragma once
#include <string>

namespace cv {

enum ColorConversionCodes {
    COLOR_BGR2GRAY = 6
};

enum ImreadModes {
    IMREAD_COLOR = 1
};

class Mat {
public:
    Mat();
    Mat(int rows, int cols, int type);
    Mat(const Mat& other);
    Mat& operator=(const Mat& other);
    ~Mat();
    bool empty() const;

    int rows, cols;

    // Pointer to pixel data (stub allocates real memory)
    unsigned char* data;

private:
    bool owns_data_;
};

Mat imread(const std::string& filename, int flags = IMREAD_COLOR);
bool imwrite(const std::string& filename, const Mat& img);
void cvtColor(const Mat& src, Mat& dst, int code);

} // namespace cv
