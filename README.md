# Subdroid — Subreply for Android

Native Android client for [Subreply](https://subreply.com) (`subreply.com/api`), built with **Kotlin** and **Jetpack Compose**.

---

## 1. Application Metadata & Build Configuration

| Property | Value |
| :--- | :--- |
| **App Name** | `Subreply` |
| **Application ID / Package** | `com.lucianmarin.sub` |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose |
| **Minimum SDK** | API 26 (Android 8.0) |
| **Target SDK** | API 35 (Android 15) |
| **Compile SDK** | API 37 |
| **Architecture** | Single-Activity (`MainActivity`), Single Shared State (`AppStateViewModel`), DataStore session persistence (`SessionStorage`), OkHttp + Kotlinx Serialization (`APIClient`) |

---

## 2. Design System: Colors, Typography, Dimensions & Icons

### 2.1 Color Tokens (`SubTheme.kt`)

Colors adapt to System, Light, or Dark mode (user-configurable in `Options` $\rightarrow$ `Account`), with OS status bar and navigation bar icon appearance synced automatically via `WindowCompat.getInsetsController`:

| Token (`SubTheme`) | CSS Variable (`style.sass`) | Light Mode Hex | Dark Mode Hex | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `background` | `var(--white)` | `#FFFFFF` | `#000000` | Window/screen background, floating nav bar background (light mode), selected nav icon stroke & badge text, submit button text |
| `whitesmoke` | `var(--whitesmoke)` | `#F0F0F0` | `#181818` | Input/composer/search background, placeholder banner gradient start, floating nav bar background (dark mode), `1.dp` horizontal dividers (`SubDivider`) |
| `redsmoke` | `var(--redsmoke)` | `#FFF0F0` | `#281818` | Composer / description input background when length `> 640` characters |
| `gainsboro` | `var(--gainsboro)` | `#E0E0E0` | `#282828` | Subtle surface accents |
| `silver` | `var(--silver)` | `#C0C0C0` | `#404040` | Timestamps (`13.sp`), inactive `.menu` tabs, static status labels (`deleted`, `unsent`, `read`, `FOLLOWS YOU`), composer character count |
| `gray` | `var(--gray)` | `#808080` | `#808080` | Secondary text, default `.small` action links (`reply`, `edit`, `save`, `delete`, `unfollow`, `unsend`, `message`), placeholders, `Load more`, `Clear out` |
| `primary` | `var(--black)` | `#000000` | `#FFFFFF` | Primary body text, author `fullName`, default nav icon stroke, active `.menu` tab text, form field labels |
| `accent` | `var(--accent)` | `#2EB8B8` | `#2EB8B8` | Selected nav item background, `.menu` active `10×2.dp` underline bar, inline `@mention` / `/id` / `#hashtag` / `http(s)` links, `unsave` / `follow` / `undo` / `unread` / `NOTES` / `MESSAGE` actions, `SubSubmitButton` background |
| `alert` | `var(--alert)` | `#FF4500` | `#FF4500` | Unread nav icon stroke & badge text, validation errors, destructive confirmations (`yes`, `retry`, `Logout`, `LOGOUT`) |

### 2.2 Typography (`Route159FontFamily` & `MaterialSymbolsFontFamily`)

| Style | Font Family | Weight | Size | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Body / Content** | `Route 159` | Normal / Bold | `14.sp` | `20.sp` | Entry content, author `fullName`, `@username`, chat messages, form inputs, `Load more`, `Clear out` |
| **Menu Header (`SubMenuHeader`)** | `Route 159` | Bold | `14.sp` | `20.sp` | Top subtab headers (`Messages · Replies · Mentions`, `Trending · Saved · Followers · Following`, `Profile · Details · Account`, `Login · Register`, `About · Privacy · Safety · Terms`) |
| **Small Metadata (`.small`)** | `Route 159` | Normal | `13.sp` | `16.sp` | Entry action bar (`reply`, `edit`, `save`, `delete`, `undo`, `yes`), timestamps, profile metadata (`link`, `~ age`, `from city`, `Also on...`), form field labels, validation errors |
| **Small Caps (`.small.caps`)** | `Route 159` | Normal | `12.sp` | `16.sp` | `MemberStatusRowView` (`NOTES`, `EDIT`, `LOGOUT`, `MESSAGE`, `FOLLOW`, `UNFOLLOW`, `FOLLOWS YOU`) |
| **Composer Counter** | `Route 159` | Normal | `10.sp` | `10.sp` | Live character count inside `ComposerView` |
| **Nav Badge** | `Route 159` | Bold | `9.sp` | `9.sp` | Unread notification count (`1`..`99+`) in floating bottom nav bar |

### 2.3 Layout & Spacing Constants

- **Main Content Area**: `640.dp` max width (`Modifier.widthIn(max = 640.dp)`), centered horizontally, extending full height behind the floating bottom navigation bar.
- **Floating Bottom Navigation Bar (`SidebarView`)**:
  - `46.dp` bar height (`34.dp × 34.dp` icon buttons with `4.dp` spacing and `6.dp` padding), `10.dp` corner radius, `4.dp` shadow elevation, `10.dp` bottom margin (`56.dp` total bottom footprint).
  - Background is `SubTheme.background` (`#FFFFFF`) in light mode and `SubTheme.whitesmoke` (`#181818`) in dark mode.
  - Scrollable screens include a `72.dp` bottom spacer so content scrolls cleanly above the floating bar.
- **Top Forms & Subtab Headers (`56.dp` uniform height)**:
  - `ComposerView`, `PlaceholderBannerView`, and `SearchQueryBar`: `20.dp` horizontal / `10.dp` vertical outer padding + `36.dp` single-line inner box (`10.dp` horizontal / `8.dp` vertical inner padding around `20.sp` text, `6.dp` corner radius) = `56.dp` total height.
  - `SubMenuHeader`: `20.dp` horizontal / `10.dp` vertical outer padding + `36.dp` inner row (`10.dp` top / `6.dp` bottom inner padding around `20.sp` text with a `10.dp × 2.dp` `#2EB8B8` active highlight bar offset `3.dp` below the text box) = `56.dp` total height.
- **Row Padding & Dividers**: `20.dp` horizontal and `10.dp` vertical padding around entries, user rows, and message rows, separated by `1.dp` `SubTheme.whitesmoke` horizontal dividers (`SubDivider`).
- **Reply / Sublist / Incoming Chat Indentation**: `50.dp` (`20.dp` outer horizontal padding + `30.dp` indent).

### 2.4 Google Material Symbols Font (`SubIcons.kt`)

All icons are rendered from the subsetted Google **Material Symbols Rounded** TrueType font (`res/font/material_symbols_rounded.ttf`, `20pt Medium`, `7,824` bytes) containing the 19 icons used in the UI:
`account_circle`, `alternate_email`, `bolt`, `bookmark`, `cancel`, `chat`, `chat_bubble`, `group`, `info`, `language`, `lock`, `move_down`, `move_up`, `person_add`, `reply`, `search`, `sms`, `trending_up`, `tune`.

---

## 3. Navigation & Floating Bottom Bar (`MainScaffold.kt` & `SidebarDrawer.kt`)

### 3.1 Sections & Subtab Grouping (`SidebarSection` in `Models.kt`)

1. **Authenticated Bottom Bar (5 items)**:
   - `FEED` (`"feed"`, `"Feed"`, icon `"bolt"`)
   - `MESSAGES` (`"messages"`, `"Messages"`, icon `"chat_bubble"`) — groups subtabs:
     - `MESSAGES` (`"chat_bubble"`)
     - `REPLIES` (`"reply"`)
     - `MENTIONS` (`"alternate_email"`)
   - `TRENDING` (`"trending"`, `"Trending"`, icon `"trending_up"`) — groups subtabs:
     - `TRENDING` (`"trending_up"`)
     - `SAVED` (`"bookmark"`)
     - `FOLLOWERS` (`"move_down"`)
     - `FOLLOWING` (`"move_up"`)
   - `PEOPLE` (`"people"`, `"People"`, icon `"group"`) — also highlights when viewing `PROFILE` or `OPTIONS`
   - `DISCOVER` (`"discover"`, `"Discover"`, icon `"language"`)

2. **Unauthenticated Bottom Bar (5 items)**:
   - `ABOUT` (`"about"`, `"About"`, icon `"info"`) — tabs: `About`, `Privacy`, `Safety`, `Terms`
   - `LOGIN` (`"login"`, `"Login"`, icon `"lock"`) — groups subtabs:
     - `LOGIN` (`"lock"`)
     - `REGISTER` (`"person_add"`)
   - `TRENDING` (`"trending"`, `"Trending"`, icon `"trending_up"`)
   - `PEOPLE` (`"people"`, `"People"`, icon `"group"`)
   - `DISCOVER` (`"discover"`, `"Discover"`, icon `"language"`)

3. **Dynamic Floating Bar Icons**:
   - When inside a 1-on-1 `Conversation`, the `MESSAGES` button displays `"chat"` if the conversation has messages or `"sms"` if empty.
   - When a subtab is active under `MESSAGES` (`REPLIES`, `MENTIONS`), `TRENDING` (`SAVED`, `FOLLOWERS`, `FOLLOWING`), or `LOGIN` (`REGISTER`), the corresponding bottom bar button displays the active subtab's icon (`"reply"`, `"alternate_email"`, `"bookmark"`, `"move_down"`, `"move_up"`, `"person_add"`).

4. **Selection, Badges & Push Navigation**:
   - Active button (`isSelected == true`): `SubTheme.accent` (`#2EB8B8`) rounded background (`6.dp` radius) with `SubTheme.background` icon and badge text.
   - Unread notification (`!isSelected && badgeCount > 0`): `SubTheme.alert` (`#FF4500`) icon and top-right badge (`1`..`99+`). `MESSAGES` aggregates `messages + replies + mentions`; `TRENDING` shows `followers`.
   - Push navigation stack (`NavigationDestination`): `ReplyDetail(id)`, `Member(username)`, `Conversation(username)`, `EditEntry(entry)`, `Options`, handled with Android system back (`BackHandler`).

---

## 4. Screen & Component Specifications

### 4.1 Rich Content & Token Parser (`ContentParser.kt` & `RichContentView`)

- Builds a single flowing `AnnotatedString` paragraph (`14.sp` Route 159, `20.sp` line height).
- Prepends `author.fullName + " "` in **Bold** `SubTheme.primary` (clickable $\rightarrow$ `openMember(author.username)`).
- Tokenizes content words with punctuation unwrapping:
  - `@username` (`ContentToken.Mention`) $\rightarrow$ `SubTheme.accent`, clickable $\rightarrow$ `openMember(username)`
  - `/123` (`ContentToken.ReplyRef`) $\rightarrow$ `SubTheme.accent`, clickable $\rightarrow$ `openReply(id)`
  - `#hashtag` (`ContentToken.Hashtag`) $\rightarrow$ `SubTheme.accent`, clickable $\rightarrow$ `searchHashtag(tag)` (opens `DISCOVER` with `#$tag`)
  - `http(s)://...` (`ContentToken.Link`) $\rightarrow$ `SubTheme.accent`, clickable $\rightarrow$ opens URL in browser; display label strips protocol/`www.`/trailing slash and truncates at 25 characters (`take(25) + "..."`).

### 4.2 Entries & Streams (`EntryRow.kt` & `FeedScreen.kt`)

- **`EntryItemView`**:
  - Renders `RichContentView` followed (`4.dp` gap) by the lowercase `.small` metadata row (`13.sp`):
    - Left: `reply` / `1 reply` / `N replies` (+ `" ¬"` on root threads), `edit` (own post with 0 replies), `save` / `unsave` / `retry` (others' posts), `delete` $\rightarrow$ `undo` / `yes` $\rightarrow$ `deleted` (own post or direct reply to own post with 0 replies).
    - Right: `timestamp` (prefixed with `"··· "` when `entry.edited == true`).
- **`StreamEntryView`**:
  - **Reply (`entry.parent != null`)**: Renders a 1-line `0.5f`-opacity `InlineParentRowView` above the reply (collapsing consecutive replies sharing the same `parent.id`), with the reply indented by `30.dp`.
  - **Root thread (`entry.parent == null`)**: Renders the root post at full width, followed by `entry.streamKids` (`kids.take(5).reversed()`) indented by `30.dp`.
- **`FeedStreamScreen`**:
  - Top bar: `ComposerView("Say something")` on `FEED` (or auth prompt `PlaceholderBannerView` when logged out), `SearchQueryBar("Search content")` on `DISCOVER`, and `SectionSubtabsHeader` on `MENTIONS`, `REPLIES`, `SAVED`, and `TRENDING`.
  - Pull-to-refresh (`SubPullToRefreshColumn`) and pagination (`pageSize = 16`, `"Load more"` / `"Retry"`).

### 4.3 Reply Detail & Edit Entry (`ReplyDetailScreen.kt`)

- **`ReplyDetailScreen`**:
  1. Chronological `ancestors` + focal `mainEntry` at top.
  2. Inline `ComposerView("Reply to @username")` or `PlaceholderBannerView` (`Login or register your account to reply` / `No reply yet` / `Reply back to them` / `Share reply /id anywhere` with a vertically flipped `"reply"` icon).
  3. Descendant `kids` (`ThreadNodeView`), with nested `reply.kids` indented by `30.dp`.
- **`EditEntryScreen`**:
  - Displays optional parent entry, focal entry, and pre-filled `ComposerView("Edit content")` (`PATCH /api/edit/{id}`).

### 4.4 Member Profile & People Lists (`MemberScreen.kt` & `PeopleListScreen.kt`)

- **`UserRowView`**:
  - Line 1: Bold `member.fullName` (`+ " @username"` in bold `gray` on Member header; or `" • "` + `Logout` / `Follow` / `Unfollow` in lists) and right-aligned `timestamp`.
  - Line 2: Bio (`RichContentView`) if non-empty.
  - Line 3: Metadata (`hostname`, `"~ Xy old"`, `"from "` + clickable `cityLabel` that searches `PEOPLE`).
- **`MemberScreen`**:
  - Renders `UserRowView(isMemberHeader = true)`, optional `MemberLinksRowView` (`"Also on ... Donated $X."`), uppercase `MemberStatusRowView` (`NOTES`, `EDIT`, `LOGOUT` for self; `MESSAGE`, `FOLLOW`/`UNFOLLOW`, `FOLLOWS YOU` for others), and the member's posts stream (`pageSize = 16`).
- **`PeopleListScreen`**:
  - Top bar: `SearchQueryBar("Find people")` on `PEOPLE`, or `SectionSubtabsHeader` (`Trending · Saved · Followers · Following`) on `FOLLOWING` and `FOLLOWERS`.
  - Paginates user rows with `pageSize = 24`.

### 4.5 Messages Inbox & Conversation (`MessagesScreen.kt`)

- **`MessagesInboxScreen`**:
  - Top `SectionSubtabsHeader` (`Messages · Replies · Mentions`), followed by `ChatInboxRowView` items (`RichContentView` + `.small` row with `message`, `read`/`unread`, and `timestamp`, `pageSize = 16`).
- **`ConversationScreen`**:
  - Top `ComposerView("Message @username" / "Message yourself")` or `PlaceholderBannerView("Please wait for @username to respond")` when waiting for a reply.
  - Incoming messages indented by `30.dp` with no `.small` row; outgoing messages at full width with `.small` row (`read`/`unread`, `unsend` $\rightarrow$ `undo`/`yes` $\rightarrow$ `unsent`, `timestamp`).
  - Bottom `"Load more"` or `"Clear out"` (`Undo — Yes` $\rightarrow$ `Cleared`).

### 4.6 Options, Auth & About (`OptionsScreen.kt` & `AuthScreens.kt`)

- **`OptionsScreen`**:
  - `SubMenuHeader` with `Profile` (`PATCH /api/profile`), `Details` (`PATCH /api/details`), and `Account` (API Base URL, Theme selector: `System` / `Light` / `Dark`, and `Logout`).
- **`LoginScreen`**:
  - `SubMenuHeader` with `Login` (`POST /api/login`) and `Register` (`POST /api/register`).
- **`AboutScreen`**:
  - `SubMenuHeader` with `About` (features, privacy rules, API Server URL setting), `Privacy`, `Safety`, and `Terms`.

