# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HomepageV2 is an Android application implementing a sophisticated user profile/homepage interface with complex nested scrolling behaviors. The app demonstrates advanced CoordinatorLayout usage with custom behaviors to create a fluid, interactive UI where multiple layers (face/avatar, user info, title bar, and content) move and transform dynamically during scroll interactions.

## Project Structure & Module Organization

- Single Android app module: `app`
- Source code: `app/src/main/java/com/example/homepagev2` with packages:
  - `behavior/` - Coordinator behaviors (e.g., `HeaderBehavior.kt`, `TitleBarBehavior.kt`)
  - `widget/` - Custom views (e.g., `TopBarLayout.kt`, `NestedScrollFrameLayout.kt`)
  - `MainActivity.kt`, `UserInformationViewModel.kt`
- Resources: `app/src/main/res` (layouts, values, drawables, mipmaps)
- Tests: unit tests in `app/src/test`, instrumented tests in `app/src/androidTest`
- Build config: `build.gradle.kts`, `settings.gradle.kts`, and version catalog `gradle/libs.versions.toml`

## Build & Development Commands

```bash
./gradlew assembleDebug              # Build a debug APK
./gradlew installDebug               # Install on connected device/emulator
./gradlew test                       # Run JVM unit tests
./gradlew connectedDebugAndroidTest  # Run instrumented tests on device/emulator
./gradlew lint                       # Android Lint checks
./gradlew clean                      # Remove build outputs
```

**Note**: On Windows use `gradlew.bat` instead of `./gradlew`

## Coding Style & Naming Conventions

- **Kotlin Style**: Official Kotlin style (`kotlin.code.style=official`); 4-space indentation
- **Class Names**: UpperCamelCase (e.g., `HeaderBehavior`, `MainActivity`)
- **Functions/Variables**: lowerCamelCase (e.g., `onDependentViewChanged`, `contentTransY`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `PULL_RESIST`, `TAG`)
- **Resources**:
  - Layouts: `activity_main.xml`, `fragment_*`
  - IDs & drawables: snake_case (e.g., `face_container`, `ll_userinfo`)
  - String keys: lower_snake_case
- **ViewBinding**: Enabled and preferred over `findViewById`

## Testing Guidelines

- **Frameworks**: JUnit 4, AndroidX Test, Espresso
- **Locations**:
  - Unit tests: `app/src/test`
  - Instrumented tests: `app/src/androidTest`
- **Naming**:
  - Test classes end with `Test` (e.g., `UserInformationViewModelTest`)
  - Instrumented tests may use `…InstrumentedTest`
- **Best Practices**:
  - Add tests for new logic and regressions
  - Keep tests deterministic and fast
  - Prefer unit tests over instrumented tests when possible

## Commit & Pull Request Guidelines

- **Commit Format**: Use Conventional Commits when possible:
  - `feat:` - New features
  - `fix:` - Bug fixes
  - `refactor:` - Code refactoring
  - `chore:` - Maintenance tasks
  - `docs:` - Documentation changes
  - Optional scope: `feat(widget): add top bar`, `fix(behavior): correct scroll calculation`
- **Pull Requests**:
  - Include concise summary
  - Link related issues
  - Add screenshots/GIFs for UI changes
  - Provide test plan
  - Ensure `./gradlew assembleDebug` and `./gradlew test` pass before submitting

## Security & Configuration

- **Never commit secrets or API keys**
- Use `local.properties` or environment variables for sensitive data
- Shared defaults go in `gradle.properties`
- Repositories are centrally defined in `settings.gradle.kts`
- Avoid per-module repos unless required

## Architecture

### Architecture Principles

- **UI Logic**: Keep in Activities/Fragments
- **Reusable UI**: Place in `widget/` package
- **Scrolling/Toolbar Behavior**: Encapsulate in `behavior/` package
- **State/Business Logic**: Place in ViewModels
- **Observable Data**: Prefer LiveData/Flow over manual callbacks

### Core UI Structure

The app uses a single-activity architecture (`MainActivity`) with a multi-layered CoordinatorLayout-based UI:

1. **Face Container** (`face_container`) - Square aspect ratio container with ViewPager2 for profile images
2. **UserInfo Layer** (`ll_userinfo`) - User information overlay with touch passthrough
3. **TopBar** (`cl_top_bar`) - Navigation bar at the top
4. **TitleBar** (`cls_title_bar_container`) - Title section that becomes visible during collapse
5. **Content** (`ll_content`) - Main scrollable content area with TabLayout and ViewPager2

