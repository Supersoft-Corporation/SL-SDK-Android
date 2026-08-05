# SoftLink Android SDK

Official Android SDK for [SoftLink](https://supersoftlink.com) — a deep link management platform.

## Features

- 🔗 **Deep linking** — open specific screens when app is already installed
- 📦 **Deferred deep linking** — navigate to the right screen even after fresh install
- 🎯 **Install attribution** — track installs via Play Install Referrer
- 📱 **Device identification** — Android ID + AppSet ID fallback
- 🔗 **Referral link generation** — create shareable dynamic links at runtime
- ☕ **Java compatible** — works with both Kotlin and Java apps

## Installation

### Step 1 — Add JitPack to your project

In your root `settings.gradle`:
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2 — Add dependency

In your app's `build.gradle`:
```groovy
dependencies {
    implementation 'com.github.Supersoft-Corporation:SL-SDK-Android:0.0.12'
}
```

## Setup

### Step 1 — AndroidManifest.xml

Add App Links intent filter inside your `<activity>`:

```xml
<!-- App Links: intercepts https link when app is installed -->
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="https"
        android:host="your-domain.com"
        android:pathPrefix="/l/" />
</intent-filter>

<!-- Custom scheme fallback -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="yourscheme" android:host="l" />
</intent-filter>
```

### Step 2 — Initialize SDK

In your `MainActivity.kt`:

```kotlin
import com.supersoftcorporation.softlink.SoftLink
import com.supersoftcorporation.softlink.SoftLinkDeepLink

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SoftLink.init(
            context = this,
            baseUrl = "https://api.supersoftlink.com",
            apiKey = "sl_your_api_key",
            onDeepLink = { deepLink ->
                handleDeepLink(deepLink)
            }
        )

        // Handle the initial intent that launched the app
        SoftLink.handleInitialIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle deep links when app is already running
        SoftLink.handleIntent(intent)
    }

    private fun handleDeepLink(deepLink: SoftLinkDeepLink) {
        when (deepLink.screen) {
            "BOOKING_DETAIL" -> {
                val bookingId = deepLink.getParam("bookingId")
                // navigate to booking detail
            }
            "PRODUCT_DETAIL" -> {
                val productId = deepLink.getParam("productId")
                // navigate to product detail
            }
            else -> {
                // handle unknown screen
            }
        }
    }
}
```

### Java Usage

```java
SoftLink.init(
    this,
    "https://api.supersoftlink.com",
    "sl_your_api_key",
    deepLink -> {
        String screen = deepLink.getScreen();
        String param = deepLink.getParam("key", "default");
        // handle navigation
        return null;
    }
);
```

## Generate Referral Links

```kotlin
SoftLink.generateReferralLink(
    screenKey = "PRODUCT_DETAIL",
    values = mapOf("productId" to "123", "categoryId" to "456"),
    token = "PARENT_TOKEN", // optional
    referrerId = currentUser.id // optional
) { url ->
    if (url != null) {
        // share the url
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
}
```

### Java Usage

```java
SoftLink.generateReferralLink(
    "PRODUCT_DETAIL",
    Map.of("productId", "123"),
    null,
    null,
    url -> {
        if (url != null) {
            // share the url
        }
        return null;
    }
);
```

## SoftLinkDeepLink Properties

| Property | Type | Description |
|----------|------|-------------|
| `token` | `String` | Unique link token |
| `screen` | `String` | Screen key (e.g. `PRODUCT_DETAIL`) |
| `params` | `Map<String, Any>` | Link parameters |
| `linkType` | `String` | `static` or `dynamic` |

## Methods

| Method | Description |
|--------|-------------|
| `getParam(key)` | Get parameter as String or null |
| `getParam(key, default)` | Get parameter as String with default value |

## ProGuard / R8

Add to your `proguard-rules.pro`:

```
-keep class com.supersoftcorporation.softlink.** { *; }
-keepnames class com.supersoftcorporation.softlink.** { *; }
```

## Requirements

- Android SDK 21+ (Android 5.0+)
- Kotlin 1.8+ or Java 8+

## License

Apache 2.0
