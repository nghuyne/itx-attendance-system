# UX Design Overview — ITX Smart Attendance System

**Document Status:** Draft v1.0  
**Created:** 2026-06-04  
**Target Audience:** React Frontend Engineers, QA Testers  
**Design Approach:** Mobile-first, Role-based, State-driven UI

---

## 1. Design Principles

### 1.1 Core Pillars
- **Speed**: Check-in/out must complete in <30 seconds on mobile 4G (SM-5 from PRD)
- **Trust**: Visual feedback for all network operations; clear error states
- **Clarity**: Context-aware UI — role and permission determine what's visible
- **Accessibility**: WCAG 2.1 AA; touch targets ≥48px; high contrast

### 1.2 Role-Based Layouts

| Role | Primary Device | Layout | Key UX Trait |
|---|---|---|---|
| **Employee** | Mobile (iOS/Android Chrome) | Single-column, fullscreen | Minimal steps; big buttons |
| **Team Leader** | Tablet/Desktop | Responsive dashboard | Overview + action; notification-driven |
| **Admin** | Desktop | Multi-panel settings | Data-dense grids; configuration priority |

---

## 2. Navigation Architecture

### 2.1 Bottom Tab Navigation (Mobile)
Persistent tabs for Employee role:
- **Check-in/out** (default, hero screen)
- **History** (personal attendance records)
- **Requests** (pending Exception/Adjustment requests)
- **Settings** (personal preferences; minimal)

### 2.2 Sidebar Navigation (Leader/Admin)
Collapsible left sidebar (desktop/tablet):
- **Dashboard** (daily roster overview)
- **Requests** (pending approvals)
- **Team/Reports** (team view or analytics)
- **Admin Config** (shifts, MAC, holidays — Admin only)
- **Audit Logs** (Admin only)

---

## 3. Design System — Tailwind CSS v3

### 3.1 Color Palette

| Intent | Color | Tailwind Token | Usage |
|---|---|---|---|
| Primary | Green | `emerald-600` | CTA buttons, success states |
| Secondary | Blue | `blue-500` | Info, neutral actions |
| Danger | Red | `red-600` | Errors, rejection, late/violation |
| Warning | Amber | `amber-500` | Caution, GPS unavailable |
| Success | Green | `green-500` | Check-in success, APPROVED |
| Neutral | Slate | `slate-700/-400` | Text, borders, backgrounds |

### 3.2 Typography

| Element | Font | Size | Weight | Usage |
|---|---|---|---|---|
| Headings (H1–H3) | System default | `text-2xl`, `text-xl`, `text-lg` | `font-bold` | Page title, section header |
| Body | System default | `text-base` | `font-normal` | Paragraph text |
| Caption | System default | `text-sm` / `text-xs` | `font-normal` | Helper text, timestamps |
| Monospace | `font-mono` | `text-sm` | — | Time values, MAC address |

### 3.3 Spacing & Layout

- **Base unit:** `4px` (Tailwind default)
- **Padding:** `p-4` (16px) standard card padding
- **Gap:** `gap-4` between sections
- **Radius:** `rounded-lg` (8px) for cards; `rounded-full` for avatar

### 3.4 Responsive Breakpoints

| Breakpoint | Tailwind | Device |
|---|---|---|
| Mobile | `sm:` (640px) | Phone (portrait) |
| Tablet | `md:` (768px) | Tablet (portrait) |
| Desktop | `lg:` (1024px) | Desktop / Laptop |

---

## 4. Loading & Error States

### 4.1 Loading Pattern
- Skeleton loaders for slow networks (avoid spinners that steal focus)
- Pulse animation on data cards (`animate-pulse`)
- Progress indication for file uploads (e.g., photo upload)

