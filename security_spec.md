# Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **CalendarEvent (`/users/{userId}/events/{eventId}`)**:
   - Can only be created, read, updated, or deleted by the authenticated owner (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
   - `userId` and `createdAt` are immutable on update.
   - All strings (`title`, `notes`, `category`) and integers (`solarDay`, `lunarDay`, etc.) have strict bounds.
2. **UserPreference (`/users/{userId}/settings/{settingId}`)**:
   - Strictly isolated per user (`request.auth.uid == userId`).
   - `themeMode` and `accentPalette` are restricted to enumerated values.
3. **SharedSchedule (`/shared_schedules/{shareId}`)**:
   - Only `ownerId == request.auth.uid` can create, update, or delete.
   - Read (`get` and `list`) is restricted to the owner (`ownerId == request.auth.uid`) or a recipient whose email matches `recipientEmail` AND has `request.auth.token.email_verified == true`.

## 2. The Dirty Dozen Payloads
1. Unauthenticated read/write to `/users/{userId}/events`.
2. Cross-user read/write where `auth.uid != userId`.
3. Identity spoofing on create (`incoming().userId != request.auth.uid`).
4. Mutating `userId` or `createdAt` on update.
5. Shadow update injecting ghost field `isAdmin: true`.
6. Oversized `title` (> 120 chars) or `notes` (> 500 chars).
7. Invalid enum value for `category` (e.g., `"hacked"`).
8. Out-of-range lunar date (`lunarDay: 35` or `lunarMonth: 13`).
9. Future timestamp injection (`createdAt > request.time`).
10. Document ID poisoning (> 128 chars or special characters).
11. Email spoofing on `/shared_schedules` with `email_verified: false`.
12. Unfiltered `list` query on `/shared_schedules` by non-owner/non-recipient.
