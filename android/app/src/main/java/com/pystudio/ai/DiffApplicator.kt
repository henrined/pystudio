package com.pystudio.ai

object DiffApplicator {

    /**
     * S-11.4: Very naive unified diff applicator.
     * In a production app, we would use a robust library like java-diff-utils.
     * This handles replacing chunks of code proposed by the AI using --- a/file +++ b/file.
     */
    fun applyDiff(originalText: String, unifiedDiff: String): String {
        val originalLines = originalText.lines().toMutableList()
        val diffLines = unifiedDiff.lines()

        var currentLineIdx = 0
        val resultLines = mutableListOf<String>()

        // A highly simplified patch applicator assuming exact match of context lines
        for (line in diffLines) {
            when {
                line.startsWith("---") || line.startsWith("+++") -> {
                    // File headers, ignore for simple string patch
                }
                line.startsWith("@@") -> {
                    // Hunk header, e.g., @@ -1,3 +1,4 @@
                    // We parse the starting line of the original text
                    try {
                        val match = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@").find(line)
                        if (match != null) {
                            val startLine = match.groupValues[1].toInt() - 1
                            
                            // Copy lines up to the start of the hunk
                            while (currentLineIdx < startLine && currentLineIdx < originalLines.size) {
                                resultLines.add(originalLines[currentLineIdx])
                                currentLineIdx++
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                line.startsWith("-") -> {
                    // Line removed
                    currentLineIdx++
                }
                line.startsWith("+") -> {
                    // Line added
                    resultLines.add(line.substring(1))
                }
                line.startsWith(" ") -> {
                    // Context line
                    if (currentLineIdx < originalLines.size) {
                        resultLines.add(originalLines[currentLineIdx])
                        currentLineIdx++
                    }
                }
            }
        }
        
        // Add remaining lines
        while (currentLineIdx < originalLines.size) {
            resultLines.add(originalLines[currentLineIdx])
            currentLineIdx++
        }

        return resultLines.joinToString("\n")
    }
}