### Custom Behavior System

The app's scroll interactions are controlled by custom `CoordinatorLayout.Behavior` implementations in the `behavior/` package. These behaviors coordinate to create a unified scroll experience:

#### HeaderBehavior (NEW - Primary Controller)
- **File**: `behavior/HeaderBehavior.kt`
- **Controls**: `ll_content` vertical motion
- **Purpose**: Single unified behavior handling all vertical drag and nested scroll for the content layer
- **Key anchors**:
  - `contentTransY` = faceHeight + titleBarHeight (initial position)
  - `topBarHeight` (collapsed position)
  - `downEndY` = contentTransY + faceHeight (max pull-down)
- **Handles**:
  - Direct touch drags on header areas (face/userinfo/title)
  - Nested pre-scroll from content RecyclerViews when at top
  - Damped pull-down with resistance
  - Automatic rebound to contentTransY when pulled below
  - Immediate expansion from collapsed state

#### FaceBehavior
- **File**: `behavior/FaceBehavior.kt`
- **Controls**: `face_container` scaling and positioning
- **Purpose**: Makes face scale 1:1 during pull-down and fade during collapse
- **Key features**:
  - Linear 1:1 scaling: `scale = 1 + (pullPx / baseHeight)`
  - Glue compensation to keep bottom edge aligned with userinfo
  - Fade out during upward scroll
  - Uses Palette API to extract gradient colors from avatar

#### TitleBarBehavior
- **File**: `behavior/TitleBarBehavior.kt`
- **Controls**: `cls_title_bar_container` positioning and alpha
- **Purpose**: Positions title bar to stick to content top, fades in during collapse
- **Key features**:
  - Always positioned at `ll_content.y - titleBarHeight - overlapOffsetY`
  - Gradual fade-in only in second half of collapse range
  - Uses `roundToInt()` for pixel-perfect positioning

#### UserInfoBehavior
- **File**: `behavior/UserInfoBehavior.kt`
- **Controls**: `ll_userinfo` positioning and alpha
- **Purpose**: Positions user info above title bar, fades out during scroll
- **Key features**:
  - Positioned at `ll_content.y - titleBarHeight - userInfoHeight`
  - Fades from 1→0 as content scrolls from initial to face bottom
  - Enables touch passthrough (see NestedScrollFrameLayout)

#### TopBarBehavior
- **File**: `behavior/TopBarBehavior.kt`
- **Controls**: `cl_top_bar` visibility and position
- **Purpose**: Top navigation bar behavior (standard collapse/expand)

### Custom Widgets

#### NestedScrollFrameLayout
- **File**: `widget/NestedScrollFrameLayout.kt`
- **Purpose**: FrameLayout implementing `NestedScrollingChild3` for nested scroll support
- **Key features**:
  - Enables nested scrolling for non-scrollable containers
  - Implements touch passthrough for `ll_userinfo` (id check)
  - Detects vertical vs horizontal drag direction
  - Forwards horizontal touch events to underlying ViewPager2 when used as `ll_userinfo`

#### SquareNestedScrollFrameLayout
- **File**: `widget/SquareNestedScrollFrameLayout.kt`
- **Purpose**: Variant that enforces square aspect ratio (width = height)
- **Usage**: Wraps the face ViewPager2 to ensure square images

#### AnimatedNavigationBar
- **File**: `widget/AnimatedNavigationBar.kt`
- **Purpose**: Custom bottom navigation with animations and badge support

#### TopBarLayout
- **File**: `widget/TopBarLayout.kt`
- **Purpose**: Custom top bar layout

### Important Configuration Values

All layout dimensions are defined in `app/src/main/res/values/dimens.xml`:

```xml
<dimen name="iv_face_height">360dp</dimen>          <!-- Face/avatar height -->
<dimen name="userinfo_height">200dp</dimen>         <!-- User info section height -->
<dimen name="title_bar_height">160dp</dimen>        <!-- Title bar height -->
<dimen name="top_bar_height">56dp</dimen>           <!-- Top navigation bar height -->
<dimen name="title_bar_overlap_y">10dp</dimen>      <!-- TitleBar overlap onto face -->
<dimen name="face_trans_y">0dp</dimen>              <!-- Additional face translation (usually 0) -->
```

