# Subreply for Android (`subdroid`) — 1:1 UI/UX Rewrite Specification & Plan

Native Android client for [Subreply](https://subreply.com) (`subreply.com/api`), built with **Kotlin** and **Jetpack Compose**. This specification defines a complete **1:1 UI and UX rewrite** of `subdroid` to match the exact layout, styling, navigation, feeds, threads, replies, profiles, options, forms, and interactions of `../subreply` (specifically its `@media (max-width: 594px)` mobile grid and HTML templates) and `../macreply` (specifically its compact icon-only sidebar, `NavigationStack` shell, and SwiftUI views).

---

## 1. Executive Summary: Why a Full UI Rewrite is Required

An audit of the current `subdroid` codebase against `../subreply` and `../macreply` revealed that `subdroid` currently implements a completely different, outdated UI and navigation model:

| Area | Current `subdroid` (Non-Matching) | Target `subdroid` (1:1 with `../subreply` & `../macreply`) |
| :--- | :--- | :--- |
| **App Shell & Chrome** | Has a top header bar (`TopHeaderBar`) with a hamburger icon, title, search toggle, and Sign In button, plus a slide-over `ModalNavigationDrawer` and modal bottom sheets for auth. | **Zero top app bar, zero slide-over drawer, zero modal auth sheets.** Uses a permanent left-hand **icon-only sidebar** (`40.dp` wide, matching `.container` in `style.sass` `@media (max-width: 594px)` and `SidebarView(isCompact: true)` in `macreply`) alongside the main content column. |
| **Sidebar Sections** | Uses outdated grouped sections (`LIVE`, `TRENDS`, `RECENT`, `POSTS`, `BOOKMARKS`, `ACTIVE`, `TOP`, `NEW`, `SITES`, `REFUNDS`). | Exact `SidebarSection` list from `sidebar.html` and `macreply`:<br>• **Authenticated (12)**: `feed`, `following`, `followers`, `mentions`, `messages`, `replies`, `saved`, `profile`, `options`, `people`, `trending`, `discover`<br>• **Unauthenticated (6)**: `about`, `login`, `register`, `people`, `trending`, `discover` |
| **Colors (`SubTheme.kt`)** | Uses gray/charcoal surfaces (`#111111`, `#EEEEEE`, `#E05555`). | Exact CSS custom properties from `static/style.sass` (`:root` and `@media (prefers-color-scheme: dark)`):<br>• Light: `white=#FFFFFF`, `whitesmoke=#F0F0F0`, `redsmoke=#FFF0F0`, `gainsboro=#E0E0E0`, `silver=#C0C0C0`, `gray=#808080`, `black=#000000`<br>• Dark: `white=#000000`, `whitesmoke=#181818`, `redsmoke=#281818`, `gainsboro=#282828`, `silver=#404040`, `gray=#808080`, `black=#FFFFFF`<br>• Constant: `accent=#2EB8B8`, `alert=#FF4500` |
| **Entry Layout (`EntryRow.kt`)** | Renders a separate author header row, `@username` badge, star/heart counters (`isStarred`/`starCount`), and link preview cards. | **Single flowing paragraph (`RichContentView`)** starting with bold `entry.createdBy.fullName + " "` (which already includes the user's emoji from the API) followed inline by parsed `entry.content` tokens (`14.sp` Route 159, `20.sp` line height). Below (`4.dp` gap) is the `.small` lowercase action/timestamp row (`13.sp`): `reply`/`1 reply`/`N replies` (+ `" ¬"` on root threads), `edit` or `save`/`unsave`, `delete` (`undo`/`yes`), and right-aligned `timestamp` (prefixed with `"··· "` when `entry.edited == true`). |
| **Stream & Sublist (`FeedScreen.kt`)** | Flat cards without collapsed inline parent previews or `.sublist` children. | Exact `StreamEntryView` (`templates/common/stream.html`):<br>• **Reply (`entry.parent != null`)**: Renders 1-line `0.5f`-opacity `InlineParentRowView` above the reply (collapsing consecutive replies that share the same `parent.id`), with the reply indented by `30.dp`.<br>• **Root thread (`entry.parent == null`)**: Renders root post at full width, followed by `entry.streamKids` (`kids.take(5).reversed()`) indented by `30.dp` (`.sublist`). |
| **Reply Detail (`ReplyDetailScreen.kt`)** | Custom header bar + persistent bottom reply bar + non-matching cards. | Exact `ReplyDetailView` (`templates/pages/reply.html`):<br>1. Chronological `ancestors` + focal `mainEntry` at top.<br>2. Middle inline `ComposerView("Reply to @username")` OR `PlaceholderBannerView` (`Login or register your account to reply` / `No reply yet` / `Reply back to them` / `Share reply #id anywhere` with vertically flipped reply icon).<br>3. Bottom `kids` (`ThreadNodeView`), with nested `reply.kids` indented by `30.dp` (`.inlist`). |
| **Composer & Search (`ComposerBar.kt`)** | Multiline box with explicit send button / top search bar. | Exact `ComposerView` (`templates/input/thread.html`, `reply.html`, `message.html`, `edit.html`): single-paragraph auto-expanding field, `whitesmoke` bg (`redsmoke` when `> 640` chars), `6.dp` radius, hard clamp at `720` chars, live char count at bottom-right when non-empty, `Enter`/`Send` strips newlines and submits immediately (no submit button). `SearchQueryBar` (`templates/input/query.html`) only at the top of `People` (`"Find people"`) and `Discover` (`"Search content"`). |
| **Profiles & People (`MemberScreen.kt`, `PeopleListScreen.kt`)** | Custom profile card with follower/post counts. | Exact `UserRowView` (`templates/common/user.html`) + `MemberView` (`templates/pages/member.html`):<br>• Top row: bold `fullName` (`+ " @username"` in bold `gray` on Member page; or `" • "` + `Logout`/`Follow`/`Unfollow` in `People`/`Following`/`Followers` lists) and right-aligned `timestamp` (`createdAt` for self, `seenAt` for others).<br>• Bio line (`RichContentView`) + metadata line (`hostname`, `"~ Xy old"`, `"from "` + clickable `cityLabel`).<br>• Member page adds `Also on ... Donated $X.` row + `.small.caps` (`12.sp` uppercase) status row (`NOTES`/`EDIT`/`LOGOUT` or `MESSAGE`/`FOLLOW`/`UNFOLLOW`/`FOLLOWS YOU`) + member's posts stream. |
| **Messages (`MessagesScreen.kt`)** | Split/tabbed chat with bubble cards. | Exact `MessagesInboxView` (`templates/pages/messages.html`) and `ConversationView` (`templates/pages/message.html`):<br>• Inbox: `RichContentView` + `.small` row (`message`, `read`/`unread`, right-aligned `timestamp`).<br>• Conversation: Top `ComposerView("Message @username")` (or `"Message yourself"`, or `PlaceholderBannerView("Please wait for @username to respond")` when `blocked`), incoming messages indented by `30.dp` with **no** `.small` row, outgoing messages at `0.dp` indent with `.small` row (`read`/`unread`, `unsend` $\rightarrow$ `undo`/`yes` $\rightarrow$ `unsent`, `timestamp`), bottom `Load more` or `Clear out` (`Undo — Yes` $\rightarrow$ `Cleared`). |
| **Menu Header, Options & Auth (`OptionsScreen.kt`, `AuthScreens.kt`)** | Material tabs, switches, and modal bottom sheets. | Exact `.menu` header (`14.sp` bold with `10.dp × 2.dp` `#2EB8B8` underline bar under the first letter of the active tab) and multi-column grid forms (`OptionsView`: `Profile` / `Details` / `Account`; `LoginView`; `RegisterView`; `AboutView`). |

---

## 2. Application Metadata & Build Configuration

| Property | Value |
| :--- | :--- |
| **App Name** | `Subreply` |
| **Application ID / Package** | `com.lucianmarin.sub` |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose (custom minimalist components matching `style.sass` & `Macreply`) |
| **Minimum SDK** | API 26 (Android 8.0) |
| **Target / Compile SDK** | API 35 (Android 15) |
| **Architecture** | Single-Activity (`MainActivity`), Single Shared State (`AppStateViewModel`) |

---

## 3. Exact Design System: Colors, Typography, Dimensions & Icons

### 3.1 Color Tokens (`SubTheme.kt` — 1:1 with `static/style.sass` & `MainSplitView.swift`)

All colors adapt automatically to system Light and Dark mode (`isSystemInDarkTheme()`), with optional user override in `Account` settings if desired (defaulting to system):

| Token (`SubTheme`) | CSS Variable (`style.sass`) | Light Mode Hex | Dark Mode Hex | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `background` | `var(--white)` | `#FFFFFF` | `#000000` | Window/screen background, selected sidebar icon stroke & badge text, submit button text |
| `whitesmoke` | `var(--whitesmoke)` | `#F0F0F0` | `#181818` | Input/composer/search background, placeholder banner background, `1.dp` horizontal & vertical dividers |
| `redsmoke` | `var(--redsmoke)` | `#FFF0F0` | `#281818` | Composer / description input background when length `> 640` characters |
| `gainsboro` | `var(--gainsboro)` | `#E0E0E0` | `#282828` | Subtle hover/pressed states |
| `silver` | `var(--silver)` | `#C0C0C0` | `#404040` | Timestamps (`13.sp`), inactive `.menu` tabs, static status labels (`deleted`, `unsent`, `read`, `FOLLOWS YOU`), character count below limit |
| `gray` | `var(--gray)` | `#808080` | `#808080` | Secondary text, default `.small` action links (`reply`, `edit`, `save`, `delete`, `unfollow`, `unsend`, `message`), field labels, `Load more`, `Clear out` |
| `primary` | `var(--black)` | `#000000` | `#FFFFFF` | Primary body text, author `fullName`, default sidebar icon stroke, active `.menu` tab text |
| `accent` | `var(--accent)` | `#2EB8B8` | `#2EB8B8` | Selected sidebar item background, `.menu` active `10×2.dp` underline bar, inline `@mention` / `#id` / `#hashtag` / `http(s)` links, `unsave` / `follow` / `undo` / `unread` / `NOTES` / `MESSAGE` actions, `SubSubmitButton` background |
| `alert` | `var(--alert)` | `#FF4500` | `#FF4500` | Unread sidebar icon stroke & badge text, validation errors, `> 640` char counter, destructive confirmations (`yes`, `retry`, `Logout`, `LOGOUT`) |

### 3.2 Typography (`Route159FontFamily` & `HackFontFamily`)

Matches `static/style.sass` (`font: normal 14px/20px 'Route 159', sans-serif`) and `Macreply`:

| Style | Font Family | Weight | Size | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Body / Content** | `Route 159` | Normal / Bold | `14.sp` | `20.sp` (`3.dp` line spacing) | Entry content, author `fullName`, `@username`, chat messages, form inputs, `Load more`, `Clear out` |
| **Menu Header (`.menu`)** | `Route 159` | Bold | `14.sp` | `20.sp` | `Profile` / `Details` / `Account`, `Login`, `Register`, `About` headers |
| **Small Metadata (`.small`)** | `Route 159` | Normal | `13.sp` | `15.sp` | Entry action bar (`reply`, `edit`, `save`, `delete`, `undo`, `yes`), timestamps, profile metadata (`link`, `~ age`, `from city`, `Also on...`), validation errors |
| **Small Caps (`.small.caps`)** | `Route 159` | Normal | `12.sp` | `15.sp` | **Only** used on `MemberStatusRowView` (`NOTES`, `EDIT`, `LOGOUT`, `MESSAGE`, `FOLLOW`, `UNFOLLOW`, `FOLLOWS YOU`) |
| **Form Field Label** | `Route 159` | Normal | `12.sp` | `15.sp` | Labels above inputs in `Options`, `Login`, `Register`, `About` |
| **Composer Counter** | `Route 159` | Normal / Bold | `11.sp` | `13.sp` | Live character count inside `ComposerView` |
| **Sidebar Badge (`b`)** | `Route 159` | Bold | `9.sp` | `10.sp` | Unread notification count (`1`..`99+`) in sidebar |
| **Monospace (`code`)** | `Hack` | Normal | `12.sp` | `16.sp` | Inline code snippets (colored `accent` `#2EB8B8`) |

### 3.3 Layout & Spacing Constants

- **Permanent Sidebar Width**: `40.dp` (matching `grid-template-columns: 0 40px calc(100% - 70px) 0` in `style.sass` `@media (max-width: 594px)` and `SidebarView(isCompact: true)` in `Macreply`), separated from the main column by a `1.dp` vertical `whitesmoke` divider.
- **Main Content Column**: Fills remaining width (capped at `640.dp` max width on tablets/foldables, aligned leading).
- **Content Outer Padding**: `12.dp` horizontal padding (`10.dp` on narrow phones if needed to match `grid-column-gap: 10px`), `10.dp` top padding, `20.dp` bottom padding.
- **Row Vertical Padding**: `10.dp` top and `10.dp` bottom around every entry, user row, message row,composer, and menu header, separated by `1.dp` `whitesmoke` horizontal dividers (`SubDivider`).
- **Reply / Sublist / Incoming Chat Indentation**: `30.dp` (`Modifier.padding(start = 30.dp)`), matching `.indent { margin-left: 30px }` and `.sublist { margin-left: 30px }` in `style.sass` and `Macreply`.
- **Corner Radius**: `6.dp` for all inputs, composers, placeholder banners, submit buttons, and selected sidebar items (`border-radius: 6px`).

### 3.4 Google Material Symbols Font (`SubIcons.kt` — matching SF Symbols in `../macreply`)

All icons are rendered from the bundled Google **Material Symbols Rounded** TrueType font (`res/font/material_symbols_rounded.ttf`, `opsz=20, wght=500, FILL=0, GRAD=0`) via `MaterialSymbolsFontFamily` and `SubMaterialSymbol` / `SubHeroIcon`, mirroring how `../macreply` uses Apple's SF Symbols (`Image(systemName:)`):
- Sidebar icons render at `18.dp` inside a `32.dp × 32.dp` touch target (`6.dp` corner radius).
- When `animated = true`, `SubMaterialSymbol` runs the `1.6s` spring-bounce keyframe animation (`0.8s` active spring + `0.8s` rest pause).

---

## 4. Permanent Icon-Only Sidebar & Navigation Architecture

### 4.1 Shell Structure (`MainScaffold.kt` & `SidebarView.kt`)

1. **No Top Bar & No Drawer**:
   - Remove `TopHeaderBar`, `ModalNavigationDrawer`, and `AuthModalSheet` completely.
   - The root screen is a permanent horizontal `Row(Modifier.fillMaxSize().background(SubTheme.background).systemBarsPadding())`:
     - **Left**: `SidebarView` (`width = 40.dp`, `padding(horizontal = 4.dp, vertical = 10.dp)`).
     - **Divider**: `VerticalDivider(width = 1.dp, color = SubTheme.whitesmoke)`.
     - **Right**: Main content area containing either the active `SidebarSection` root view or the top destination of `navigationPath` (with Android system Back gesture / `BackHandler` popping `navigationPath` one level at a time).

2. **Sidebar Sections (`SidebarSection` enum in `Models.kt`)**:
   - **Authenticated Sections (12 items in exact order)**:
     1. `FEED` (`"feed"`, title `"Feed"`, icon `"bolt"`, apiPath `"/feed"`)
     2. `FOLLOWING` (`"following"`, title `"Following"`, icon `"playlist_add"`, apiPath `"/following"`)
     3. `FOLLOWERS` (`"followers"`, title `"Followers"`, icon `"playlist_play"`, apiPath `"/followers"`)
     4. `MENTIONS` (`"mentions"`, title `"Mentions"`, icon `"alternate_email"`, apiPath `"/mentions"`)
     5. `MESSAGES` (`"messages"`, title `"Messages"`, icon `"chat_bubble"`, apiPath `"/messages"`) — **Dynamic icon rule**: when the top of `navigationPath` is `NavigationDestination.Conversation`, the `MESSAGES` sidebar icon switches to `"chat"` if `conversationMessages.isNotEmpty()`, or `"sms"` if `conversationMessages.isEmpty()` (matching `sidebar.html` lines 16–22 and `SidebarView.swift`).
     6. `REPLIES` (`"replies"`, title `"Replies"`, icon `"undo"`, apiPath `"/replies"`)
     7. `SAVED` (`"saved"`, title `"Saved"`, icon `"bookmark"`, apiPath `"/saved"`)
     8. `PROFILE` (`"profile"`, title `"Profile"`, icon `"account_circle"`)
     9. `OPTIONS` (`"options"`, title `"Options"`, icon `"tune"`)
     10. `PEOPLE` (`"people"`, title `"People"`, icon `"group"`, apiPath `"/people"`)
     11. `TRENDING` (`"trending"`, title `"Trending"`, icon `"trending_up"`, apiPath `"/trending"`)
     12. `DISCOVER` (`"discover"`, title `"Discover"`, icon `"language"`, apiPath `"/discover"`)
   - **Unauthenticated Sections (6 items in exact order)**:
     1. `ABOUT` (`"about"`, title `"About"`, icon `"info"`)
     2. `LOGIN` (`"login"`, title `"Login"`, icon `"lock"`)
     3. `REGISTER` (`"register"`, title `"Register"`, icon `"person_add"`)
     4. `PEOPLE` (`"people"`, title `"People"`, icon `"group"`, apiPath `"/people"`)
     5. `TRENDING` (`"trending"`, title `"Trending"`, icon `"trending_up"`, apiPath `"/trending"`)
     6. `DISCOVER` (`"discover"`, title `"Discover"`, icon `"language"`, apiPath `"/discover"`)
   - **Default Section on Launch**:
     - Authenticated: `SidebarSection.FEED`
     - Unauthenticated: `SidebarSection.TRENDING`

3. **Sidebar Item Selection & Badge Rules (`SidebarView.kt`)**:
   - **Active/Selected Check (`isSectionSelected(section)`)**:
     - Returns `true` if `selectedSection == section && navigationPath.isEmpty()`.
     - Special case matching `sidebar.html` (`request.path.endswith('/message')`) and `Macreply`: also returns `true` for `SidebarSection.MESSAGES` when the top of `navigationPath` is `NavigationDestination.Conversation(username)`.
     - When `navigationPath` has a pushed destination (`ReplyDetail`, `Member`, `EditEntry`), no sidebar item is highlighted as `.on` (unless `Member(currentUser.username)` or `Conversation`).
   - **Visual States**:
     - **Selected (`isSelected == true`)**: Background rounded rectangle (`6.dp` radius) filled with `SubTheme.accent` (`#2EB8B8`); icon tint is `SubTheme.background` (`#FFFFFF` light / `#000000` dark); badge text (if any) is `SubTheme.background`.
     - **Unread Notification (`!isSelected && badgeCount > 0`)**: Transparent background; icon tint is `SubTheme.alert` (`#FF4500`); badge text (`SubNotifications.capBadge(badgeCount)`, e.g. `"1"`..`"99+"`) rendered in `9.sp` bold `SubTheme.alert` at top-end of the icon cell.
     - **Default (`!isSelected && badgeCount == 0`)**: Transparent background; icon tint is `SubTheme.primary` (`#000000` light / `#FFFFFF` dark).
   - **Tap Behavior (`selectSection(section)`)**:
     - Always clears `navigationPath` (`navigationPath.clear()`).
     - Sets `selectedSection = section`.
     - Increments/regenerates `feedRefreshTrigger` so tapping the currently active icon refreshes that feed/screen from page 1 and re-focuses its composer or search input.
     - Clears the corresponding notification counter locally (`followers`, `mentions`, `replies`) and triggers a background `refreshNotifications()` call.

4. **Push Navigation Stack (`NavigationDestination`)**:
   - `ReplyDetail(id: Int)`
   - `Member(username: String)`
   - `Conversation(username: String)`
   - `EditEntry(entry: SubEntry)`
   - Navigating to any `NavigationDestination` appends it to `navigationPath` inside the main column while keeping the left `SidebarView` permanently visible.

---

## 5. Exact Component & Screen Specifications (1:1 with `../macreply` & `../subreply`)

### 5.1 Rich Content & Token Parser (`ContentParser.kt` & `RichContentView`)

Mirrors `../subreply/app/filters.py` and `../macreply/Sources/Macreply/Utilities/ContentParser.swift` + `EntryRowView.swift`:

1. **`RichContentView(author: SubUser?, content: String, fontSize: TextUnit = 14.sp, singleLine: Boolean = false)`**:
   - Builds a single `AnnotatedString` paragraph using `Route159FontFamily` at `14.sp` (`lineHeight = 20.sp`).
   - If `author != null`:
     - Prepends `author.fullName + " "` in **Bold** `SubTheme.primary` (`#000000` light / `#FFFFFF` dark) with a clickable `LinkAnnotation` that calls `appState.openMember(author.username)`.
     - Note: `author.fullName` returned by the Subreply API (`serializers.py: emojize(user.full_name)`) already includes the user's emoji if set. Never render a separate emoji avatar or `@username` header above entries!
   - Appends the tokenized `content`:
     - `.text(String)` $\rightarrow$ Regular `SubTheme.primary`
     - `.mention(username)` (`@username`) $\rightarrow$ Regular `SubTheme.accent` (`#2EB8B8`), clickable $\rightarrow$ `appState.openMember(username)`
     - `.replyRef(id)` (`#123` where digits > 0) $\rightarrow$ Regular `SubTheme.accent` (`#2EB8B8`), clickable $\rightarrow$ `appState.openReply(id)`
     - `.hashtag(tag)` (`#alphanumeric`, containing at least one non-digit) $\rightarrow$ Regular `SubTheme.accent` (`#2EB8B8`), clickable $\rightarrow$ `appState.searchHashtag(tag)` (switches to `SidebarSection.DISCOVER` with `discoverSearchQuery = "#$tag"`)
     - `.link(display, url)` (`http://` or `https://`) $\rightarrow$ Regular `SubTheme.accent` (`#2EB8B8`), clickable $\rightarrow$ opens `url` via `UriHandler` / `CustomTabsIntent`. Display label strips `http(s)://`, `www.`, and trailing `/`, and truncates strings `> 21` chars to `take(18) + "..."`.
   - **Punctuation Unwrapping (`unwrapPunctuation`)**:
     - Before classifying a whitespace-delimited word, strips surrounding `(...)` or `[...]`, trailing `...`, `!!!`, `???`, `'s`, and trailing single punctuation characters (`. , ! ? : ;`), emitting the leading/trailing punctuation as plain `.text` tokens outside the clickable token.

### 5.2 Entry Item Row (`EntryItemView` in `EntryRow.kt` — `templates/common/entry.html`)

Every post/reply row in streams, threads, and profiles uses `EntryItemView`:

1. **Content Line**:
   - `RichContentView(author = entry.createdBy, content = entry.content, fontSize = 14.sp)`
2. **Small Metadata & Action Bar (`4.dp` below content, `13.sp` Route 159, lowercase)**:
   - Left side (`Row(horizontalArrangement = Arrangement.spacedBy(10.dp))`):
     - **Reply Button (`SubTheme.gray`)**:
       - Label (`entry.replyButtonLabel(isRootOverride)`):
         - Base text: `"reply"` if `replyCount == 0`, `"1 reply"` if `replyCount == 1`, `"${replyCount} replies"` if `replyCount > 1`.
         - Root thread indicator: appends `" ¬"` if `isRootOverride ?: entry.isRootThread` (where `isRootThread` is `parent == null && hasExplicitNullParent`).
       - Tapping calls `appState.openReply(entry.id)`.
     - **When `appState.isAuthenticated`**:
       - **Edit Button** (shown **only** when `isOwnEntry && entry.replyCount == 0 && !isDeleted`):
         - Text `"edit"` in `SubTheme.gray`. Tapping calls `appState.openEdit(entry)`.
       - **Save / Unsave Button** (shown **only** when `!isOwnEntry`):
         - If save failed: `"retry"` in `SubTheme.alert` (`#FF4500`).
         - Else if `isSaved`: `"unsave"` in `SubTheme.accent` (`#2EB8B8`).
         - Else: `"save"` in `SubTheme.gray`.
         - Tapping optimistically toggles `isSaved` and calls `POST /api/save/{id}` or `POST /api/unsave/{id}`.
       - **Delete Flow** (shown **only** when `(isOwnEntry || canDeleteAsRecipient) && entry.replyCount == 0`):
         - Note on `canDeleteAsRecipient`: Matches `entry.html` (`user.id in [entry.created_by_id, entry.to_user_id] and not entry.replies`) and `Macreply` — the author of a parent post can also delete a direct reply to their post as long as that reply has `0` replies.
         - Initial state: `"delete"` in `SubTheme.gray` $\rightarrow$ tapping enters confirmation state.
         - Confirmation state: `"undo"` in `SubTheme.accent` (`#2EB8B8`) and `"yes"` (or `"retry"` on failure) in `SubTheme.alert` (`#FF4500`).
         - Deleted state (after `POST /api/delete/{id}` returns `{"status": "deleted"}`): static text `"deleted"` in `SubTheme.silver` (and hides `"edit"`).
   - Right side (`Spacer(Modifier.weight(1f))`):
     - **Timestamp Button (`SubTheme.silver`, `13.sp`)**:
       - Text: `if (entry.edited) "··· ${entry.timestamp}" else entry.timestamp` (three middle dots `"··· "` prefix when `entry.edited == true`, matching `{% if entry.edited %}&middot;&middot;&middot; {% endif %}{{ entry.timestamp }}` in `entry.html`).
       - Tapping calls `appState.openReply(entry.id)`.

### 5.3 Stream Entry & Inline Parent Preview (`StreamEntryView` in `EntryRow.kt` — `templates/common/stream.html`)

Used in `Feed`, `Mentions`, `Replies`, `Saved`, `Trending`, `Discover`, and `Member` posts:

1. **Case A: Entry is a Reply (`entry.parent != null`)**:
   - **Collapsed Inline Parent Preview**:
     - Given `previousParentID: Int?` (the `parent.id` of the immediately preceding entry in the list):
     - If `previousParentID != entry.parent.id`:
       - If `index > 0`, render `SubDivider()` at full width.
       - Render `InlineParentRowView(entry = entry.parent)`: a single-line (`singleLine = true`, `maxLines = 1`, `TextOverflow.Ellipsis`), `alpha = 0.5f` `RichContentView(author = parent.createdBy, content = parent.content)` with `10.dp` vertical padding. Tapping anywhere on the row calls `appState.openReply(parent.id)`.
   - **Indented Reply**:
     - Inside a container indented by `30.dp` (`Modifier.padding(start = 30.dp)`):
       - Render `SubDivider()`.
       - Render `EntryItemView(entry = entry, isRootOverride = false, canDeleteAsRecipient = (currentUser == entry.parent.createdBy))`.
2. **Case B: Entry is a Root Thread (`entry.parent == null`)**:
   - If `index > 0`, render `SubDivider()` at full width.
   - Render `EntryItemView(entry = entry, isRootOverride = true)`.
   - **Sublist Children (`entry.streamKids`)**:
     - `streamKids` is `entry.kids?.take(5)?.reversed() ?: emptyList()` (matching `entry.kids[:5]|reverse` in `stream.html`).
     - If `streamKids.isNotEmpty()`, inside a `.sublist` container indented by `30.dp` (`Modifier.padding(start = 30.dp)`):
       - For each `kid` in `streamKids`:
         - Render `SubDivider()`.
         - Render `EntryItemView(entry = kid, isRootOverride = false, canDeleteAsRecipient = (currentUser == entry.createdBy))`.

### 5.4 Composer, Placeholder Banner & Search Query Bar (`ComposerBar.kt` — `templates/input/*`)

1. **`ComposerView` (`templates/input/thread.html`, `reply.html`, `message.html`, `edit.html`)**:
   - Single-paragraph auto-expanding text input (`14.sp` Route 159, `SubTheme.primary` text, `SubTheme.gray` placeholder).
   - Background: `SubTheme.redsmoke` when `text.length > 640`, otherwise `SubTheme.whitesmoke`; `6.dp` corner radius; `10.dp` horizontal / `6.dp` vertical padding (`minHeight = 32.dp`).
   - Hard character limit: clamps input at `720` characters (`maxlength="720"`).
   - Live character counter: when `text.isNotEmpty()`, displays `"${text.length}"` at bottom-end inside the field (`11.sp`, bold `SubTheme.alert` if `> 640`, otherwise regular `SubTheme.silver`).
   - Keyboard submission: pressing `Enter` / `Return` / `ImeAction.Send` strips any newlines (`replace("\n", " ")`) and immediately calls `submitIfReady()` when `text.trim().isNotEmpty() && text.length <= 640 && !isSubmitting`. **No separate Send/Submit button is rendered next to the composer.**
   - Validation error display: if the server returns field/content errors (`APIError.Validation`), displays the error text (`13.sp`, `SubTheme.alert` `#FF4500`) directly below the field.

2. **`PlaceholderBannerView` (`templates/input/thread.html`, `reply.html`, `message.html`)**:
   - Rounded box (`SubTheme.whitesmoke`, `6.dp` radius, `10.dp` horizontal / `6.dp` vertical padding, `minHeight = 32.dp`).
   - Renders `14.sp` text in `SubTheme.gray` on the left and an optional vertically flipped `"arrow-uturn-left"` icon (`16.dp`, `scaleY = -1f`, `SubTheme.gray`) on the right.
   - Auth prompt variant (`Login or register your account to share` / `Login or register your account to reply`): `"Login"` and `"register"` are rendered in `SubTheme.primary` and are clickable to navigate to `SidebarSection.LOGIN` and `SidebarSection.REGISTER`.

3. **`SearchQueryBar` (`templates/input/query.html`)**:
   - Used **only** at the top of `SidebarSection.PEOPLE` (`placeholder = "Find people"`) and `SidebarSection.DISCOVER` (`placeholder = "Search content"`).
   - Rounded box (`SubTheme.whitesmoke`, `6.dp` radius) with `14.sp` single-line text field on the left (`10.dp` horizontal / `6.dp` vertical padding), an `"xmark"` clear button (`12.dp`, `SubTheme.gray`) when `text.isNotEmpty()`, and a right-aligned `"magnifying-glass"` icon button (`16.dp` icon in a `30.dp × 29.dp` touch area, `SubTheme.primary`).
   - Submitting via `Return`/`Search` or tapping the magnifying glass triggers `onSearch()`.

### 5.5 Feed Screen (`FeedScreen.kt` — `Feed`, `Mentions`, `Replies`, `Saved`, `Trending`, `Discover`)

1. **Top Input Area (rendered inside the scrollable column before entries)**:
   - **`SidebarSection.FEED`**:
     - If authenticated: `ComposerView(placeholder = "Say something")` (`POST /api/thread`), followed by `SubDivider()`.
     - If unauthenticated: `PlaceholderBannerView("Login or register your account to share", showReplyIcon = false)`, followed by `SubDivider()`.
   - **`SidebarSection.DISCOVER`**:
     - `SearchQueryBar(text = discoverSearchQuery, placeholder = "Search content")`, followed by `SubDivider()`.
   - **`MENTIONS`, `REPLIES`, `SAVED`, `TRENDING`**:
     - No top bar, no title header, and no composer — the list starts immediately with the first entry!
2. **Entries List**:
   - Pull-to-refresh (`PullToRefreshBox`) wrapping a `LazyColumn` (with `contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 20.dp)`).
   - Renders each `SubEntry` using `StreamEntryView(entry, previousParentID, isFirstInList)`.
3. **Pagination (`LoadMoreRow`)**:
   - Entry feeds use `pageSize = 16`. When `entries.size >= 16 && entries.size % 16 == 0`, renders `SubDivider()` + centered `"Load more"` (`14.sp`, `SubTheme.gray`; or `"Retry"` in `SubTheme.alert` if pagination failed).

### 5.6 Reply Detail & Edit Entry Screens (`ReplyDetailScreen.kt` — `templates/pages/reply.html` & `edit.html`)

1. **`ReplyDetailView(entryID: Int)` (`GET /api/reply/{id}`)**:
   - **Ancestors & Focal Entry**:
     - Renders each `ancestor` in `detail.ancestors` in chronological order (`EntryItemView(entry = ancestor, isRootOverride = (index == 0))`), separated by `SubDivider()`.
     - Renders `detail.entry` (`mainEntry`) with `isRootOverride = detail.ancestors.isEmpty()` and `canDeleteAsRecipient = (detail.ancestors.lastOrNull()?.createdBy == currentUser)`.
   - **Reply Composer / Placeholder Section** (separated by `SubDivider()` above and, if `kids` non-empty, below):
     - If `!isAuthenticated`: `PlaceholderBannerView("Login or register your account to reply", showReplyIcon = true)`.
     - Else if `mainEntry.createdBy == currentUser`:
       - If `(mainEntry.replies ?: 0) == 0 && detail.kids.isEmpty()`: `PlaceholderBannerView("No reply yet", showReplyIcon = true)`.
       - Else: `PlaceholderBannerView("Reply back to them", showReplyIcon = true)`.
     - Else if `hasDuplicateReply` (`detail.duplicate == true || detail.kids.any { it.createdBy == currentUser }`):
       - `PlaceholderBannerView("Share reply #${mainEntry.id} anywhere", showReplyIcon = true)`.
     - Else:
       - `ComposerView(placeholder = "Reply to @${mainEntry.createdBy.username}")` (`POST /api/reply/{id}`).
   - **Descendant Threads (`ThreadNodeView` — `templates/common/threads.html`)**:
     - For each `reply` in `detail.kids`:
       - Renders `EntryItemView(entry = reply, isRootOverride = false, canDeleteAsRecipient = (currentUser == mainEntry.createdBy))`.
       - If `!reply.kids.isNullOrEmpty()`:
         - Inside an indented container (`Modifier.padding(start = 30.dp)`):
           - For each `nestedKid` in `reply.kids`:
             - Renders `SubDivider()` + `EntryItemView(entry = nestedKid, isRootOverride = false, canDeleteAsRecipient = (currentUser == reply.createdBy))`.

2. **`EditEntryView(entry: SubEntry)` (`templates/pages/edit.html`)**:
   - If `entry.parent != null`: renders `EntryItemView(entry = entry.parent, isRootOverride = (entry.parent.parent == null))` + `SubDivider()`.
   - Renders `EntryItemView(entry = entry)` + `SubDivider()`.
   - Renders `ComposerView(placeholder = "Edit content", initialText = entry.content)` (`PATCH /api/edit/{id}`). On success, triggers refresh and pops `navigationPath`.

### 5.7 Member Profile & User Directory (`MemberScreen.kt`, `PeopleListScreen.kt` — `user.html`, `status.html`, `member.html`, `people.html`)

1. **`UserRowView(member: SubUser, isMemberHeader: Boolean)` (`templates/common/user.html`)**:
   - **Line 1 (Header & Inline Action)**:
     - Left:
       - Bold `14.sp` `member.fullName` in `SubTheme.primary` (tapping opens `Member(member.username)` when `!isMemberHeader`).
       - If `isMemberHeader == true`: appends `" @${member.username}"` in bold `14.sp` `SubTheme.gray`.
       - If `!isMemberHeader && appState.isAuthenticated`:
         - Appends `" • "` in `14.sp` `SubTheme.gray`.
         - If `member == currentUser`: `"Logout"` in `14.sp` `SubTheme.alert` (`#FF4500`) $\rightarrow$ `appState.logout()`.
         - Else: `"Follow"` (`SubTheme.accent`), `"Unfollow"` (`SubTheme.gray`), or `"Retry"` (`SubTheme.alert`) $\rightarrow$ calls `POST /api/follow/{username}` or `POST /api/unfollow/{username}`.
     - Right (`Spacer(Modifier.weight(1f))`):
       - `13.sp` `SubTheme.silver` timestamp:
         - When `isMemberHeader == true`: `ContentParser.timeAgo(from = if (isSelf) member.createdAt else member.seenAt)` (fallback to `member.timestamp`).
         - When `isMemberHeader == false`: `member.timestamp.ifEmpty { ContentParser.timeAgo(from = member.createdAt) }`.
   - **Line 2 (Bio — shown if `member.description.isNotEmpty()`)**:
     - `RichContentView(author = null, content = member.description, fontSize = 14.sp)` (`4.dp` top spacing).
   - **Line 3 (Metadata — shown if `member.link`, `member.birthday`, or `member.location` is non-empty)**:
     - `Row(horizontalArrangement = Arrangement.spacedBy(5.dp))` in `13.sp` `SubTheme.gray` (`4.dp` top spacing):
       - If `member.link` has a valid hostname (`ContentParser.hostname(from = member.link)`): clickable hostname in `SubTheme.gray` that opens `member.link` externally.
       - If `member.birthday` yields a valid age (`ContentParser.age(from = member.birthday)`): `"~ ${age}y old"` in `SubTheme.gray`.
       - If `member.cityLabel` (`member.location.split(",").firstOrNull()?.trim()`) is non-empty: `"from "` + clickable `cityLabel` in `SubTheme.gray` that switches to `SidebarSection.PEOPLE` with `peopleSearchQuery = cityLabel`.

2. **`MemberView(username: String)` (`templates/pages/member.html` & `templates/common/status.html`)**:
   - **Row 1**: `UserRowView(member = member, isMemberHeader = true)`
   - **Row 2 (`MemberLinksView` — shown if `member.allLinks.isNotEmpty() || member.donated > 0`)**:
     - Preceded by `SubDivider()`.
     - Renders `13.sp` `SubTheme.gray` inline text:
       - If `member.allLinks.isNotEmpty()`: `"Also on "` + comma-separated links (with `" and "` before the last item and trailing `"."`), where each platform name (`Behance`, `Dribbble`, `Figma`, `GitHub`, `Instagram`, `LinkedIn`, `PayPal`, `Phone`, `Medium`, `Reddit`, `Spotify`, `Steam`, `Substack`, `Telegram`, `TikTok`, `Twitch`, `Tumblr`, `VK`, `X`, `YouTube`) is clickable to open its URL (or `tel:` for `Phone` when `phone["code"]` and `phone["number"]` are non-empty).
       - If `member.donated > 0`: `"Donated $${member.donated.toInt()}."`
   - **Row 3 (`MemberStatusRowView` — `.small.caps`, `12.sp` uppercase, `Arrangement.spacedBy(10.dp)`)**:
     - Preceded by `SubDivider()`.
     - If `isSelf`:
       - `"NOTES"` (`SubTheme.accent` `#2EB8B8`) $\rightarrow$ `appState.openConversation(member.username)`
       - `"EDIT"` (`SubTheme.gray`) $\rightarrow$ `appState.selectSection(SidebarSection.OPTIONS)`
       - `"LOGOUT"` (`SubTheme.alert` `#FF4500`) $\rightarrow$ `appState.logout()`
     - If another user:
       - `"MESSAGE"` (`SubTheme.accent` `#2EB8B8`) $\rightarrow$ if authenticated, `appState.openConversation(member.username)`; else `appState.selectSection(SidebarSection.LOGIN)`
       - `"FOLLOW"` (`SubTheme.accent`) / `"UNFOLLOW"` (`SubTheme.gray`) / `"RETRY"` (`SubTheme.alert`) $\rightarrow$ toggles follow state (or navigates to `LOGIN` if unauthenticated)
       - If `member.follows == true`: static `"FOLLOWS YOU"` in `SubTheme.silver`.
   - **Row 4 (Member's Posts)**:
     - Preceded by `SubDivider()`.
     - Renders `entries` (`GET /api/{username}/posts?p={page}`) via `StreamEntryView`, with `LoadMoreRow` (`pageSize = 16`).

3. **`PeopleListView(section: SidebarSection)` (`People`, `Following`, `Followers`)**:
   - If `section == SidebarSection.PEOPLE`: renders `SearchQueryBar(text = peopleSearchQuery, placeholder = "Find people")` + `SubDivider()` at the top.
   - Renders each `SubUser` using `UserRowView(member = user, isMemberHeader = false)` separated by `SubDivider()`.
   - Uses `pageSize = 24` for pagination (`users.size >= 24 && users.size % 24 == 0`).

### 5.8 Messages Inbox & 1-on-1 Conversation (`MessagesScreen.kt` — `messages.html` & `message.html`)

1. **`MessagesInboxView` (`GET /api/messages`)**:
   - No top header or search bar.
   - Each `SubChat` item renders `MessageInboxRow`:
     - `RichContentView(author = chat.createdBy, content = chat.content, fontSize = 14.sp)`
     - `.small` row (`13.sp`, `4.dp` top spacing, `Arrangement.spacedBy(10.dp)`):
       - `"message"` button (`SubTheme.gray`) $\rightarrow$ `appState.openConversation(chat.createdBy.username)`
       - `"read"` (`SubTheme.silver`) if `chat.seen`, else `"unread"` (`SubTheme.accent` `#2EB8B8`)
       - Right-aligned `chat.timestamp` (`SubTheme.silver`) $\rightarrow$ `appState.openConversation(chat.createdBy.username)`
   - Paginates with `pageSize = 16`.

2. **`ConversationView(username: String)` (`GET /api/{username}/message`)**:
   - **Blocked / Waiting Check (`isBlockedWaitingForResponse`)**:
     - True when `!isSelfConversation && (isBlocked || (messages.isNotEmpty() && messages.any { it.createdBy.username.equals(currentUser.username, true) } && !messages.any { !it.createdBy.username.equals(currentUser.username, true) }))`.
   - **Top Input Area**:
     - If `isBlockedWaitingForResponse`: `PlaceholderBannerView("Please wait for @$username to respond", showReplyIcon = false)`.
     - Else: `ComposerView(placeholder = if (isSelfConversation) "Message yourself" else "Message @$username")` (`POST /api/{username}/send`).
   - **Messages List**:
     - For each `chat` in `messages` (separated by `SubDivider()`):
       - `isIncomingFromPartner = (chat.toUser == currentUser && chat.createdBy != currentUser)`
       - **Incoming message (`isIncomingFromPartner == true`)**:
         - Indented by `30.dp` (`Modifier.padding(start = 30.dp)`).
         - Renders `RichContentView(author = chat.createdBy, content = chat.content, fontSize = 14.sp)`.
         - **No `.small` metadata row** (matching `message.html` line 21: `{% if entry.created_by_id == user.id %}`).
       - **Outgoing message (`isIncomingFromPartner == false`)**:
         - Full width (`0.dp` indent).
         - Renders `RichContentView(author = chat.createdBy, content = chat.content, fontSize = 14.sp)`.
         - Renders `.small` metadata row (`13.sp`, `4.dp` top spacing):
           - `"read"` (`SubTheme.silver`) if `chat.seen`, else `"unread"` (`SubTheme.accent`)
           - Unsend flow: `"unsend"` (`SubTheme.gray`) $\rightarrow$ `"undo"` (`SubTheme.accent`) + `"yes"` / `"retry"` (`SubTheme.alert`) $\rightarrow$ calls `POST /api/unsend/{id}` and shows static `"unsent"` (`SubTheme.silver`).
           - Right-aligned `chat.timestamp` (`SubTheme.silver`).
   - **Bottom Row (`Load more` or `Clear out`)**:
     - If `canLoadMore` (`messages.size >= 16 && messages.size % 16 == 0`): centered `"Load more"` / `"Retry"`.
     - Else if `!isBlockedWaitingForResponse && messages.isNotEmpty()`:
       - Centered `"Clear out"` (`14.sp`, `SubTheme.gray`) $\rightarrow$ tapping shows `"Undo"` (`SubTheme.accent`) + `" — "` (`SubTheme.silver`) + `"Yes"` / `"Retry"` (`SubTheme.alert`) $\rightarrow$ confirming calls `POST /api/{username}/clear`, empties `messages`, and displays `"Cleared"` (`14.sp`, `SubTheme.gray`).

### 5.9 Menu Header, Options, Login, Register & About (`OptionsScreen.kt`, `AuthScreens.kt`)

1. **Menu Header (`MenuHeaderView` — `.menu` in `style.sass`)**:
   - Horizontal row (`Arrangement.spacedBy(10.dp)`, `padding(vertical = 10.dp)`).
   - Each tab item is `14.sp` **Bold** Route 159:
     - Active tab: `SubTheme.primary` text with a `10.dp` wide × `2.dp` high `SubTheme.accent` (`#2EB8B8`) bar aligned to the bottom-start under the first letter (`offset(y = 3.dp)`).
     - Inactive tab: `SubTheme.silver` text.

2. **`OptionsView` (`OptionsScreen.kt` — `templates/pages/profile.html` & `details.html`)**:
   - Top `MenuHeaderView` with 3 tabs: `"Profile"`, `"Details"`, `"Account"`, followed by `SubDivider()`.
   - **`Profile` Tab (`PATCH /api/profile`)**:
     - Row 1 (2 columns, `10.dp` gap): `Username*` + `Email address*`
     - Row 2 (2 columns, `10.dp` gap): `First name*` + `Last name`
     - Row 3 (3 columns, `10.dp` gap): `Emoji` (`64.dp` fixed width, placeholder `":)"`) + `Birthday` (`108.dp` fixed width, placeholder `"Y-M-D"`) + `Location` (flexible width, placeholder `"City, Country"`)
     - Row 4 (full width): `Link` (placeholder `"https://"`)
     - Row 5 (full width): `Description` (`2..5` lines, background turns `SubTheme.redsmoke` when `> 640` chars)
     - Row 6: status/error message on left + right-aligned `SubSubmitButton(title = "Update")` (`SubTheme.accent` bg, `SubTheme.background` bold `14.sp` text, `6.dp` radius).
   - **`Details` Tab (`PATCH /api/details`)**:
     - Row 1 (2 columns): `GitHub` + `Instagram`
     - Row 2 (2 columns): `LinkedIn` + `Reddit`
     - Row 3 (3 columns): `Code` (`64.dp` width, placeholder `"+1"`) + `Phone number` + `PayPal`
     - Row 4 (2 columns): `Spotify` + `X`
     - Row 5: status/error message on left + right-aligned `SubSubmitButton(title = "Update")`.
   - **`Account` Tab**:
     - `API Base URL` field + right-aligned `Save Endpoint` button
     - `Notifications polling (minutes)` field + right-aligned `Save Interval` button
     - `Signed in as @username` + right-aligned `Logout` button (`SubTheme.alert` `#FF4500`).

3. **`LoginView` (`AuthScreens.kt` — `templates/pages/login.html`)**:
   - `MenuHeaderView(["Login"])` + `SubDivider()`
   - 2-column row (`10.dp` gap): `Username or email*` + `Password*` (password masked)
   - Validation errors (`username`, `password`, or general error) in `13.sp` `SubTheme.alert`
   - Right-aligned `SubSubmitButton(title = "Login")` (`POST /api/login`).

4. **`RegisterView` (`AuthScreens.kt` — `templates/pages/register.html`)**:
   - `MenuHeaderView(["Register"])` + `SubDivider()`
   - Row 1 (2 columns): `Username*` + `Email address*`
   - Row 2 (2 columns): `Password*` + `Password confirmation*`
   - Row 3 (2 columns): `First name*` + `Last name`
   - Row 4 (3 columns): `Emoji` (`64.dp`, `":)"`) + `Birthday` (`108.dp`, `"Y-M-D"`) + `Location` (`"City, Country"`)
   - Bottom row: `"You agree with Subreply's "` (`13.sp` `SubTheme.gray`) + clickable `"Privacy"` (`SubTheme.accent`) + `", "` + clickable `"Safety"` (`SubTheme.accent`) + `" and "` + clickable `"Terms"` (`SubTheme.accent`) + `"."` on the left, and `SubSubmitButton(title = "Register")` on the right (`POST /api/register`).

5. **`AboutView` (`AuthScreens.kt` — `templates/pages/about.html`)**:
   - `MenuHeaderView(["About"])` + `SubDivider()`
   - Intro paragraph: `"Subreply is an independent social network created and maintained by @lucian. The minimal interface is designed to feel familiar while staying completely out of your way."` (`14.sp` Route 159) + `SubDivider()`
   - Section `"Features and limitations"` (`14.sp` bold `SubTheme.primary`) + `SubDivider()` + numbered items `1.` to `10.` (matching `about.html` and `AuthViews.swift` verbatim, separated by `SubDivider()`).
   - Section `"Keeping things private"` (`14.sp` bold `SubTheme.primary`) + `SubDivider()` + numbered items `1.` to `3.` (separated by `SubDivider()`).
   - Policy links row (`Privacy`, `Safety`, `Terms`, `API`) + `API Server URL` configuration form (`Save Server`).

---

## 6. Step-by-Step Rewrite Plan (File-by-File)

### Phase 1: Theme, Icons, Models & Parser Alignment
1. **`ui/theme/SubTheme.kt`**:
   - Replace `DarkSubPalette` / `LightSubPalette` colors with the exact tokens from `static/style.sass` and `MainSplitView.swift` (`background`, `whitesmoke`, `redsmoke`, `gainsboro`, `silver`, `gray`, `primary`, `accent = #2EB8B8`, `alert = #FF4500`).
   - Add reusable `SubDivider()` (`HorizontalDivider(thickness = 1.dp, color = SubTheme.whitesmoke)`), `SubSubmitButton`, and `SubTextField` components matching `OptionsView.swift`.
2. **`ui/theme/SubIcons.kt`**:
   - Verify all 18 Heroicons from `templates/sidebar.html` and `templates/svg/SPEC.md` (`bolt`, `bars-arrow-up`, `bars-arrow-down`, `at-symbol`, `chat-bubble-left`, `chat-bubble-bottom-center-text`, `chat-bubble-left-ellipsis`, `arrow-uturn-left`, `bookmark`, `user-circle`, `adjustments-horizontal`, `information-circle`, `lock-closed`, `receipt-refund`, `clipboard-document-check`, `users`, `arrow-trending-up`, `globe-alt`, `magnifying-glass`, `xmark`) render at `16.dp` with `2.dp` stroke.
3. **`model/Models.kt`**:
   - Replace the old `SidebarDestination` / `SidebarSection` enums with the exact 13-case `SidebarSection` enum (`FEED`, `FOLLOWING`, `FOLLOWERS`, `MENTIONS`, `MESSAGES`, `REPLIES`, `SAVED`, `PROFILE`, `OPTIONS`, `ABOUT`, `LOGIN`, `REGISTER`, `PEOPLE`, `TRENDING`, `DISCOVER`) and `NavigationDestination` sealed interface (`ReplyDetail`, `Member`, `Conversation`, `EditEntry`) from `Macreply/Models/Models.swift`.
   - Ensure `SubUser` (`fullName`, `cityLabel`, `allLinks`, `donated`, `followed`, `follows`), `SubEntry` (`hasExplicitNullParent`, `isRootThread`, `streamKids`, `replyCount`, `replyButtonLabel`), `SubChat`, and `SubNotifications` (`capBadge`) match `Macreply/Models/Models.swift` 1:1.
4. **`util/ContentParser.kt`**:
   - Ensure `ContentParser.parse(text)`, `unwrapPunctuation`, `formatLink`, `hostname(from)`, `age(from)`, and `timeAgo(from)` match `Macreply/Utilities/ContentParser.swift` and `subreply/app/filters.py` 1:1.

### Phase 2: State & Networking Cleanup
5. **`network/APIClient.kt` & `state/AppStateViewModel.kt`**:
   - Align `AppStateViewModel` with `Macreply/Services/AppState.swift`:
     - `selectedSection: SidebarSection` (defaults to `.FEED` when authenticated, `.TRENDING` when unauthenticated)
     - `navigationPath: List<NavigationDestination>` (`openReply`, `openMember`, `openConversation`, `openEdit`, `searchHashtag`, `searchPeopleByCity`, `popBack`)
     - `activeConversationHasMessages: Boolean?` (drives the dynamic `MESSAGES` sidebar icon when viewing a conversation)
     - `feedRefreshTrigger: Int` (incremented on sidebar re-selection or post/edit submission)
     - Notification badge clearing on section entry (`FOLLOWERS`, `MENTIONS`, `REPLIES`, and `Conversation`) + background polling (`GET /api/notifications`).

### Phase 3: Permanent Icon-Only Sidebar & Navigation Shell
6. **`ui/navigation/SidebarDrawer.kt` & `ui/navigation/MainScaffold.kt`**:
   - Rewrite `SidebarDrawer.kt` into `SidebarView`: a permanent `40.dp`-wide vertical icon column (`Modifier.width(40.dp).fillMaxHeight()`) rendering `visibleSections` (`authenticatedSections` or `unauthenticatedSections`) with `#2EB8B8` active pill, `#FF4500` unread icon/badge, and dynamic `messages` icon.
   - Rewrite `MainScaffold.kt` to remove `TopHeaderBar`, `ModalNavigationDrawer`, and `AuthModalSheet`, replacing them with a permanent `Row` (`SidebarView` + `1.dp` `whitesmoke` vertical divider + main content container with `BackHandler` for `navigationPath`).

### Phase 4: Core Components & Screens (1:1 with `Macreply` Views)
7. **`ui/screens/EntryRow.kt`**:
   - Rewrite to provide `RichContentView`, `InlineParentRowView`, `EntryItemView`, and `StreamEntryView` matching `Macreply/Views/EntryRowView.swift` and `templates/common/entry.html` + `stream.html` 1:1.
8. **`ui/screens/ComposerBar.kt`**:
   - Rewrite to provide `ComposerView`, `PlaceholderBannerView`, and `SearchQueryBar` matching `Macreply/Views/ComposerView.swift` and `templates/input/*` 1:1.
9. **`ui/screens/FeedScreen.kt`**:
   - Rewrite to match `Macreply/Views/FeedView.swift` (`Feed`, `Mentions`, `Replies`, `Saved`, `Trending`, `Discover` with `pageSize = 16` and `PullToRefreshBox`).
10. **`ui/screens/ReplyDetailScreen.kt`**:
    - Rewrite to provide `ReplyDetailView`, `ThreadNodeView`, and `EditEntryView` matching `Macreply/Views/ReplyDetailView.swift` and `templates/pages/reply.html` + `edit.html` 1:1.
11. **`ui/screens/MemberScreen.kt` & `ui/screens/PeopleListScreen.kt`**:
    - Rewrite to provide `UserRowView`, `MemberLinksView`, `MemberStatusRowView`, `MemberView`, and `PeopleListView` (`pageSize = 24`) matching `Macreply/Views/MemberView.swift` and `PeopleListView.swift` 1:1.
12. **`ui/screens/MessagesScreen.kt`**:
    - Rewrite to provide `MessagesInboxView` and `ConversationView` matching `Macreply/Views/MessagesView.swift` and `templates/pages/messages.html` + `message.html` 1:1.
13. **`ui/screens/OptionsScreen.kt` & `ui/screens/AuthScreens.kt`**:
    - Rewrite to provide `MenuHeaderView`, `OptionsView` (`Profile`, `Details`, `Account`), `LoginView`, `RegisterView`, and `AboutView` matching `Macreply/Views/OptionsView.swift` and `AuthViews.swift` 1:1.

### Phase 5: Verification & Testing
14. **Unit & Build Verification**:
    - Update unit tests in `app/src/test/java/com/lucianmarin/sub/` (`ContentParserTest.kt`, `ModelsSerializationTest.kt`) to cover `SubUser`, `SubEntry`, `SubChat`, `SidebarSection`, and `ContentParser`.
    - Run `./gradlew testDebugUnitTest assembleDebug` to verify the build and tests pass cleanly.

