#include "pyembed.h"
#include <Python.h>
#include <iostream>
#include "pystudio/logger.h"

namespace pystudio {
namespace pyembed {

static OutputCallback g_output_callback = nullptr;

// A custom Python object for intercepting IO
typedef struct {
    PyObject_HEAD
    int is_stderr;
} PyStdOutRedirector;

static PyObject* PyStdOutRedirector_write(PyStdOutRedirector* self, PyObject* args) {
    const char* data;
    Py_ssize_t size;
    if (!PyArg_ParseTuple(args, "s#", &data, &size)) {
        return NULL;
    }
    
    if (g_output_callback && size > 0) {
        g_output_callback(std::string(data, size), self->is_stderr != 0);
    }
    
    return PyLong_FromSsize_t(size);
}

static PyObject* PyStdOutRedirector_flush(PyStdOutRedirector* self, PyObject* Py_UNUSED(ignored)) {
    Py_RETURN_NONE;
}

static PyMethodDef PyStdOutRedirector_methods[] = {
    {"write", (PyCFunction)PyStdOutRedirector_write, METH_VARARGS, "Write data"},
    {"flush", (PyCFunction)PyStdOutRedirector_flush, METH_NOARGS, "Flush data"},
    {NULL, NULL, 0, NULL}
};

static PyTypeObject PyStdOutRedirectorType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "pystudio.StdOutRedirector", /* tp_name */
    sizeof(PyStdOutRedirector),  /* tp_basicsize */
    0,                           /* tp_itemsize */
    0,                           /* tp_dealloc */
    0,                           /* tp_vectorcall_offset */
    0,                           /* tp_getattr */
    0,                           /* tp_setattr */
    0,                           /* tp_as_async */
    0,                           /* tp_repr */
    0,                           /* tp_as_number */
    0,                           /* tp_as_sequence */
    0,                           /* tp_as_mapping */
    0,                           /* tp_hash */
    0,                           /* tp_call */
    0,                           /* tp_str */
    0,                           /* tp_getattro */
    0,                           /* tp_setattro */
    0,                           /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT,          /* tp_flags */
    "Custom stdout/stderr redirector", /* tp_doc */
    0,                           /* tp_traverse */
    0,                           /* tp_clear */
    0,                           /* tp_richcompare */
    0,                           /* tp_weaklistoffset */
    0,                           /* tp_iter */
    0,                           /* tp_iternext */
    PyStdOutRedirector_methods,  /* tp_methods */
    0,                           /* tp_members */
    0,                           /* tp_getset */
    0,                           /* tp_base */
    0,                           /* tp_dict */
    0,                           /* tp_descr_get */
    0,                           /* tp_descr_set */
    0,                           /* tp_dictoffset */
    0,                           /* tp_init */
    0,                           /* tp_alloc */
    PyType_GenericNew,           /* tp_new */
};

PythonEnv::PythonEnv() = default;

PythonEnv::~PythonEnv() {
    Finalize();
}

void PythonEnv::SetOutputCallback(OutputCallback cb) {
    output_callback_ = cb;
    g_output_callback = cb;
}

bool PythonEnv::SetupIOInterceptor() {
    if (PyType_Ready(&PyStdOutRedirectorType) < 0) {
        return false;
    }

    PyObject* sys = PyImport_ImportModule("sys");
    if (!sys) return false;

    // stdout
    PyStdOutRedirector* stdout_obj = PyObject_New(PyStdOutRedirector, &PyStdOutRedirectorType);
    if (stdout_obj) {
        stdout_obj->is_stderr = 0;
        PyObject_SetAttrString(sys, "stdout", (PyObject*)stdout_obj);
        Py_DECREF(stdout_obj);
    }

    // stderr
    PyStdOutRedirector* stderr_obj = PyObject_New(PyStdOutRedirector, &PyStdOutRedirectorType);
    if (stderr_obj) {
        stderr_obj->is_stderr = 1;
        PyObject_SetAttrString(sys, "stderr", (PyObject*)stderr_obj);
        Py_DECREF(stderr_obj);
    }

    Py_DECREF(sys);
    return true;
}

// Helper: narrow to wide string (ASCII paths only — Android paths are ASCII)
static std::wstring to_wstr(const std::string& s) {
    return std::wstring(s.begin(), s.end());
}

// ─── S-2.4: Initialize with full per-project environment config ──────────
bool PythonEnv::Initialize(const EnvConfig& cfg) {
    if (initialized_) {
        return true;
    }

    PyConfig config;
    // SRS REQ-FUNC-0076: use IsolatedConfig — no env vars, no site-packages from host
    PyConfig_InitIsolatedConfig(&config);
    config.isolated = 1;
    config.site_import = cfg.siteImport ? 1 : 0;
    config.write_bytecode = cfg.writeBytecode ? 1 : 0;
    config.buffered_stdio = 0;           // SRS: no buffered stdio on Android
    config.configure_c_stdio = 0;        // SRS: no tty on Android, we redirect ourselves

    std::wstring wHome = to_wstr(cfg.pythonHome);
    PyStatus status = PyConfig_SetString(&config, &config.home, wHome.c_str());
    if (PyStatus_Exception(status)) {
        PS_LOG_E("PythonEnv", "Failed to set PYTHONHOME");
        PyConfig_Clear(&config);
        return false;
    }

    // ── Explicit module search paths (zip-based Android stdlib) ──────────
    // SRS REQ-FUNC-0076: module_search_paths = [stdlib.zip, site-packages]
    config.module_search_paths_set = 1;

    // 1. stdlib zip
    std::wstring wZip = to_wstr(cfg.stdlibZipPath);
    status = PyWideStringList_Append(&config.module_search_paths, wZip.c_str());
    if (PyStatus_Exception(status)) {
        PS_LOG_E("PythonEnv", "Failed to add stdlib zip to search paths");
        PyConfig_Clear(&config);
        return false;
    }

    // 2. lib-dynload (C extension modules)
    std::wstring wDynload = to_wstr(cfg.dynloadPath);
    status = PyWideStringList_Append(&config.module_search_paths, wDynload.c_str());
    if (PyStatus_Exception(status)) {
        PS_LOG_E("PythonEnv", "Failed to add lib-dynload to search paths");
        PyConfig_Clear(&config);
        return false;
    }

    // 3. Per-project site-packages (S-2.4: venv emulation)
    if (!cfg.sitePackagesPath.empty()) {
        std::wstring wSite = to_wstr(cfg.sitePackagesPath);
        status = PyWideStringList_Append(&config.module_search_paths, wSite.c_str());
        if (PyStatus_Exception(status)) {
            PS_LOG_E("PythonEnv", "Failed to add site-packages to search paths");
            PyConfig_Clear(&config);
            return false;
        }
        PS_LOG_I("PythonEnv", "Venv site-packages: " + cfg.sitePackagesPath);
    }

    status = Py_InitializeFromConfig(&config);
    if (PyStatus_Exception(status)) {
        PS_LOG_E("PythonEnv", "Failed to initialize Python interpreter");
        PyConfig_Clear(&config);
        return false;
    }

    PyConfig_Clear(&config);

    if (!SetupIOInterceptor()) {
        PS_LOG_W("PythonEnv", "Failed to setup IO interceptor");
    }

    initialized_ = true;
    PS_LOG_I("PythonEnv", "Python interpreter initialized successfully");
    return true;
}

// ─── Simple overload: backward-compatible, derives EnvConfig from pythonHome ─
bool PythonEnv::Initialize(const std::string& pythonHome) {
    EnvConfig cfg;
    cfg.pythonHome       = pythonHome;
    cfg.stdlibZipPath    = pythonHome + "/lib/python314.zip";
    cfg.dynloadPath      = pythonHome + "/lib/lib-dynload";
    cfg.sitePackagesPath = "";  // no venv
    cfg.writeBytecode    = false;
    cfg.siteImport       = false;
    return Initialize(cfg);
}

void PythonEnv::Finalize() {
    if (initialized_) {
        Py_FinalizeEx();
        initialized_ = false;
        PS_LOG_I("PythonEnv", "Python interpreter finalized");
    }
}

bool PythonEnv::RunString(const std::string& code) {
    if (!initialized_) {
        PS_LOG_E("PythonEnv", "Cannot run code: Python is not initialized");
        return false;
    }

    int ret = PyRun_SimpleString(code.c_str());
    return ret == 0;
}

bool PythonEnv::RunFile(const std::string& filepath) {
    if (!initialized_) {
        PS_LOG_E("PythonEnv", "Cannot run file: Python is not initialized");
        return false;
    }

    FILE* fp = fopen(filepath.c_str(), "r");
    if (!fp) {
        PS_LOG_E("PythonEnv", "Failed to open Python script file");
        return false;
    }

    int ret = PyRun_SimpleFile(fp, filepath.c_str());
    fclose(fp);

    return ret == 0;
}

void PythonEnv::ForceGcCollect(int& collected, int& uncollectable) {
    collected = 0;
    uncollectable = 0;
    if (!initialized_) return;
    
    PyGILState_STATE gstate = PyGILState_Ensure();
    PyObject* gc_module = PyImport_ImportModule("gc");
    if (gc_module) {
        PyObject* result = PyObject_CallMethod(gc_module, "collect", NULL);
        if (result) {
            collected = (int)PyLong_AsLong(result);
            Py_DECREF(result);
        }
        PyObject* garbage = PyObject_GetAttrString(gc_module, "garbage");
        if (garbage) {
            uncollectable = (int)PyList_Size(garbage);
            Py_DECREF(garbage);
        }
        Py_DECREF(gc_module);
    }
    PyGILState_Release(gstate);
}

} // namespace pyembed
} // namespace pystudio
