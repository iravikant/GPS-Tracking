# GPS Tracking Android Application

## Overview
This is a native Android application built using Kotlin that continuously tracks the user’s GPS location using a foreground service. The app stores GPS tracks locally in a session-based format and visualizes routes on Google Maps with distance details.

The implementation strictly follows modern Android platform guidelines for background execution, permissions, and power management.

---

## Core Features

- Foreground GPS tracking service
- Persistent, non-dismissible notification with Stop action
- Tracking continues when app is backgrounded or removed from recents
- Session-based local storage using Room
- Session history listing
- Session detail view with Google Maps route visualization
- Start & End markers on map
- Distance calculation per session
- Live latitude & longitude updates in UI while tracking
- Battery optimization handling for long-running reliability

---

## Technical Stack

- Kotlin
- Android SDK
- Foreground Service (`foregroundServiceType="location"`)
- Google Maps SDK
- Room Database
- LocalBroadcastManager
- Lifecycle-aware components

---

## Permissions Used

- ACCESS_FINE_LOCATION
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_LOCATION
- POST_NOTIFICATIONS (Android 13+)


Background location permission is intentionally avoided.  
Foreground services with a visible notification are used instead, which aligns with Play Store policies.

---

## Battery Optimization Handling

To ensure reliable long-running tracking, the app explicitly handles battery optimization restrictions.

### Behavior:
- Detects if battery optimization is enabled
- Prompts the user to disable optimization
- Redirects to the system battery optimization settings screen

This improves reliability on OEM-customized Android versions (MIUI, ColorOS, OneUI, etc.) where background processes are aggressively killed.

Disabling battery optimization is optional but recommended.

---

## App Flow

1. User launches the app
2. Location & notification permissions are requested when required
3. User starts tracking
4. Foreground service begins collecting GPS updates
5. Location points are stored locally under a session
6. Live latitude/longitude updates are shown in UI
7. Tracking continues even if app is backgrounded or removed from recents
8. User stops tracking via notification or app
9. Session is saved and appears in session history
10. User can view route details on Google Maps

---

## Project Structure

com.gpstracking
├── Services
│ └── LocationTrackingService.kt
├── Room
│ ├── AppDatabase.kt
│ ├── TrackingSessionEntity.kt
│ ├── LocationPointEntity.kt
│ ├── SessionDao.kt
│ └── LocationDao.kt
├── utils
│ ├── NotificationUtils.kt
│ └── PermissionUtils.kt
├── Adapters
│ └── SessionAdapter.kt
├── SplashActivity.kt
├── HomeActivity.kt
├── SessionHistoryActivity.kt
└── SessionDetailActivity.kt




## ScreenShots


![home](https://github.com/user-attachments/assets/917eb3e1-4b47-4eb9-82ce-b09e5d2ab44c)
![history](https://github.com/user-attachments/assets/a1b055a1-3545-4753-a240-599244294f4c)
![live screen](https://github.com/user-attachments/assets/04fe108e-70a1-4035-874c-92d8ad1fd14c)

