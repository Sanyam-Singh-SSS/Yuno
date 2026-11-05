# Yuno — keep on Skipii upstream merges

Edit when you want something new protected. Cursor rule: `.cursor/rules/yuno-upstream-preserve.mdc`.

## Product & branding

- App name **Yuno**; package **`com.yuno`**.
- Self-update: users always get APKs from **`danielkanan/Yuno`** — merge upstream `Updater.kt` fixes freely, never point releases at Skipii.
- No upload/Create; tabs Home · Subscriptions · Shorts · Search · Library (Shorts optional).

## Look & feel (keep on merge)

- Liquid glass floating tab bar + **liquid glass setting** on Settings page.
- System / Light / Dark only — **no** colour palette chips or wallpaper themes.
- **Settings page** (`Settings.kt`) — not a legacy account settings sheet; don’t re-add appearance chip rows.
- Icons, font weights, `FadingLine` truncation, sharp thumbnails.
- Player: **on-video** controls + **edge scrub**; **no** player bar on tabs; **no** “controls in the bar” setting.
- **Mini-player:** glass accessory, fading titles, morph — keep Yuno.
- **Shorts:** keep Yuno’s glass rail. Drag-to-seek, the double-tap thumb burst, and the progress line are copied from upstream `Shorts.kt`, not rewritten.
- **Player:** on-video controls are upstream `VideoView` (tiles, seek step, fullscreen cards, tap waits so a double tap only seeks). Seek icons are upstream `seekIcon`, including 15/20/25, and they follow the setting. Zoom scales the picture only; captions stay on the unzoomed box. Portrait edge scrub stays. No PlayerBar and no “controls in the bar” setting.
- **Pages:** channel and Settings use the app background (`c.background`), the same as the rest of the shell.

## Shell & motion (keep Yuno)

- Mini-player ↔ player morph, headers, vignettes, no ripples, `pressable`.

## Your changes — ask only if an update touches them

When you change or add something in Yuno (a layout, a screen, a setting, a behavior), write it here **and** in `.cursor/rules/yuno-upstream-preserve.mdc` in the same turn, without being asked. One line: what changed, and where it lives. Leave it in this list until you decide.

On a later Skipii merge, ask only if that update **conflicts with or rewrites** the change. If Skipii does not touch it, keep it and do not ask. When it does collide, ask: **keep** it, or **let Skipii replace it**?

- **Keep** — move the line into **Look & feel** / **Always keep**.
- **Let Skipii replace it** — delete the line and take upstream there.

## Ask on each merge

- Playback, extraction, bot-check, SponsorBlock **service**, Media3/NewPipe/Gradle deps.
- Large Shorts bank changes.
- A line under **Your changes** only when this update conflicts with it or rewrites that spot.

## OK to follow upstream

| Topic | Your choice |
|--------|----------------|
| Audio-only stage | Upstream OK |
| Queue clear animation | Upstream OK |
| Updater implementation | Upstream OK (releases stay **Yuno** repo) |
| SponsorBlock settings UI | Upstream OK |
| Account / settings structure | Keep Yuno page + liquid glass; no colour chips |
| Related list wording | Upstream OK |
| RTL / `supportsRtl` / bidi text | Upstream OK |
| Playful.kt / Comments panel | Upstream OK unless veto |
| Shorts disk bank | Upstream OK (ask if large diff) |
| Home category chips | Upstream `HomeChips` / `UnderlineTabs` — underlined words, not filled chips |
| Subscriptions channel tap | Opens the channel page |
| Hold menu | iOS-style hold menu only; new video actions go there |
| Playback settings rows | Skip step, chapters stay open, auto PiP, zoom to fill, music speed — must be visible in Settings › Playback |
| Fullscreen zoom | Pinch and the setting both fill the screen |

## Never remake a Skipii feature

Port the upstream code (`com.decio.skipii` → `com.yuno`, user-facing Skipii → Yuno). Do not write a new gesture, animation, layout, or screen that only “does the same thing.” Chrome this file says to keep stays; the upstream behavior is wired into it.

## End of every upstream update

Do this without being asked, after the merge builds. Two lists, one number sequence:

1. **Left out** — Skipii changes the user would notice that Yuno did not ship.
2. **Added** — Skipii changes the user would notice that did land. Continue the numbers (if left-out ends at 7, added starts at 8).

You answer `1. agree`, `2. disagree`, and so on. Then update this file and `.cursor/rules/yuno-upstream-preserve.mdc`:

| List | Agree | Disagree |
|------|--------|----------|
| Left out | Stay out. Add to **Always keep** / Look & feel if it is not already written there. | Port it in. Move it to **OK to follow upstream** and drop any keep-rule that blocks it. |
| Added | Stay in. Add to **OK to follow upstream** if it is not already written there. If it is only in code and not on screen, wire it before calling it added. | Remove it. Add the Yuno behavior to **Always keep**. |

After those votes are applied, post a **fresh** numbered left-out / added list. That list is the new state, not a repeat of the one just voted on. **Keep posting a new list after every fix pass until you say all is good.**

## Open questions

Add checkboxes when something new comes up:

- [ ] _
