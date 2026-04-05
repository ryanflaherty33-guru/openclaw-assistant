1. **Explore the upstream repo**
   - Use `git clone` or fetch to investigate recent commits in `apps/android`.
   - Find candidate commits for porting (e.g., bug fixes, memory leaks).
   - *Exploration complete*.

2. **Choose a suitable improvement**
   - Identified upstream commit `2909d8cd12` ("Android: fix Bitmap memory leaks in CanvasController snapshots").
   - This prevents memory leaks caused by not recycling Bitmaps (`bmp` and `scaled`) during `snapshotPngBase64()` and `snapshotBase64()` in `CanvasController.kt`.
   - Verified that `CanvasController.kt` in this repo has the exact same leak.

3. **Port the improvement**
   - Modify `app/src/main/java/com/openclaw/assistant/node/CanvasController.kt`.
   - Update `snapshotPngBase64()` and `snapshotBase64()` to wrap operations in `try...finally` blocks, ensuring `bmp` and `scaled` are correctly recycled if they differ.
   - Use `replace_with_git_merge_diff` to modify the file.

4. **Run Native Validation Commands**
   - Run `app:lintStandardDebug` and `app:testStandardDebugUnitTest` with the `./gradlew` command to verify compilation and prevent regressions.

5. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
   - Run `pre_commit_instructions`.

6. **Submit PR**
   - Use the `submit` tool to create a PR named `🔄 Upstream Parity: Fix Bitmap memory leaks in CanvasController snapshots` detailing the source, why, adaptation, and verification.
