# Manual Testing — Desktop Moderation & Safety

Branch: `worktree-feat-desktop-moderation-safety`
Run: `./gradlew :desktopApp:run`

Prereq: log in with a **writeable** account (local nsec or bunker). For mute-list
sync tests, use an account that already has a kind-10000 mute list from mobile.

## Enforcement (the bug fix)

| # | Scenario | Expected |
|---|----------|----------|
| E1 | Account has a mobile-set mute on user X → open Following/Global | X's notes do NOT appear |
| E2 | Right-click a note → **Mute user** | Author's notes vanish from the feed **without restart** (may take a beat while it publishes) |
| E3 | Mute a user you follow | Their notes are hidden in Following too (mute wins over follow) |
| E4 | Open a thread where a reply author is muted | Muted reply hidden; thread root still shows |
| E5 | Open the muted user's profile | (v1) profile still renders — enforcement is feed/notification/DM-scoped, not profile |
| E6 | Notifications tab with a muted user reacting/replying | Their notifications are hidden |
| E7 | DM conversation list with a muted user | Their conversation is filtered out |
| E8 | Hidden word present in mute list (from mobile) → note containing it | Note is filtered |
| E9 | Read-only (npub) account | No crash; public mute portion still enforced; no Mute/Report items in menu |

## Report (NIP-56)

| # | Scenario | Expected |
|---|----------|----------|
| R1 | Right-click a note → **Report…** | Dialog opens with reason radios + comment field |
| R2 | Pick a reason → **Report** | kind-1984 published to relays (verify via `amy` or a relay log); no local hide |
| R3 | **Block & report** | Report published AND author muted (notes vanish) |
| R4 | Read-only account | No Report item in the menu |

## Content warning (NIP-36)

| # | Scenario | Expected |
|---|----------|----------|
| C1 | Feed contains a note tagged `content-warning` / NSFW | Renders collapsed: "Sensitive content" + reason + **Show** |
| C2 | Click **Show** | Note body reveals; stays revealed while scrolling (per-note) |
| C3 | Thread root that is CW-tagged | Auto-revealed (forceReveal) — you navigated into it |

> Note: the "always show sensitive content" account setting defaults to blur.
> A settings toggle to flip it is a deferred follow-up (see plan Non-goals).

## Regression

| # | Scenario | Expected |
|---|----------|----------|
| X1 | Feeds with no mutes | Everything renders as before; no perf regression scrolling |
| X2 | Spam-collapse (hashtag spam) | Still works; CW + spam don't conflict on the same note |

## Automated coverage
`./gradlew :desktopApp:test --tests "com.vitorpamplona.amethyst.desktop.filters.DesktopMuteEnforcementTest"`
covers E1/E3/E8 at the filter level (muted author hidden, hidden even if followed,
hidden-word note dropped, clean notes pass).