**Anchors are computed dynamically** in HeaderBehavior:
- `contentTransY` = measured faceHeight + measured titleBarHeight (initial position)
- `topBarHeight` = topBarContentHeight + statusBarHeight (collapsed position)
- `downEndY` = contentTransY + faceHeight (max pull-down position)

### Nested Scroll Flow

1. User touches face/userinfo/title area or scrolls content list
2. `NestedScrollFrameLayout` (for face/userinfo) or RecyclerView (for content) detects scroll
3. `HeaderBehavior.onNestedPreScroll()` consumes scroll to move `ll_content`
4. All other behaviors (`FaceBehavior`, `TitleBarBehavior`, `UserInfoBehavior`) observe `ll_content` position via `layoutDependsOn` and update their views accordingly
5. When pulled below `contentTransY`, HeaderBehavior triggers rebound animation

### Key Implementation Patterns

1. **Dynamic Anchor Calculation**: Behaviors capture `contentTransY` and other anchors at runtime from measured view dimensions, not hardcoded dimens
2. **Pixel-Perfect Layout**: Use `roundToInt()` when setting view positions to avoid 1px gaps
3. **Damped Pull Resistance**: Pull-down uses `PULL_RESIST` factor that increases as you pull further
4. **Touch Passthrough**: `ll_userinfo` has `id` check in `NestedScrollFrameLayout` to forward horizontal touches to underlying face ViewPager2
5. **Type-Based Scroll Handling**: Behaviors distinguish between `TYPE_TOUCH` (user drag) and `TYPE_NON_TOUCH` (fling) for different handling logic

## Homepage Animation & Interaction Specification (页面动画交互规格)

This section defines the detailed behavior and interaction rules for the homepage scrolling system.

### View Hierarchy (视图层级)

From top to bottom:
1. **TopBar** (固定) - Fixed navigation bar at top
2. **Face** (ViewPager2 + Mask) - Profile image gallery with mask overlay
3. **UserInfo** (悬浮且相对Face静止) - User information layer, floating and visually static relative to Face
4. **TitleBar** (紧贴Face底部) - Title section sticking to Face bottom
5. **TabLayout** - Tab navigation
6. **Content** (ViewPager2/RecyclerView) - Main scrollable content

### Initial State (初始状态)

- **Face**: Fully visible
- **UserInfo/TitleBar**: Visible
- **TopBar content**: `alpha = 0`
- **TabLayout/Content**: Positioned below Face
- **contentTransY**: `= faceHeight + titleBarHeight` (measured dynamically in HeaderBehavior)
  - **Note**: AGENTS.md specifies `contentTransY = faceHeight`, but current implementation uses `faceHeight + titleBarHeight`

### Pull to Zoom (下拉放大)

**Trigger**: Pull down from any position when Content is at top

**Behavior**:
- **Face scaling**: Linear 1:1 scaling: `scale = 1 + pull / faceHeight`
- **Face positioning**: Glue compensation to keep bottom edge aligned with UserInfo top
- **UserInfo**: Visually static relative to TitleBar
- **Movement**: `UserInfo + TitleBar + Tab + Content` move down as a group
- **Resistance**: Damped with `resistance = 0.8 * (1 - progress)`
- **Release**: Rebounds to initial position

**Formulas**:
```kotlin
scale = 1 + (pullPx / baseHeight)
bottomExtension = baseH * (scale - 1) * 0.5f
glue = pullPx - bottomExtension
imageView.translationY = glue
```

### Upward Collapse - Stage 1 (向上滑动折叠 - 阶段1)

**Trigger**: Swipe up from header or continue scrolling up from Content top

**Behavior**: Entire layout moves up until TabLayout snaps under TopBar

**Fade Rules**:
- **Face**: `alpha = 1 - upPro`
  - Range: Full collapse range from `contentTransY` to `topBarHeight`
- **UserInfo**: `alpha = 1 - upPro`
  - Start point: `faceHeight`
  - Fades as content moves from `faceHeight` to final collapsed position
- **TitleBar**: `alpha = 1 - titleUpPro`
  - Start point: `≈ (contentTransY + topBarHeight) / 2` (only fades in second half of collapse range)
  - Delayed fade-out to avoid early content obstruction
- **TopBar content**: `alpha = upPro`
  - Gradually appears as collapse progresses

