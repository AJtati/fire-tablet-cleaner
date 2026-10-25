---
name: Clean Tablet Home
description: A calm, fast home screen for low-memory Fire tablets.
colors:
  wallpaper-wash: "#52131A23"
  system-surface: "#B8141A22"
  dock-surface: "#B3222933"
  control-surface: "#D92B323C"
  text-primary: "#FFFFFFFF"
  text-secondary: "#FFD8DCE3"
  accent-sky: "#FF86D7FF"
typography:
  display:
    fontFamily: "sans-serif-light, sans-serif"
    fontSize: "38sp"
    fontWeight: 300
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
  label:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 400
rounded:
  control: "24dp"
  dock: "26dp"
spacing:
  compact: "4dp"
  standard: "12dp"
  screen: "18dp"
components:
  search-field:
    backgroundColor: "{colors.control-surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.control}"
    height: "48dp"
  favorites-dock:
    backgroundColor: "{colors.dock-surface}"
    rounded: "{rounded.dock}"
    height: "82dp"
---

# Design System: Clean Tablet Home

## Overview

**Creative North Star: "The Quiet Tablet"**

Clean Tablet Home borrows the calm spacing and wallpaper-forward quality of a modern tablet home
screen while remaining native to Android. The visual hierarchy is deliberately shallow so the app
list stays immediate on memory-constrained hardware.

**Key Characteristics:** floating app icons, restrained translucent controls, a persistent favourites
dock, readable labels, and no decorative animation.

## Colors

Cool charcoal surfaces sit transparently over the user's wallpaper. White is reserved for primary
content; soft blue-grey supports instructions and dates. Accent Sky is available for native focus and
selection state, not as decoration.

**The Wallpaper First Rule.** Large opaque panels must not cover the home screen; only controls and
the dock receive stronger tonal surfaces.

## Typography

**Display Font:** Android sans-serif light  
**Body Font:** Android sans-serif

The 38sp lightweight clock is the only display treatment. Dates use 14sp, while app names and helper
text use compact 12sp labels. App labels gain a soft dark text shadow only to preserve contrast over
arbitrary wallpapers.

## Layout

Portrait uses 18dp screen edges, a compact clock header, a full-width 48dp search field, an auto-fit
94dp icon grid, and an 82dp bottom dock. Landscape compresses the header into one row, uses 20dp
edges, and reduces the dock to 76dp. All interactive controls preserve a minimum 48dp target.

## Elevation & Depth

The system uses tonal layering and one-pixel translucent borders rather than drop shadows or blur.
Icons remain visually unboxed.

## Shapes

Search and icon controls use 24dp corners. The larger dock uses 26dp corners. App artwork keeps the
shape supplied by each installed application.

## Components

### Search field

A 48dp charcoal pill with a leading authored search icon, white input text, and blue-grey hint text.

### App grid

Icons are 66dp with centered two-line labels. There is no per-app card. Touch and hold toggles a dock
favourite; a normal tap launches the app.

### Favourites dock

A single translucent container holds 64dp icon targets and scrolls horizontally when necessary.
An instructional empty state is shown until the first favourite is added.

## Do's and Don'ts

### Do:

- **Do** preserve direct app access and Android Back/Home behavior.
- **Do** use static alpha surfaces and native views to protect performance.
- **Do** test portrait and landscape changes on the 600×1024 Fire tablet.

### Don't:

- **Don't** add blur, video, background services, Internet access, analytics, or polling.
- **Don't** place every app inside a card or add decorative badges to favourites.
- **Don't** copy Apple artwork, system icons, or control behavior.
