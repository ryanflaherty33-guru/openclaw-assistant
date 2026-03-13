## 2025-02-17 - Added contentDescription to visibility toggles
**Learning:** In settings and input forms, icon-only buttons that toggle visibility (e.g., password visibility) often use `contentDescription = null`. This prevents screen readers from announcing the button's action, creating a critical accessibility barrier for visually impaired users attempting to toggle sensitive information.
**Action:** When creating or reviewing UI components that feature toggles or icon-only actions, explicitly check for and provide dynamic `contentDescription` attributes that describe the resulting action based on the current state.

## 2025-02-16 - Replace plain text icons with semantic Icons in Compose
**Learning:** Using simple string characters (like "×") as button labels inside `IconButton` leads to confusing screen reader announcements (e.g., "times" or "cross") instead of the actual action ("Close" or "Delete"). This negatively affects accessibility.
**Action:** Always replace standalone text characters used as interactive visual symbols with standard Compose `Icon` components (e.g., `Icons.Default.Close`) alongside an explicit `contentDescription` (e.g., `stringResource(R.string.close)` or `delete`). This ensures the screen reader announces the correct semantic action.
