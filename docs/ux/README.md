# UX Design Documentation — ITX Smart Attendance System

**Status:** Draft v1.0  
**Created:** 2026-06-04  
**Target Audience:** Frontend Engineers (React), QA, Product Managers

---

## 📚 Document Structure

This folder contains complete UX/UI specifications for the ITX Smart Attendance System. All designs follow **React 18 + TypeScript + Tailwind CSS v3** and map precisely to the PRD State Machine.

### Core Documents

| File | Purpose | Audience |
|------|---------|----------|
| **01-ux-overview.md** | Design system, principles, navigation, color palette | All |
| **02-component-tree.md** | React component hierarchy, structure, hooks, state mgmt | Frontend |
| **03-employee-check-in-screen.md** | Mobile-first check-in flow, camera, validations | Frontend |
| **04-leader-dashboard.md** | Team roster, request approval, notifications | Frontend |
| **05-admin-settings.md** | Configuration grids, audit logs, overrides | Frontend |
| **06-ui-state-mapping.md** | How UI states map to PRD State Machine | All |

---

## 🎯 Quick Start

### For Frontend Engineers

1. **Start here:** `01-ux-overview.md` (design system, colors, spacing)
2. **Understand structure:** `02-component-tree.md` (React architecture, hooks, state)
3. **Build screens in order:**
   - `03-employee-check-in-screen.md` (highest priority, MVP critical path)
   - `04-leader-dashboard.md` (lower priority, but needed for approval flow)
   - `05-admin-settings.md` (lowest priority, data config screens)
4. **Validate against PRD:** `06-ui-state-mapping.md` (ensure UI matches State Machine)

### For QA / Product

1. Read: `06-ui-state-mapping.md` (understand state transitions)
2. Refer to: `01-ux-overview.md` (visual expectations)
3. Check: Scenario tables in each screen spec

---

## 🏗️ Tech Stack (Locked)

| Layer | Tech | Notes |
|---|---|---|
| **Language** | TypeScript 5.x | Strict mode required |
| **Framework** | React 18.x | Hooks-based, no class components for new code |
| **Build** | Vite | Not CRA |
| **Styling** | Tailwind CSS v3 | Utility-first, no custom CSS |
| **HTTP** | Axios + TanStack Query v5 | Polling every 15 seconds for notifications |
| **Forms** | React Hook Form + Zod | Type-safe validation |
| **Routing** | React Router v6 | Nested routes, lazy loading |
| **Camera** | react-webcam | Direct capture only, no gallery upload |

**Not included:** Redux, GraphQL, WebSocket (polling sufficient for MVP), WebWorker (unless needed for image compression)

---

## 📋 Component Checklist

### Employee Flow
- [ ] **CheckInScreen** — Mobile-first, <30 seconds, camera + GPS + MAC
- [ ] **CameraViewfinder** — react-webcam integration, photo compression
- [ ] **MacWifiStatus** — Visual indicator for network validation
- [ ] **GpsStatus** — Location accuracy display
- [ ] **ClientSiteModeToggle** — Conditional, GPS-based fallback
- [ ] **AttendanceHistoryScreen** — Personal history + request list
- [ ] **ExceptionRequestForm** — Modal for LATE_IN/EARLY_OUT/HALF_DAY
- [ ] **AdjustmentRequestForm** — Modal for INCOMPLETE records

### Leader Flow
- [ ] **LeaderHeader** — Bell + unread badge
- [ ] **LeaderSidebar** — Navigation + unread count badge
- [ ] **DailyRosterScreen** — Date picker, employee cards, status summary
- [ ] **RosterCard** — Individual employee, with pending request badge
- [ ] **PendingRequestsScreen** — Tabs (Pending/Approved/Rejected)
- [ ] **RequestDetailModal** — Full request preview + approval form
- [ ] **ApprovalForm** — [Duyệt] [Từ chối] buttons
- [ ] **RejectionReasonModal** — Required reason input
- [ ] **NotificationPanel** — Slide-over, mark as read