### 4.2 Error Pattern
```
┌─────────────────────────────────────┐
│ ⚠️  Lỗi Không nhận diện được mạng   │
│                                      │
│ Hệ thống không tìm thấy Wi-Fi      │
│ văn phòng hợp lệ.                   │
│                                      │
│ [Bật Client Site Mode] [Thử lại]   │
└─────────────────────────────────────┘
```

- **Alert box:** Red border (`border-red-300`), light red background (`bg-red-50`)
- **Action buttons:** Inline, secondary styling
- **Dismiss:** Auto-clear after 5 seconds or manual close

### 4.3 Network Offline
Show banner at top: "Mất kết nối Internet. Hệ thống sẽ đồng bộ khi online."

---

## 5. Notification Strategy

### 5.1 In-App Notification
- Toast at top-right corner (desktop) / top-center (mobile)
- 4-second auto-dismiss unless user action
- Types: `success` (green), `error` (red), `info` (blue), `warning` (amber)
- Polling every 15 seconds via TanStack Query (no WebSocket in MVP)

### 5.2 Bell Icon (Leader/Admin)
- Unread count badge (red dot or number)
- Click opens notification panel (slide-over or dropdown)
- Mark as read individually or all-at-once

---

## 6. Micro-interactions

### 6.1 Button States
- **Default:** Full opacity, cursor pointer
- **Hover:** Slightly darker shade (opacity 0.9) on desktop
- **Active/Pressed:** Border + shadow effect
- **Disabled:** Gray out (opacity 0.5), `cursor-not-allowed`

### 6.2 Form Inputs
- **Focus:** Border color changes to primary (`border-emerald-600`), shadow outline
- **Valid:** Green checkmark icon (optional)
- **Invalid:** Red border, error message below
- **Placeholder:** Lighter gray (`text-slate-400`)

### 6.3 Camera Viewfinder
- Smooth camera stream (no lag)
- Shutter button: Large circle button (60px diameter) with ripple effect on click
- Flash indicator (if device supports)

---

## 7. Accessibility Requirements

### 7.1 Keyboard Navigation
- Tab order logical (top→bottom, left→right)
- All buttons accessible via keyboard (Enter/Space to activate)
- Skip to main content link for screen readers

### 7.2 Screen Reader
- Semantic HTML (`<button>`, `<nav>`, `<main>`, `<form>`)
- `aria-label` for icon-only buttons: `aria-label="Check-in"`
- `aria-live="polite"` for toast notifications
- Form labels linked via `htmlFor`

### 7.3 Color Contrast
- Text ≥ 4.5:1 ratio (AA) for normal text
- Touch targets ≥ 48×48px minimum

---

## 8. Performance Targets

- **First Contentful Paint (FCP):** <1.5s on 4G
- **Time to Interactive (TTI):** <3.5s
- **Largest Contentful Paint (LCP):** <2.5s
- **Cumulative Layout Shift (CLS):** <0.1
- **Component render time:** <16ms (60 fps)

Use React DevTools Profiler to validate before merging code.

---

## 9. Browser & Device Support

| Browser | Min Version | Support |
|---|---|---|
| Chrome (Android) | 90+ | ✅ Full |
| Safari (iOS) | 14+ | ✅ Full |
| Chrome (Desktop) | 90+ | ✅ Full |
| Safari (macOS) | 14+ | ✅ Full |
| Firefox | 88+ | ✅ Full |
| Edge | 90+ | ✅ Full |

**Not Supported:** IE 11, UC Browser, older Samsung Internet <13

---

## 10. Dark Mode (Future; Out of MVP Scope)

Planned for v1.1. Current design is light-mode only. Use semantic color tokens (not hardcoded hex) to prepare for future dark mode migration.

---

## Next Steps

1. Review Component Tree (02-component-tree.md)
2. Study Employee Check-in Screen spec (03-employee-check-in-screen.md)
3. Study Leader Dashboard spec (04-leader-dashboard.md)
4. Study Admin Settings spec (05-admin-settings.md)
5. Map UI States to PRD State Machine (06-ui-state-mapping.md)
