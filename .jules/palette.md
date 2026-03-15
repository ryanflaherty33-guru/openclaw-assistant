## 2025-02-13 - Dynamic Content Descriptions for Multi-state Buttons
**Learning:** Hardcoded accessibility descriptions (`contentDescription`) on multi-purpose Compose elements (like a FAB that acts as "Send", "Stop", or "Mic" depending on state) will cause screen readers to announce misleading actions.
**Action:** When evaluating or creating multi-state interactive icons or buttons in Compose, always ensure the `contentDescription` string is computed dynamically using the same state rules as the `imageVector` or `onClick` handler.
