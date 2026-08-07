#pragma once

#include <string>
#include <vector>
#include <memory>
#ifdef HAS_LIBGIT2
#include <git2.h>
#else
struct git_repository;
#endif

namespace pystudio {
namespace gitengine {

struct GitStatus {
    std::string branchName;
    int ahead;
    int behind;
    std::vector<std::string> modifiedFiles;
    std::vector<std::string> untrackedFiles;
    std::vector<std::string> stagedFiles;
    std::vector<std::string> conflictedFiles;
};

struct CommitInfo {
    std::string oid;
    std::string author;
    std::string email;
    std::string message;
    int64_t timestamp;
};

class GitEngine {
public:
    GitEngine();
    ~GitEngine();

    // S-6.1 / S-6.2: libgit2 wrapper methods
    bool Clone(const std::string& url, const std::string& destPath, const std::string& username = "", const std::string& token = "");
    bool Open(const std::string& repoPath);
    
    // S-6.3.2: status
    GitStatus GetStatus();
    
    // S-6.3.3: stage / commit
    bool StageFile(const std::string& path);
    bool UnstageFile(const std::string& path);
    bool Commit(const std::string& message, const std::string& authorName, const std::string& authorEmail);
    
    // S-6.3.4: branches
    bool CreateBranch(const std::string& name);
    bool CheckoutBranch(const std::string& name);
    bool DeleteBranch(const std::string& name);
    std::vector<std::string> ListBranches();
    
    // S-6.3.5: merge
    bool Merge(const std::string& sourceBranch);
    bool Rebase(const std::string& targetBranch);
    
    // S-6.3.7: history & diff
    std::vector<CommitInfo> GetLog(int maxCount);
    std::string GetDiff(const std::string& filePath);
    
    // S-6.3.6: push / pull
    bool Push(const std::string& remoteName, const std::string& username, const std::string& token);
    bool Pull(const std::string& remoteName, const std::string& username, const std::string& token);

private:
    git_repository* repo_;
};

} // namespace gitengine
} // namespace pystudio
