import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  // S-2: FS
  readFile(path: String): Promise<String>;
  writeFile(path: String, content: String): Promise<boolean>;

  // S-4: Python Runtime
  executePythonScript(path: String): Promise<String>;
  
  // S-6: LSP
  startLspServer(language: String): Promise<boolean>;
  
  // S-8: Git
  gitInit(path: String): Promise<boolean>;
  gitCommit(path: String, message: String): Promise<boolean>;

  // S-10: Jupyter
  runJupyterCell(notebookId: String, cellId: String, code: String): Promise<String>;

  // S-11: AI
  askAI(prompt: String): Promise<String>;

  // S-12: Marketplace
  installExtension(extensionId: String): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PyStudioBridge');
