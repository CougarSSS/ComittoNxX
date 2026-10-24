# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Note (2026-09-16)**: This is a corrected copy for the `full-local-features` branch (current daily-driver source, cloned from `https://github.com/CougarSSS/ComittoNxX.git`). The original version of this file (still sitting in the old, no-longer-used `C:\Dev\ComittoNxX\` tree on the Windows dev machine) had a stale SMB-layer description; see the "File access" paragraph below for the corrected version. Everything else carried over unchanged and is still accurate.

## Project overview

ComittoNxX is an Android comic/e-book viewer (image archives, PDF, EPUB, text/Aozora Bunko) written in legacy-style Java (package root `src.comitton.*`, app id `jp.dip.muracoro.comittonx`). It reads files from local storage, SMB shares, or SAF/picker-based cloud storage providers. It is a fork descended from ComittoN/ComittoNxA/ComittoNxD; upstream is `comittonxxa2/ComittoNxX` on GitHub (Issues are disabled there — upstream contributions go via Pull Request only).

## Build & run

Requires JDK 17, Android SDK/NDK (pinned `ndkVersion` in `app/build.gradle`), and for native deps (dav1d/libavif/libjxl/unrar): Git, Ninja, Meson, pkg-config, NASM.

```bash
./gradlew :app:assembleDebug          # build debug APK (arm64-v8a, debuggable)
./gradlew :app:installDebug           # build + install to connected/authorized device
./gradlew :app:compileDebugJavaWithJavac   # fast Java-only compile check, no native/APK step
```

There is no automated test suite — verification is manual build + install + on-device testing (screenshots/logcat).

`buildTypes` (no product flavors are defined, so the type name is the full variant name):
| type | ABI | notes |
|---|---|---|
| `debug` (default) | arm64-v8a | debuggable, `.debug` app id suffix |
| `debug32` | armeabi-v7a | debuggable, 32-bit |
| `arm64` / `arm32` / `x64` / `x86` | per-ABI | minified/shrunk, unsigned |
| `release` | arm64-v8a + armeabi-v7a | minified, requires a real signing config (see below) |

Release signing: create `signingConfigs/release.gradle` at the repo root (not present by default) defining a `signingConfigs { release { ... } }` block per the README, then uncomment the `signingConfig signingConfigs.release` lines in `app/build.gradle`.