### Collapse - Stage 2 (折叠 - 阶段2)

**Trigger**: When `ll_content.translationY == topBarHeight`

**Behavior**:
- RecyclerView takes over scrolling
- Face/UserInfo/TitleBar: Transparent and moved out of view
- TabLayout: Fixed under TopBar
- Content scrolls independently

### Expand from Top (顶部下拉展开)

**Trigger**: Pull down when RecyclerView is at top (`canScrollUp == false`)

**Behavior**: Reverse of collapse animation, restoring to initial state

### Horizontal Scrolling (横向滑动)

**Face ViewPager2**:
- Can swipe left/right at any position (including over UserInfo area)
- UserInfo area forwards horizontal touch events to underlying Face (touch passthrough)

**Content ViewPager2**:
- Bidirectional sync with TabLayout
- Standard ViewPager2 behavior

### Touch Interception Rules (触摸拦截)

**Initial pull-down** (from `contentTransY`):
- Intercept at any position to trigger zoom

**Non-initial pull-down**:
- Only intercept when Content is at top

**Upward scroll**:
- Intercept when:
  - Drag from header area, AND
  - Content is at top, AND
  - There's collapse space (`translationY > topBarHeight`)

### Boundaries & Animations (边界与动画)

- **Max pull-down**: `downEndY = contentTransY + faceHeight`
- **Max collapse**: `topBarHeight`
- **Tab switching**: Must maintain TitleBar position (can use negative margin), no jumping
- **Rebound duration**: 400ms
- **Collapse duration**: 180ms (fast collapse to top)

### Dimensions (尺寸)

From `app/src/main/res/values/dimens.xml`:
- **Face**: 360dp (square, takes width)
- **UserInfo**: 200dp
- **TitleBar**: 160dp
- **TabLayout**: 48dp
- **TopBar**: 56dp + statusBarHeight

**Key Calculations**:
- `contentTransY = faceHeight + titleBarHeight` (measured dynamically in HeaderBehavior)
- `downEndY = contentTransY + faceHeight`
- **Note**: Some documentation references `contentTransY = faceHeight`, but actual implementation includes titleBarHeight

### Current Implementation Status

- ✅ TitleBar gap with Face handled via negative margin/overlap
- ✅ Touch passthrough for UserInfo to enable horizontal swipe on Face ViewPager2
- ✅ Unified HeaderBehavior replaces previous separate behaviors
- ✅ Damped pull-down with resistance
- ✅ Immediate expansion from collapsed state (no gating)
- ✅ Separate handling of TOUCH vs NON_TOUCH scroll types

## Common Development Tasks

### Adding a New Behavior

1. Create class extending `CoordinatorLayout.Behavior<View>`
2. Override `layoutDependsOn()` to specify dependency (usually `ll_content`)
3. Implement `onDependentViewChanged()` to update view based on dependency position
4. Add behavior to view in `activity_main.xml` using `app:layout_behavior=".behavior.YourBehavior"`

### Modifying Scroll Anchors

1. Update dimension values in `app/src/main/res/values/dimens.xml`
2. Anchors are computed in `HeaderBehavior.onLayoutChild()` from measured view sizes
3. Test with various screen sizes/orientations

### Debugging Scroll Issues

- Enable logs in behaviors (TAG constants defined in each)
- Key log points:
  - `HeaderBehavior`: logs all nested scroll events with type and consumed amounts
  - `NestedScrollFrameLayout`: logs touch intercept and direction detection
- Check view hierarchy inspector to verify translationY values match expectations

## Project Configuration

- **minSdk**: 24
- **targetSdk**: 35
- **compileSdk**: 35
- **Java/Kotlin**: JVM target 21
- **ViewBinding**: Enabled
- **Architecture Components**: ViewModel, LiveData, Lifecycle
- **Key Dependencies**: AndroidX, Material Design, ViewPager2, Palette

## Recent Work (feat/header-rewrite branch)

Recent commits focused on unifying scroll behavior logic:
- Replaced separate pull behaviors with single `HeaderBehavior`
- Added touch passthrough for `ll_userinfo` to enable horizontal swipes on face ViewPager2
- Optimized nested scroll handling to avoid conflicts between touch and fling events
- Removed deprecated `ContentBehavior` and `UnifiedPullBehavior` in favor of `HeaderBehavior`
