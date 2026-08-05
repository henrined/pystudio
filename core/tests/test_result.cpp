#include <gtest/gtest.h>
#include "pystudio/core.h"

namespace pystudio {

TEST(ResultTest, OkIsOk) {
  auto r = Result::Ok();
  EXPECT_TRUE(r.ok());
  EXPECT_EQ(r.status, Status::kOk);
  EXPECT_TRUE(r.message.empty());
}

TEST(ResultTest, ErrIsNotOk) {
  auto r = Result::Err("something went wrong");
  EXPECT_FALSE(r.ok());
  EXPECT_EQ(r.status, Status::kError);
  EXPECT_EQ(r.message, "something went wrong");
}

TEST(ResultTest, CancelledIsNotOk) {
  auto r = Result::Cancelled("user cancelled");
  EXPECT_FALSE(r.ok());
  EXPECT_EQ(r.status, Status::kCancelled);
}

} // namespace pystudio
