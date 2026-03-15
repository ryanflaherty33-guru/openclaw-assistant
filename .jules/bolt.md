## 2025-02-28 - Pre-compiled Regex patterns
**Learning:** Frequent initialization of `Regex("...")` objects inside hot loops or parsing functions (like text-to-speech text normalization or markdown parsing) introduces significant, measurable performance overhead because the regular expression pattern has to be compiled on every invocation. In this Kotlin Android codebase, extracting them to `private val` properties avoids repeated compilation, especially in methods like `stripMarkdownForSpeech` which parses an entire text block multiple times per message string.
**Action:** When working on text parsing features (e.g. ChatMarkdown blocks, TTS filters, Tool output formatting), always ensure `Regex` objects are pre-compiled as top-level file constants, or inside a `companion object` of a class, or as a property within a singleton `object`.

## 2024-05-24 - Hoist Static Collection Allocations in Loop Parsers
**Learning:** Instantiating static data structures like `listOf(...)` inside frequently called parsing methods (such as text chunking) causes redundant memory allocations and garbage collection pressure, leading to hidden CPU overhead.
**Action:** Always hoist static parsing collections (like sentence or comma enders) to `private val` properties at the file or object level to ensure they are created exactly once.