### Admin Flow
- [ ] **AdminSidebar** — Navigation tabs (Shifts/MACs/Holidays/Requests/Audit)
- [ ] **ShiftConfigGrid** — CRUD shifts, double-click edit, audit reason
- [ ] **MacManagerGrid** — CRUD MACs (company + individual scope)
- [ ] **HolidayManagerGrid** — Fixed + Dynamic holidays
- [ ] **AttendanceOverrideForm** — Admin can edit any record + audit log
- [ ] **AuditLogViewer** — Immutable log with filters

### Common
- [ ] **Header** — Logo + title + notification bell
- [ ] **BottomTabNav** — Mobile: Check-in/History/Requests/Settings
- [ ] **Toast** — Auto-dismiss notifications (4 seconds)
- [ ] **ErrorBoundary** — Global error handler
- [ ] **LoadingSpinner** — Consistent loading state

---

## 🎨 Design System Quick Reference

### Colors
```
Primary:    emerald-600  (#059669)
Danger:     red-600      (#dc2626)
Warning:    amber-500    (#f59e0b)
Success:    green-500    (#22c55e)
Neutral:    slate-700    (#334155)
Background: slate-50     (#f8fafc)
```

### Spacing
```
Base unit: 4px
Standard padding: p-4 (16px)
Standard gap: gap-4
Card radius: rounded-lg (8px)
```

### Typography
```
H1: text-2xl font-bold
H2: text-xl font-bold
H3: text-lg font-bold
Body: text-base font-normal
Caption: text-sm / text-xs
Monospace: font-mono (for MAC, time, JSON values)
```

### Responsive
```
Mobile: < 640px (default target)
Tablet: 640px–1024px (md:)
Desktop: 1024px+ (lg:)
```

---

## 🔄 State Management Pattern

### Auth State (Zustand)
```typescript
useAuthStore() → { user, role, token, login(), logout(), refreshToken() }
```

### UI State (Zustand)
```typescript
useUIStore() → { currentPage, isSidebarOpen, toastMessage, notificationPanelOpen }
```

### Server State (TanStack Query)
```typescript
useQuery({
  queryKey: ['...'],
  queryFn: () => api.call(),
  refetchInterval: 15000,  // Polling for notifications
})
```

### Form State (React Hook Form + Zod)
```typescript
const { register, handleSubmit, formState: { errors } } = useForm<SchemaType>({
  resolver: zodResolver(schema),
});
```

---

## 🚀 Key Feature Implementation Order

**MVP Critical Path (Week 1–2):**
1. ✅ **Employee Check-in** (`03-employee-check-in-screen.md`)
   - MAC validation
   - GPS collection
   - Photo capture (react-webcam)
   - Status calculation (ON_TIME / LATE_IN)

2. ✅ **Employee History** (partial in `03-employee-check-in-screen.md`)
   - List view with status badges
   - Exception request form

3. ✅ **Leader Dashboard** (`04-leader-dashboard.md`)
   - Daily roster by date
   - Pending requests view
   - Approval flow (Duyệt / Từ chối)

**Phase 2 (Week 3–4):**
4. **Admin Settings** (`05-admin-settings.md`)
   - Shift config grid
   - MAC manager
   - Holiday manager
   - Attendance override
   - Audit log viewer

5. **Notifications**
   - In-app polling (TanStack Query every 15s)
   - Email (backend handles SMTP)
   - Bell icon badge count

---

## 📐 Mobile-First Constraints

- **Viewport:** 375px–480px (iPhone X to Android)
- **Touch targets:** ≥48×48px minimum
- **Performance:** FCP <1.5s, LCP <2.5s, CLS <0.1
- **Check-in time:** <30 seconds on 4G
- **Camera:** No permission denied → allow fallback (optional GPS in office mode)
- **Network:** Queue requests when offline, sync when online

---

## ✅ Testing & Validation

### Component Testing
- Unit tests for form validation (Zod schemas)
- Integration tests for API calls (Mock TanStack Query)
- Snapshot tests for UI components

