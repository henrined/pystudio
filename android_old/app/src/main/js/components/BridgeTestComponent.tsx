import React, { useEffect, useState } from 'react';
import { View, Text, Button } from 'react-native';
import PyStudioBridge from '../specs/NativePyStudioBridge';

export const BridgeTestComponent = () => {
  const [result, setResult] = useState<string>('Ready');

  const testAI = async () => {
    try {
      const response = await PyStudioBridge.askAI('How to start?');
      setResult('AI Response: ' + response);
    } catch (e) {
      setResult('Error: ' + e);
    }
  };

  const testPython = async () => {
    try {
      const response = await PyStudioBridge.executePythonScript('/main.py');
      setResult('Python: ' + response);
    } catch (e) {
      setResult('Error: ' + e);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <Text style={{ fontSize: 20, marginBottom: 20 }}>TurboModule Bridge Test</Text>
      <Button title="Test AI Service" onPress={testAI} />
      <View style={{ height: 10 }} />
      <Button title="Test Python Script" onPress={testPython} />
      <Text style={{ marginTop: 20, color: 'blue' }}>{result}</Text>
    </View>
  );
};
