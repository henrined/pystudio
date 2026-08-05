#include "gitengine.h"
#include "pystudio/logger.h"
#include <git2.h>
#include <iostream>
#include <stdexcept>

namespace pystudio {
namespace gitengine {

static int credentials_cb(git_cred **out, const char *url, const char *username_from_url, unsigned int allowed_types, void *payload) {
    auto* creds = static_cast<std::pair<std::string, std::string>*>(payload);
    if (allowed_types & GIT_CREDTYPE_USERPASS_PLAINTEXT) {
        return git_cred_userpass_plaintext_new(out, creds->first.c_str(), creds->second.c_str());
    }
    return -1;
}

GitEngine::GitEngine() : repo_(nullptr) {
    git_libgit2_init();
    PS_LOG_I("GitEngine", "GitEngine initialized");
}

GitEngine::~GitEngine() {
    if (repo_) {
        git_repository_free(repo_);
    }
    git_libgit2_shutdown();
}

bool GitEngine::Clone(const std::string& url, const std::string& destPath, const std::string& username, const std::string& token) {
    PS_LOG_I("GitEngine", "Cloning " + url + " to " + destPath);
    git_clone_options clone_opts = GIT_CLONE_OPTIONS_INIT;
    
    std::pair<std::string, std::string> creds = {username, token};
    if (!username.empty() || !token.empty()) {
        clone_opts.fetch_opts.callbacks.credentials = credentials_cb;
        clone_opts.fetch_opts.callbacks.payload = &creds;
    }
    
    int error = git_clone(&repo_, url.c_str(), destPath.c_str(), &clone_opts);
    if (error < 0) {
        const git_error *e = git_error_last();
        PS_LOG_E("GitEngine", "Error cloning: " + std::string(e ? e->message : "unknown"));
        return false;
    }
    return true;
}

bool GitEngine::Open(const std::string& repoPath) {
    PS_LOG_I("GitEngine", "Opening repo at " + repoPath);
    int error = git_repository_open(&repo_, repoPath.c_str());
    if (error < 0) {
        return false;
    }
    return true;
}

GitStatus GitEngine::GetStatus() {
    GitStatus status;
    if (!repo_) return status;

    git_reference* head = nullptr;
    if (git_repository_head(&head, repo_) == 0) {
        status.branchName = git_reference_shorthand(head);
        git_reference_free(head);
    }
    status.ahead = 0;
    status.behind = 0;

    git_status_options opts = GIT_STATUS_OPTIONS_INIT;
    opts.show = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    opts.flags = GIT_STATUS_OPT_INCLUDE_UNTRACKED | GIT_STATUS_OPT_RENAMES_HEAD_TO_INDEX | GIT_STATUS_OPT_SORT_CASE_SENSITIVELY;

    git_status_list *statuses = nullptr;
    if (git_status_list_new(&statuses, repo_, &opts) == 0) {
        size_t count = git_status_list_entrycount(statuses);
        for (size_t i = 0; i < count; ++i) {
            const git_status_entry *s = git_status_byindex(statuses, i);
            if (s->status & GIT_STATUS_WT_NEW) {
                status.untrackedFiles.push_back(s->index_to_workdir->new_file.path);
            }
            if (s->status & GIT_STATUS_WT_MODIFIED || s->status & GIT_STATUS_WT_DELETED) {
                status.modifiedFiles.push_back(s->index_to_workdir->new_file.path);
            }
            if (s->status & GIT_STATUS_INDEX_MODIFIED || s->status & GIT_STATUS_INDEX_NEW || s->status & GIT_STATUS_INDEX_DELETED) {
                status.stagedFiles.push_back(s->head_to_index->new_file.path);
            }
            if (s->status & GIT_STATUS_CONFLICTED) {
                status.conflictedFiles.push_back(s->head_to_index->new_file.path);
            }
        }
        git_status_list_free(statuses);
    }

    return status;
}

bool GitEngine::StageFile(const std::string& path) {
    if (!repo_) return false;
    git_index *index = nullptr;
    if (git_repository_index(&index, repo_) != 0) return false;
    
    int error = git_index_add_bypath(index, path.c_str());
    if (error == 0) {
        error = git_index_write(index);
    }
    git_index_free(index);
    return error == 0;
}

bool GitEngine::UnstageFile(const std::string& path) {
    if (!repo_) return false;
    git_reference *head = nullptr;
    git_object *head_commit = nullptr;
    if (git_repository_head(&head, repo_) == 0) {
        git_reference_peel(&head_commit, head, GIT_OBJECT_COMMIT);
        git_reference_free(head);
    }
    
    char* paths[] = { const_cast<char*>(path.c_str()) };
    git_strarray pathspec = { paths, 1 };
    
    int error = git_reset_default(repo_, head_commit, &pathspec);
    if (head_commit) git_object_free(head_commit);
    
    return error == 0;
}

bool GitEngine::Commit(const std::string& message, const std::string& authorName, const std::string& authorEmail) {
    if (!repo_) return false;
    
    git_oid tree_id, commit_id;
    git_tree *tree = nullptr;
    git_index *index = nullptr;
    git_signature *signature = nullptr;
    
    if (git_repository_index(&index, repo_) != 0) return false;
    if (git_index_write_tree(&tree_id, index) != 0) {
        git_index_free(index);
        return false;
    }
    git_index_free(index);
    
    if (git_tree_lookup(&tree, repo_, &tree_id) != 0) return false;
    
    if (git_signature_now(&signature, authorName.c_str(), authorEmail.c_str()) != 0) {
        git_tree_free(tree);
        return false;
    }
    
    git_oid parent_id;
    git_commit *parent = nullptr;
    int parent_count = 0;
    const git_commit *parents[1] = {nullptr};
    
    if (git_reference_name_to_id(&parent_id, repo_, "HEAD") == 0) {
        if (git_commit_lookup(&parent, repo_, &parent_id) == 0) {
            parents[0] = parent;
            parent_count = 1;
        }
    }
    
    int error = git_commit_create_v(
        &commit_id, repo_, "HEAD", signature, signature,
        nullptr, message.c_str(), tree, parent_count, parents[0]
    );
    
    git_signature_free(signature);
    git_tree_free(tree);
    if (parent) git_commit_free(parent);
    
    return error == 0;
}

bool GitEngine::CreateBranch(const std::string& name) {
    if (!repo_) return false;
    git_object *target = nullptr;
    git_reference *branch = nullptr;
    
    if (git_revparse_single(&target, repo_, "HEAD") != 0) return false;
    
    git_commit *commit = nullptr;
    if (git_commit_lookup(&commit, repo_, git_object_id(target)) != 0) {
        git_object_free(target);
        return false;
    }
    
    int error = git_branch_create(&branch, repo_, name.c_str(), commit, 0);
    
    git_commit_free(commit);
    git_object_free(target);
    if (branch) git_reference_free(branch);
    
    return error == 0;
}

bool GitEngine::CheckoutBranch(const std::string& name) {
    if (!repo_) return false;
    git_object *treeish = nullptr;
    git_checkout_options opts = GIT_CHECKOUT_OPTIONS_INIT;
    opts.checkout_strategy = GIT_CHECKOUT_SAFE;
    
    std::string refName = "refs/heads/" + name;
    if (git_revparse_single(&treeish, repo_, refName.c_str()) != 0) return false;
    
    int error = git_checkout_tree(repo_, treeish, &opts);
    if (error == 0) {
        error = git_repository_set_head(repo_, refName.c_str());
    }
    git_object_free(treeish);
    return error == 0;
}

bool GitEngine::DeleteBranch(const std::string& name) {
    if (!repo_) return false;
    git_reference *branch = nullptr;
    if (git_branch_lookup(&branch, repo_, name.c_str(), GIT_BRANCH_LOCAL) != 0) return false;
    
    int error = git_branch_delete(branch);
    git_reference_free(branch);
    return error == 0;
}

std::vector<std::string> GitEngine::ListBranches() {
    std::vector<std::string> branches;
    if (!repo_) return branches;
    
    git_branch_iterator *iter = nullptr;
    if (git_branch_iterator_new(&iter, repo_, GIT_BRANCH_LOCAL) != 0) return branches;
    
    git_reference *ref = nullptr;
    git_branch_t type;
    while (git_branch_next(&ref, &type, iter) == 0) {
        const char *name = nullptr;
        if (git_branch_name(&name, ref) == 0 && name) {
            branches.push_back(name);
        }
        git_reference_free(ref);
    }
    git_branch_iterator_free(iter);
    return branches;
}

bool GitEngine::Merge(const std::string& sourceBranch) {
    // S-6.3.5 Merge implementation
    if (!repo_) return false;
    git_reference *branch_ref = nullptr;
    std::string refName = "refs/heads/" + sourceBranch;
    if (git_reference_lookup(&branch_ref, repo_, refName.c_str()) != 0) return false;
    
    git_annotated_commit *their_head = nullptr;
    if (git_annotated_commit_from_ref(&their_head, repo_, branch_ref) != 0) {
        git_reference_free(branch_ref);
        return false;
    }
    
    git_merge_options merge_opts = GIT_MERGE_OPTIONS_INIT;
    git_checkout_options checkout_opts = GIT_CHECKOUT_OPTIONS_INIT;
    checkout_opts.checkout_strategy = GIT_CHECKOUT_SAFE | GIT_CHECKOUT_ALLOW_CONFLICTS;
    
    const git_annotated_commit *heads[] = { their_head };
    int error = git_merge(repo_, heads, 1, &merge_opts, &checkout_opts);
    
    git_annotated_commit_free(their_head);
    git_reference_free(branch_ref);
    return error == 0;
}

bool GitEngine::Rebase(const std::string& targetBranch) {
    if (!repo_) return false;
    
    git_reference *branch_ref = nullptr;
    std::string refName = "refs/heads/" + targetBranch;
    if (git_reference_lookup(&branch_ref, repo_, refName.c_str()) != 0) return false;
    
    git_annotated_commit *branch_commit = nullptr;
    if (git_annotated_commit_from_ref(&branch_commit, repo_, branch_ref) != 0) {
        git_reference_free(branch_ref);
        return false;
    }
    
    git_rebase_options rebase_opts = GIT_REBASE_OPTIONS_INIT;
    git_rebase *rebase = nullptr;
    if (git_rebase_init(&rebase, repo_, nullptr, branch_commit, nullptr, &rebase_opts) != 0) {
        git_annotated_commit_free(branch_commit);
        git_reference_free(branch_ref);
        return false;
    }
    
    git_rebase_operation *operation = nullptr;
    bool conflict = false;
    while (git_rebase_next(&operation, rebase) == 0) {
        git_oid commit_id;
        if (git_rebase_commit(&commit_id, rebase, nullptr, nullptr, nullptr, nullptr) != 0) {
            conflict = true;
            PS_LOG_E("GitEngine", "Rebase conflict detected or error during commit");
            break;
        }
    }
    
    bool result = true;
    if (conflict) {
        result = false;
    } else {
        if (git_rebase_finish(rebase, nullptr) != 0) {
            result = false;
        }
    }
    
    git_rebase_free(rebase);
    git_annotated_commit_free(branch_commit);
    git_reference_free(branch_ref);
    return result;
}

std::vector<CommitInfo> GitEngine::GetLog(int maxCount) {
    std::vector<CommitInfo> commits;
    if (!repo_) return commits;
    
    git_revwalk *walker = nullptr;
    if (git_revwalk_new(&walker, repo_) != 0) return commits;
    
    git_revwalk_sorting(walker, GIT_SORT_TIME);
    if (git_revwalk_push_head(walker) != 0) {
        git_revwalk_free(walker);
        return commits;
    }
    
    git_oid oid;
    int count = 0;
    while (git_revwalk_next(&oid, walker) == 0 && (maxCount <= 0 || count < maxCount)) {
        git_commit *commit = nullptr;
        if (git_commit_lookup(&commit, repo_, &oid) == 0) {
            CommitInfo info;
            char oid_str[GIT_OID_HEXSZ + 1];
            git_oid_tostr(oid_str, sizeof(oid_str), &oid);
            info.oid = oid_str;
            
            const git_signature *author = git_commit_author(commit);
            if (author) {
                info.author = author->name ? author->name : "";
                info.email = author->email ? author->email : "";
                info.timestamp = author->when.time;
            } else {
                info.timestamp = git_commit_time(commit);
            }
            
            const char *msg = git_commit_message(commit);
            info.message = msg ? msg : "";
            
            commits.push_back(info);
            git_commit_free(commit);
        }
        count++;
    }
    
    git_revwalk_free(walker);
    return commits;
}

static int diff_print_cb(const git_diff_delta *delta, const git_diff_hunk *hunk, const git_diff_line *line, void *payload) {
    std::string *output = static_cast<std::string*>(payload);
    if (line->origin == GIT_DIFF_LINE_CONTEXT || line->origin == GIT_DIFF_LINE_ADDITION || line->origin == GIT_DIFF_LINE_DELETION) {
        output->push_back(line->origin);
    }
    output->append(line->content, line->content_len);
    return 0;
}

std::string GitEngine::GetDiff(const std::string& filePath) {
    if (!repo_) return "";
    
    git_index *index = nullptr;
    if (git_repository_index(&index, repo_) != 0) return "";
    
    git_diff_options diff_opts = GIT_DIFF_OPTIONS_INIT;
    char* path_array[1];
    git_strarray pathspec = { nullptr, 0 };
    if (!filePath.empty()) {
        path_array[0] = const_cast<char*>(filePath.c_str());
        pathspec.strings = path_array;
        pathspec.count = 1;
        diff_opts.pathspec = pathspec;
    }
    
    git_diff *diff = nullptr;
    if (git_diff_index_to_workdir(&diff, repo_, index, &diff_opts) != 0) {
        git_index_free(index);
        return "";
    }
    
    std::string diffOutput;
    git_diff_print(diff, GIT_DIFF_FORMAT_PATCH, diff_print_cb, &diffOutput);
    
    git_diff_free(diff);
    git_index_free(index);
    return diffOutput;
}

bool GitEngine::Push(const std::string& remoteName, const std::string& username, const std::string& token) {
    if (!repo_) return false;
    git_remote *remote = nullptr;
    if (git_remote_lookup(&remote, repo_, remoteName.c_str()) != 0) return false;
    
    git_push_options options = GIT_PUSH_OPTIONS_INIT;
    std::pair<std::string, std::string> creds = {username, token};
    if (!username.empty() || !token.empty()) {
        options.callbacks.credentials = credentials_cb;
        options.callbacks.payload = &creds;
    }
    
    // push head
    git_reference *head = nullptr;
    if (git_repository_head(&head, repo_) != 0) {
        git_remote_free(remote);
        return false;
    }
    
    std::string refspec = std::string("refs/heads/") + git_reference_shorthand(head) + ":refs/heads/" + git_reference_shorthand(head);
    git_reference_free(head);
    
    char* refspecs[] = { const_cast<char*>(refspec.c_str()) };
    git_strarray array = { refspecs, 1 };
    
    int error = git_remote_push(remote, &array, &options);
    git_remote_free(remote);
    return error == 0;
}

bool GitEngine::Pull(const std::string& remoteName, const std::string& username, const std::string& token) {
    if (!repo_) return false;
    git_remote *remote = nullptr;
    if (git_remote_lookup(&remote, repo_, remoteName.c_str()) != 0) return false;
    
    git_fetch_options fetch_opts = GIT_FETCH_OPTIONS_INIT;
    std::pair<std::string, std::string> creds = {username, token};
    if (!username.empty() || !token.empty()) {
        fetch_opts.callbacks.credentials = credentials_cb;
        fetch_opts.callbacks.payload = &creds;
    }
    
    int error = git_remote_fetch(remote, nullptr, &fetch_opts, nullptr);
    git_remote_free(remote);
    
    if (error != 0) return false;
    
    // Simplistic pull: just merge FETCH_HEAD after fetch
    git_oid fetch_head_id;
    if (git_reference_name_to_id(&fetch_head_id, repo_, "FETCH_HEAD") != 0) return false;
    
    git_annotated_commit *fetch_head_commit = nullptr;
    if (git_annotated_commit_lookup(&fetch_head_commit, repo_, &fetch_head_id) != 0) return false;
    
    git_merge_options merge_opts = GIT_MERGE_OPTIONS_INIT;
    git_checkout_options checkout_opts = GIT_CHECKOUT_OPTIONS_INIT;
    checkout_opts.checkout_strategy = GIT_CHECKOUT_SAFE | GIT_CHECKOUT_ALLOW_CONFLICTS;
    
    const git_annotated_commit *heads[] = { fetch_head_commit };
    error = git_merge(repo_, heads, 1, &merge_opts, &checkout_opts);
    
    git_annotated_commit_free(fetch_head_commit);
    return error == 0;
}

} // namespace gitengine
} // namespace pystudio
