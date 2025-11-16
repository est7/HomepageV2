# Repository Guidelines

## Project Structure & Module Organization
- Single Android app module: `app`.
- Source code: `app/src/main/java/com/example/homepagev2` with packages:
  - `behavior/` coordinator behaviors (e.g., `TitleBarBehavior.kt`).
  - `widget/` custom views (e.g., `TopBarLayout.kt`).
  - `MainActivity.kt`, `UserInformationViewModel.kt`.
- Resources: `app/src/main/res` (layouts, values, drawables, mipmaps).
- Tests: unit tests in `app/src/test`, instrumented tests in `app/src/androidTest`.
- Build config: `build.gradle.kts`, `settings.gradle.kts`, and version catalog `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` — build a debug APK.
- `./gradlew installDebug` — install on connected device/emulator.
- `./gradlew test` — run JVM unit tests.
- `./gradlew connectedDebugAndroidTest` — run instrumented tests on device/emulator.
- `./gradlew lint` — Android Lint checks.
- `./gradlew clean` — remove build outputs. On Windows use `gradlew.bat`.

## Coding Style & Naming Conventions
- Kotlin official style (`kotlin.code.style=official`); 4‑space indentation.
- Classes/objects: UpperCamelCase; functions/vars: lowerCamelCase; constants: UPPER_SNAKE_CASE.
- Resources: layouts `activity_main.xml`, `fragment_*`; ids & drawables snake_case; string keys lower_snake_case.
- ViewBinding is enabled; prefer binding over `findViewById`.

## Testing Guidelines
- Frameworks: JUnit 4, AndroidX Test, Espresso.
- Locations: unit tests in `app/src/test`, instrumented tests in `app/src/androidTest`.
- Naming: test classes end with `Test` (e.g., `UserInformationViewModelTest`); instrumented tests may use `…InstrumentedTest`.
- Add tests for new logic and regressions; keep tests deterministic and fast.

## Commit & Pull Request Guidelines
- Use Conventional Commits when possible: `feat:`, `fix:`, `refactor:`, `chore:`, `docs:`; optional scope (e.g., `feat(widget): add top bar`).
- PRs include: concise summary, linked issues, screenshots/GIFs for UI changes, and a test plan. Ensure `./gradlew assembleDebug` and `./gradlew test` pass.

## Security & Configuration Tips
- Never commit secrets or API keys. Prefer `local.properties` or environment variables; shared defaults go in `gradle.properties`.
- Repositories are centrally defined in `settings.gradle.kts`; avoid per‑module repos unless required.

## Architecture Notes
- Keep UI logic in Activities/Fragments; reusable UI in `widget`.
- Encapsulate scrolling/toolbar behavior in `behavior`.
- Place state/business logic in ViewModels; prefer observable data (LiveData/Flow) over manual callbacks.

## 页面动画交互规格 (Homepage)
- 视图层级（自上而下）: `TopBar(固定) → Face(ViewPager2+Mask) → UserInfo(悬浮且相对Face静止) → TitleBar(紧贴Face底部) → TabLayout → Content(ViewPager2/RecyclerView)`。
- 初始状态: `Face` 完整可见；`UserInfo/TitleBar` 可见；`TopBar` 内容 alpha=0；`TabLayout/Content` 位于其下；`contentTransY = faceHeight`。
- 下拉放大（Pull to Zoom）: 任意位置下拉（Content 顶部时）触发；`Face` 图片线性放大 `scale = 1 + pull/faceHeight`，并粘合使底边贴合 `UserInfo` 顶部；`UserInfo` 视觉相对 TitleBar 静止；`UserInfo+TitleBar+Tab+Content` 整体下移；阻尼 `resistance = 0.8 * (1 - progress)`；释放后回弹到初始。
- 向上滑动折叠（阶段1）: 从头部上滑或 Content 顶部继续上滑；整体上移直至 `TabLayout` 吸附到 `TopBar` 下。`Face` alpha: `1 - upPro`；`UserInfo` alpha: `1 - upPro`（起点=faceHeight）；`TitleBar` 在折叠区间后半段才淡出（起点≈`(contentTransY+topBarHeight)/2`）；`TopBar` 内容 alpha: `upPro`。
- 折叠（阶段2）: 当 `ll_content.translationY == topBarHeight` 后，列表接管滚动；`Face/UserInfo/TitleBar` 透明且移出，`TabLayout` 固定在 `TopBar` 下。
- 顶部下拉展开: 当 RecyclerView 顶部（`canScrollUp == false`）继续下拉，反向执行折叠动画恢复初始状态。
- 横向滑动: `Face` 的 ViewPager2 在任意位置（含 `UserInfo` 区域）可左右切换；手指触摸滑动在`UserInfo` 区域让横向事件穿透到 `Face` 中；`Content` 的 ViewPager2 与 `TabLayout` 双向联动。
- 渐隐/显规则: Face: `alpha = 1 - upPro`；UserInfo: `alpha = 1 - fadeUpPro`（起点=faceHeight）；TitleBar: `alpha = 1 - titleUpPro`（起点≈中点）；TopBar 内容: `alpha = upPro`。
- 触摸拦截（UnifiedPullBehavior）: 初始下拉任意位置均拦截触发放大；非初始下拉仅在 Content 顶部拦截；从头部上滑、且 Content 顶部且有折叠空间（`translationY > topBarHeight`）时拦截。
- 边界与动画: 最大下拉 `downEndY = contentTransY + faceHeight`；最大上滑 `topBarHeight`；Tab 切换需保持 `TitleBar` 位置（可用负 margin），禁止跳变。
- 尺寸: Face=360dp（方形取宽），UserInfo=200dp，TitleBar=160dp，TabLayout=48dp，TopBar=56dp+statusBarHeight；关键量：`contentTransY = faceHeight`，`downEndY = 2*faceHeight`。
- 当前状态: ①TitleBar 与 Face 间隙已用负 margin 处理；
