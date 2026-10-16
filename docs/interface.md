# Interface refresh

The app uses Material 3 components with a quieter, more sculpted presentation: graphite and grayscale surfaces, soft depth, larger corner radii and clear selected states. The password generator uses a raised preview card and separate control areas; secrets stay on opaque surfaces for legibility.

- Navigation uses 28dp vector icons with smaller labels in a compact, approximately 64dp floating container (before outer/system insets). The entire selected tab is highlighted, including its icon and label; font scaling can increase its height. The first destination is named “Cuentas”.
- Profile filters and profile selection in the editor share compact text-only `ChoiceChip` controls with distinct selected fill and radio-button semantics. No ticks, leading icons or blank icon slots. Unselected choices have quiet borders. Profile choices wrap if necessary. Appearance presents Sistema/Oscuro/Claro directly in one equal-width row with 48dp minimum touch targets; no dropdown.
- Account cards have a raised initial tile, title, secondary username, readable plain profile label and a chevron. The tile is the only colored element on the card; there is no vertical stripe or colored badge. Pending updates retain their status label.
- The generator groups the password preview, length slider and symbol switch into rounded elevated panels. Primary and secondary actions have larger touch targets, softer corners and clear hierarchy.
- Copy, show/hide, lock, back, rename and delete are 48dp icon controls with Spanish accessibility descriptions. Destructive actions still require confirmation.
- Settings presents appearance, autofill status, read-only auto-lock behavior and Help. Full compatibility/recovery guidance is in Help; a short no-backup warning remains visible.
- Light and dark themes share hierarchy and use theme colors, including the navigation container. Choice colors animate without moving the controls.

Validation: all Kotlin sources including Compose compiled with the cached Kotlin 2.1.20 Compose compiler; vector resources compiled and linked with AAPT2. This is not an APK assembly or screenshot test.

Screenshots are now permitted in the main app and autofill activity at the user's request. This also permits capturing revealed credentials; the in-app privacy help reflects that tradeoff.

Keyboard space is handled once around the Scaffold, and its content consumes the supplied padding. Bottom navigation is hidden while the IME is visible or animating closed, so its reserved height does not become a blank strip above the keyboard. Check search, profile creation and account editing with the keyboard open and during dismissal on a device.

The account-detail “Se oculta en 30 s” caption has been removed. The 30-second detail timeout remains unchanged.

The accounts screen starts with search and a 48dp lock action, without the former CLAVE/Bóveda/count headers. Wrapping profile filters lead directly into the account list. Search stays accessible while the filters and accounts scroll. The add action is a floating plus above the navigation; list bottom padding lets the final account scroll clear of it. Both floating controls hide with the keyboard. Autofill setup is placed after the account list rather than before it.

Search uses a compact BasicTextField decoration with a 48dp minimum height and a 1dp focus border, rather than Material's taller outlined-field padding. It can grow for large fonts; the IME Search action dismisses the keyboard. Account-editor fields explicitly use 20dp corners.

Account entry starts with username and password, then optional name and HTTPS website, followed by profile. The redundant example under the website is removed. A blank name is filled on save from the validated website host, native app package, or username (bounded to 100 characters). The stored title remains non-empty for existing vault-format compatibility; custom titles are preserved. Supplying a website is still necessary for matching manually saved web accounts during autofill.

The lock-screen branding begins around 20% of the usable height; the fingerprint retry control remains near 62%. Short windows scroll to keep controls reachable. The icon-only control has a TalkBack label and shows progress during authentication. The logo keyhole is a single continuous path to avoid overlapping-fill seams. Automatic authentication/cancellation behavior is unchanged.

## Profile and account colors

Perfiles shows tappable profile cards, with a floating + button matching Cuentas. The button opens a rounded name-and-color form; tapping an existing profile opens the same form for editing. Name and color save together in one vault write, and the form closes after the updated document is received. Duplicate names are flagged inline. Delete is only shown inside the profile editor, disabled for nonempty or last profiles, and requires a separate confirmation. List padding keeps the final card clear of the floating button.

Colors belong exclusively to profiles. The profile form offers eight named colors; canceling leaves the stored color unchanged. Profiles without an explicit color use neutral grey, including Ocio. All accounts follow their profile color, ignoring legacy account override metadata. There is no account color picker.

The profile color drives account initial tiles and detail-screen actions. Profile filters also use their profile color when selected. Backgrounds remain neutral, with restrained elevation and borders providing depth.

Color choices are saved inside the encrypted vault document. Existing vaults receive default colors without migration. Renaming a profile retains its color; deleting a profile removes its color metadata. Unknown stored colors safely fall back to defaults. Encryption and autofill matching are unchanged.

Validation: all Kotlin/Compose sources compile, and 19 JVM tests pass, including color persistence, rename/delete/reset, old-document compatibility and at least 4.5:1 text contrast for all palette pairs in both themes. No APK was assembled for this change.

Device review still needed: light/dark/system switching, color selection/inheritance, selected choices in every screen, large font sizes, narrow screens with wrapping profile filters, screenshot capture, TalkBack labels, and Help scrolling.