### E2E Testing
- Employee: Check-in → History → Send request
- Leader: View roster → Approve request
- Admin: Configure shifts → Override record → View audit log

### Visual Regression
- Storybook for component gallery
- Visual diff on every PR

### Accessibility
- WCAG 2.1 AA compliance
- Keyboard navigation (Tab, Enter, Escape)
- Screen reader testing (NVDA, JAWS, VoiceOver)

---

## 🔐 Security Considerations

- **JWT:** Access token (15 min), Refresh token (7 days, HttpOnly cookie)
- **S3 Photos:** Presigned URLs (1 hour TTL), no public bucket
- **Audit Log:** Immutable, logged by Admin ID + reason
- **Form Input:** Server-side validation + client-side (Zod)
- **XSS:** Sanitize user input, use `dangerouslySetInnerHTML` sparingly

---

## 🌐 Browser Support

| Browser | Min Version | Support |
|---------|-------------|---------|
| Chrome (Android) | 90+ | ✅ Full |
| Safari (iOS) | 14+ | ✅ Full |
| Chrome (Desktop) | 90+ | ✅ Full |
| Safari (macOS) | 14+ | ✅ Full |
| Firefox | 88+ | ✅ Full |
| Edge | 90+ | ✅ Full |

**Excluded:** IE 11, UC Browser, Samsung Internet <13

---

## 🎓 Learning Resources

- **React 18:** https://react.dev (hooks, concurrent features)
- **Tailwind CSS v3:** https://tailwindcss.com/docs
- **React Hook Form:** https://react-hook-form.com/
- **TanStack Query:** https://tanstack.com/query/latest
- **TypeScript:** https://www.typescriptlang.org/docs/
- **react-webcam:** https://github.com/mozmorris/react-webcam

---

## 📝 Naming Conventions

### React Components
```typescript
// Screens / Pages
CheckInScreen.tsx           // Full-screen component
DailyRosterScreen.tsx
AttendanceHistoryScreen.tsx

// UI Components
RosterCard.tsx              // Reusable, isolated
StatusBadge.tsx
NotificationBell.tsx

// Hooks
useAttendance.ts            // Data fetching
useCamera.ts                // Device access
useGeolocation.ts           // GPS
```

### Type Definitions
```typescript
types/api.ts                // Backend DTOs
types/domain.ts             // Frontend domain entities
types/form.ts               // Form schemas
types/state.ts              // UI enums
```

### Files Structure
```
components/
├── common/                  // Shared across all roles
├── employee/               // Employee-only
├── leader/                 // Leader-only
└── admin/                  # Admin-only

hooks/                      # Custom hooks (useAuth, useQuery, etc.)
services/                   # API clients (axios instances, methods)
store/                      # Zustand stores (auth, ui)
types/                      # TypeScript interfaces
utils/                      # Helpers (formatters, validators)
pages/                      # Page-level components
```

---

## 🚨 Common Pitfalls to Avoid

1. **Over-rendering:** Use `useMemo` and `useCallback` for expensive computations
2. **Stale state:** Refresh queries after mutations (invalidateQueries)
3. **Memory leaks:** Clean up event listeners in `useEffect` return
4. **Unhandled promises:** Always await async functions, handle errors
5. **Hardcoded strings:** Use enums or constants for status values
6. **Missing aria attributes:** Add `aria-label`, `aria-live`, `role` to dynamic elements
7. **Blocking UI on upload:** Use async/await for photo upload to S3
8. **Not validating input:** Always validate form inputs with Zod before submit

---

## 📞 Questions & Support

- **PRD Questions:** Refer to `/prd.md` (business logic, features, State Machine)
- **Project Context:** Refer to `/project_context.md` (tech stack, architecture guardrails)
- **Addendum:** Refer to `/addendum.md` (data model, API boundary, rejected alternatives)

---

## 📄 Document Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| v1.0 | 2026-06-04 | Claude Code (BMAD) | Initial comprehensive UX specification |

---

**Last Updated:** 2026-06-04  
**Next Review:** After first component merge  
**Owner:** Product Team / UX Lead
