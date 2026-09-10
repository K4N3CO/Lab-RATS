# Implementation Plan - Stealth Operations Update

Update the `ic_system_update_decoy` zoom level and add a new "Lab-RATS Logo" stealth identity.

## Proposed Changes

### [Component] Resources - Icons

#### [MODIFY] [ic_system_update_adaptive.xml](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/res/mipmap-anydpi-v26/ic_system_update_adaptive.xml)
Increase insets from `18dp` to `24dp` to zoom out the gear icon.

#### [MODIFY] [ic_system_update_round.xml](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/res/mipmap-anydpi-v26/ic_system_update_round.xml)
Increase insets from `18dp` to `24dp`.

#### [NEW] [ic_labrats_logo_adaptive.xml](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/res/mipmap/ic_labrats_logo_adaptive.xml)
Standard layer-list for the new Lab-RATS Logo.

#### [NEW] [ic_labrats_logo_adaptive.xml](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/res/mipmap-anydpi-v26/ic_labrats_logo_adaptive.xml)
Adaptive icon for the new Lab-RATS Logo using `app_logo.png`.

---

### [Component] Manifest & Logic

#### [MODIFY] [AndroidManifest.xml](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/AndroidManifest.xml)
Add `LabRatsLogoAlias` activity-alias targeting `.MainActivity`.

#### [MODIFY] [SystemAnalytics.java](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/java/com/labs/labrats/SystemAnalytics.java)
Update `setStealthMode` to include the new `LabRatsLogoAlias` and handle choice `5`.

#### [MODIFY] [TerminalModule.java](file:///Users/admin/StudioProjects/Lab-RATS/app/src/main/java/com/labs/labrats/modules/TerminalModule.java)
Add "Lab-RATS Logo" to the stealth masquerade identity selection list.

## Verification Plan

### Manual Verification
1.  Verify the gear icon appears smaller (zoomed out) in the Android launcher.
2.  Use the C2 interface to select "Lab-RATS Logo" and verify the app icon changes to the Lab-RATS logo and opens the main app directly.
