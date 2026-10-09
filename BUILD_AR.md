# طريقة إنشاء APK — دوامي المدرسي

## الطريقة الأسهل: Android Studio

1. ثبّت Android Studio على Windows أو macOS أو Linux.
2. افتح Android Studio ثم اختر **Open** وافتح مجلد المشروع.
3. انتظر انتهاء Gradle Sync. إذا طلب SDK Platform 35 أو Build Tools ثبّتها من SDK Manager.
4. إذا لم يجد Android Studio مسار SDK:
   - انسخ `local.properties.example` إلى `local.properties`.
   - عدّل `sdk.dir` ليشير إلى Android SDK الموجود على جهازك.
5. من القائمة اختر:
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**
6. ستجد APK التجريبي غالبًا في:
   `app/build/outputs/apk/debug/app-debug.apk`

## من سطر الأوامر

المتطلبات:

- JDK 17 أو أحدث.
- Android SDK Platform 35.
- Android Build Tools 35.0.0.
- Gradle 8.11.1 أو استخدم Gradle الموجود في Android Studio.

بعد فك الضغط:

```bash
cd SchoolScheduleAndroid
cp local.properties.example local.properties
# عدّل sdk.dir داخل local.properties
./gradlew testDebugUnitTest assembleDebug
```

إذا لم يوجد `gradlew` في بيئتك، شغّل الأمر باستخدام Gradle المثبت:

```bash
gradle testDebugUnitTest assembleDebug
```

## إنشاء نسخة Release موقّعة

للاختبار استخدم debug APK. للنشر، أنشئ Keystore خاصًا بك ثم اربطه بإعدادات `signingConfigs` داخل `app/build.gradle.kts`، وبعدها شغّل:

```bash
gradle assembleRelease
```

لا تشارك ملف الـ Keystore أو كلمات المرور.

## بعد تثبيت التطبيق

عند أول تشغيل سيطلب التطبيق صلاحية الوصول إلى الملفات وصلاحية الإشعارات. ملفات البيانات تنشأ تلقائيًا في:

`Internal Storage/SchoolSchedule/data/`

وهي:

- `schedule.json`
- `periods.json`
- `holidays.json`

يمكن تعديلها خارجيًا ثم اختيار **إعادة تحميل ملفات البيانات** من الإعدادات.

## تنبيه مهم عن بيئة البناء الحالية

تم إنشاء المشروع والكود كاملين، لكن بيئة البناء الأصلية واجهت مشكلة TLS أثناء تنزيل مكتبات Gradle من Google Maven وMaven Central. لذلك يُفضّل تنفيذ البناء من Android Studio أو من جهاز يملك اتصالًا طبيعيًا بمستودعات Gradle.