**adb gotcha**: if `gradlew installDebug` reports "No online devices found" while `adb devices` shows the device as online, there are likely two different `adb` binaries on PATH (e.g. a standalone copy and the one under the Android SDK's `platform-tools`) running separate servers. Run `adb kill-server && adb start-server` to consolidate, or invoke the SDK's own `platform-tools/adb` explicitly.

**Local build.gradle adjustments (this branch only, gitignored files needed)**: see `ComittoNxX_ArchLinux移行_引き継ぎ書.md` in this same folder for the compileSdk/androidx-version/signing workarounds this branch currently carries, and whether they're still needed on this machine's toolchain.

## Architecture

**UI is not RecyclerView/Fragment-based.** The main file-browser screen (`FileSelectActivity`) hosts a `SurfaceView` (`ListScreenView`) that self-draws everything on a `Canvas` — file lists, the bottom selector bar, titles, toolbars. Touch handling is manual hit-testing (`findAreaType`/`areaPosX`/`areaPosY`) dispatched to per-area classes, not standard Android click listeners.

**The bottom tab bar is a hardcoded N-way switch, not a dynamic list.** Each tab corresponds to a `RecordList.TYPE_*` constant (`TYPE_DIRECTORY`, `TYPE_SERVER`, `TYPE_BOOKMARK`, `TYPE_HISTORY`, `TYPE_MENU`, `TYPE_FILELIST`, `TYPE_SEARCH`, ...). Adding a new tab means touching all of: `SelectorArea` (icon/label arrays, `BUTTON_MAXNUM`), `ListScreenView` (constructor, `draw()`, `findAreaType()`, `updateLayout()`, `areaPosX/Y()`, `getListByType()`, `setDrawColor/Info()`, `setListNoticeListener()`, `updateTitleSortName()`), `SetRecorderActivity.getListTypes()` (which tabs are enabled), and `FileSelectActivity`'s touch-routing `else if (mTouchArea == AREATYPE_*)` chain and `onItemClick()`. A tab backed by data that isn't persisted to disk (e.g. transient search results) must also be special-cased in `RecordList.checkModified()` and `RecordList.load(list, listtype)` (both index a `FILENAME[]` array by type and will throw `ArrayIndexOutOfBoundsException` otherwise).

**The "menu" tab** is a static list built from parallel arrays in `RecordList` (`MENU_TITLE_ID`/`MENU_ITEM`/`MENU_IMAGE`), dispatched through `FileSelectActivity.onOptionsItemSelected(int id)` using `DEF.MENU_*` id constants.

**File access is abstracted through `FileAccess`**, branching on `DEF.ACCESS_TYPE_{LOCAL,SMB,SAF,PICKER}`. SMB access is **single-library** (`org.codelibs:jcifs:2.1.40`, plain `jcifs.*` package namespace) via `SmbFileAccess`/`FileAccess` — there is no second/fallback SMB client. (An earlier Windows-side dev copy of this app used a dual-library setup — SMBJ as primary with jcifs-ng as fallback, and a *different*, newer `org.codelibs:jcifs:3.0.2` build whose config keys lived under `jcifs.client.*` instead of the standard `jcifs.smb.client.*` — but that was abandoned when upstream did its own SMB-layer rewrite and downgraded to `2.1.40`; this branch follows upstream's current jcifs-only setup.) With this `2.1.40` build, config properties use the **standard** `jcifs.smb.client.*` namespace (verified by decompiling the jar's `PropertyConfiguration.class`) — using any other prefix silently no-ops instead of erroring. `SmbFileAccess.ensureSingletonContextInitialized()` must run before *any* code path calls `SingletonContext.getInstance()` (including ones that bypass `SmbFileAccess.smbFile()`, e.g. archive-opening code in `ImageManager`/`EpubWebViewActivity`) — jcifs's `SingletonContext` is init-once, so whichever caller runs first locks in its config for the rest of the process.

**Read/bookmark history is tracked by two independent, differently-keyed mechanisms**:
- Per-file last-read page + read/unread state: `SharedPreferences`, keyed by `DEF.createUrl(fullPath, user, pass)` (an exact, credential-embedded URL string). For SMB files, this local cache is now also mirrored to a small external sync server (see `bookmark-sync-server_ClaudeCode_引き継ぎ書.md`) so read position carries over across devices — pulled synchronously right before a file opens, pushed best-effort on pause/close (`ImageActivity.pushReadPositionToServer`/`ReadPositionSyncClient`).
- Bookmarks/history/recent-directories/recent-servers: tab-separated `.dat` files under `<baseDir>/conf/` via `RecordList.load/add/update`, keyed by `(server, path, file)` exact match via `RecordItem.equals()`. Named bookmarks on SMB files are also synced the same way (`BookmarkSyncClient`).

Both are exact-match by default (extension- and directory-sensitive). Fallback helpers exist to bridge this (`DEF.resolvePageKey`/`getPageWithFallback`, `RecordList.loadWithFallback`) — they retry with the archive extension stripped (`DEF.stripArchiveExt`, using `FileData.isArchive()`'s extension list as source of truth) and relaxed directory matching, but only where wired in (`ImageActivity`, `FileSelectList`); they were deliberately **not** wired into `TextActivity`/`EpubWebViewActivity` because those build their lookup key by concatenating the archive name with an in-archive path (e.g. `.../container.xml`), and fuzzy-matching on that would cross-match unrelated books. The server-side read-position/bookmark sync follows the same scoping decision (SMB-file `ImageActivity` only, not text/epub).

**Archive file-list caching**: `ImageManager.cmpFileList()` caches the scanned entry list to disk (keyed by file path + mtime) to avoid re-scanning the archive on every open. Thumbnail generation scans with sorting disabled (`mFileSort = FILESORT_NONE`), so whichever scan populates the cache first can persist the archive's raw (unsorted) internal storage order. The cache-hit path now re-applies the current sort setting to the deserialized list before use, so this doesn't leak into actual reading — see PR #2 upstream (`fix-archive-filelist-cache-sort`) if this file predates that fix.

**Native/JNI layer** (`app/src/main/jni/`, built via CMake) provides image/archive decoding: `dav1d` (AV1), `libavif`, `libjxl`, `unrar`, plus the app's own glue under `jni/comitton`, invoked from Java through `CallImgLibrary`/`CallJniLibrary`/`CallTxtLibrary`.

**Locale resources are duplicated, not overridden selectively.** `res/xml-ja/*.xml` mirrors most of `res/xml/*.xml` (preference screens), and `res/values-ja/strings.xml` mirrors `res/values/strings.xml`. On a Japanese-locale device, the `-ja` file wins *entirely* for anything it defines — a string or preference entry added only to the default `res/xml/` or `res/values/` file will silently not appear on a ja-locale device. When adding a setting/menu entry or string, update both. (One known exception already in this branch: `res/xml/seteverything.xml` has no `-ja` counterpart — harmless since Android just falls back to the base file for that one, but worth knowing the pattern isn't 100% consistently applied.)
