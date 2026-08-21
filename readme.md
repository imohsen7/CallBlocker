# CallBlocker MVP

اپ ساده Android برای رد خودکار تماس بر اساس شماره کامل یا prefix.

## قابلیت‌ها

- شماره کامل: `09131101212`
- پیش‌شماره: `092130*`
- تبدیل خودکار ارقام فارسی/عربی به انگلیسی
- نرمال‌سازی `+98`، `0098` و `98` به فرمت `0...`
- رد تماس با Android `CallScreeningService`
- لاگ داخلی تماس‌های ردشده
- Retention: 7 / 30 / 90 / 365 روز / همیشه
- مجوز اختیاری Contacts برای بررسی تماس شماره‌هایی که در مخاطبین ذخیره شده‌اند

## نیازمندی

- Android 10 (API 29) یا بالاتر
- Android Studio
- JDK 17
- Android SDK 35

## Build

1. پوشه `CallBlocker` را در Android Studio باز کنید.
2. صبر کنید Gradle Sync تمام شود.
3. اگر Android Studio خواست SDK 35 را نصب کند، نصب کنید.
4. از منوی Build > Build APK(s) استفاده کنید.
5. APK دیباگ معمولاً در مسیر زیر ساخته می‌شود:
   `app/build/outputs/apk/debug/app-debug.apk`

## فعال‌سازی روی گوشی

1. برنامه را نصب و باز کنید.
2. `فعال‌سازی مسدودکننده تماس` را بزنید.
3. در پنجره Android، برنامه را به عنوان Call Screening انتخاب/تأیید کنید.
4. اگر می‌خواهید تماس شماره‌های ذخیره‌شده در Contacts هم بررسی شود، مجوز Contacts را بدهید.
5. قانون اضافه کنید:
   - `09131101212` فقط همان شماره را رد می‌کند.
   - `092130*` هر شماره‌ای که با `092130` شروع شود را رد می‌کند.

## نکته Retention

پاکسازی لاگ هنگام باز شدن اپ و هنگام دریافت تماس جدید انجام می‌شود. گزینه `همیشه` پاکسازی خودکار را غیرفعال می‌کند.

## نکته Android

CallScreeningService باید حداکثر در 5 ثانیه به سیستم پاسخ بدهد. این پروژه فقط lookup محلی SQLite انجام می‌دهد و شبکه استفاده نمی‌کند.

## Build APK without Android Studio (GitHub Actions)

1. Create a new empty GitHub repository.
2. Upload the contents of this project to the repository root.
3. Open the repository's **Actions** tab.
4. Select **Build Android APK**.
5. Click **Run workflow**.
6. After the workflow completes, download the artifact named **CallBlocker-debug-apk**.
7. Extract it and install `app-debug.apk` on the Android phone.

The debug APK is signed automatically with the Android debug key and is suitable for testing.
