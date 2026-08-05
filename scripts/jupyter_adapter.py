import json
import threading
import sys
from ipykernel.inprocess.manager import InProcessKernelManager

class PyStudioJupyterAdapter:
    def __init__(self):
        # Demarrage du kernel in-process (sans ZMQ)
        self.km = InProcessKernelManager()
        self.km.start_kernel()
        self.kc = self.km.client()
        self.kc.start_channels()
        
        self.msg_to_cell = {}
        
        self._running = True
        self._poll_thread = threading.Thread(target=self._poll_channels, daemon=True)
        self._poll_thread.start()

    def _poll_channels(self):
        """Poll IO Pub and Shell channels to get outputs and forward them to Android via stdout JSON"""
        while self._running:
            try:
                # Get message from iopub channel
                msg = self.kc.iopub_channel.get(timeout=0.1)
                self._send_to_android("iopub", msg)
            except Exception:
                pass # Queue empty or timeout

            try:
                # Get message from shell channel (execution replies)
                msg = self.kc.shell_channel.get(timeout=0.1)
                self._send_to_android("shell", msg)
            except Exception:
                pass

    def _send_to_android(self, channel, msg):
        """Encode message to JSON and print to stdout so RunnerService.kt can catch it"""
        try:
            parent_msg_id = msg.get("parent_header", {}).get("msg_id")
            cell_id = self.msg_to_cell.get(parent_msg_id)
            # Pystudio magic prefix to differentiate from normal stdout
            out = json.dumps({"channel": channel, "cell_id": cell_id, "msg": msg})
            sys.stdout.write(f"__PYSTUDIO_JUPYTER__:{out}\n")
            sys.stdout.flush()
        except Exception as e:
            pass

    def execute_cell(self, cell_id, code):
        """Called by Android (via IRunnerService.runString)"""
        msg_id = self.kc.execute(code)
        self.msg_to_cell[msg_id] = cell_id
        return msg_id

    def shutdown(self):
        self._running = False
        self.kc.stop_channels()
        self.km.shutdown_kernel()

# Instance globale
adapter = None

def init_adapter():
    global adapter
    if adapter is None:
        adapter = PyStudioJupyterAdapter()

def execute(cell_id, code):
    if adapter:
        adapter.execute_cell(cell_id, code)

# Appelable depuis Kotlin: 
# runString("import jupyter_adapter; jupyter_adapter.init_adapter()")
# runString("jupyter_adapter.execute('cell_1', 'print(1+1)')")
