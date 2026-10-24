# Xiaomi overall device test — 24 October 2026

## Device and build

- Device: Xiaomi `2606FRN72Y`, 720 × 1600 portrait / 1600 × 720 landscape.
- Package: `com.gmail.manhle240406.project`.
- Variant: Spark.
- Existing authenticated account and application data were preserved.
- Installed package timestamp was newer than the tested Spark APK build timestamp.

## Verified flows

| Area | Result |
|---|---|
| Cold launch | Opened authenticated `MainActivity`; no loading loop |
| Home | Dynamic animal types and featured listings returned |
| Lazy refresh | Featured cards remained rendered after the old 30-second expiry interval; no blank/loading flicker |
| Favorites | Saved-post and saved-pet content returned |
| Normal chat | Inbox rendered current peer data |
| Adoption chat | Adoption tab rendered multiple current conversations |
| Profile | Display name and linked pet rows returned |
| Notifications | Current notification inbox items returned |
| Explore/tools | Rendered inside the main shell |
| Back | Explore/tools returned to the previous Home destination |
| Bottom navigation | Remained visible across tested main destinations |
| Rotation | Chat/adoption content survived portrait → landscape → portrait |
| App lifecycle | Five Home/background/foreground cycles returned to `MainActivity` |

## Layout evidence

- Captured UI trees for Home, Favorites, Chat, Adoption chat, Profile, Notifications, Explore/tools, Back-returned Home, and landscape.
- Every captured node remained inside its 720 × 1600 or 1600 × 720 root bounds: zero out-of-bounds nodes.
- Portrait Home content ended at y=1346 and bottom navigation began at y=1346, with no geometric overlap.
- Visual inspection of the final Home screenshot found consistent margins, aligned filter controls, two-column featured cards, and a readable persistent bottom bar.

## Stability evidence

- No `AndroidRuntime` fatal exception.
- No native fatal signal.
- No ANR was observed.
- MIUI logged display-reconfiguration messages during forced rotation; these were system WindowManager messages and did not stop or recreate the app incorrectly.

## Limitations

- The pass was read-only and navigation-focused. It did not publish, delete, approve, adopt, or modify account data.
- Push delivery was not forced by creating a production notification event.
- A fresh reinstall attempt waited on MIUI's per-install confirmation and was cancelled; the already installed Spark package was current and was used for testing.
