package com.brandmauer.abplayer.i18n

/** Раздел юридического текста: заголовок и абзац. Английские варианты — в LegalText.en. */
class LegalSection(val title: String, val body: String)

/**
 * Тексты «Политики конфиденциальности» и «Лицензий» для экрана «О приложении».
 * Исходные тексты русские; английский перевод подмешивается в словарь I18n (см. LegalText.en).
 */
object LegalText {

    const val PRIVACY_TITLE = "Политика конфиденциальности"
    const val PRIVACY_DATE = "Редакция от 3 октября 2026 г."

    val privacy: List<LegalSection> = listOf(
        LegalSection(
            "Коротко",
            "ABPlayer работает на вашем устройстве. Разработчик не создаёт учётных записей, не собирает, не продаёт " +
                "и не передаёт ваши личные данные. В приложении нет рекламы, аналитики и сторонних трекеров."
        ),
        LegalSection(
            "Что хранится на устройстве",
            "Списки воспроизведения, избранное, закладки, позиции прослушивания, точки A–B, добавленные радиостанции, " +
                "настройки звука и интерфейса, а также обложки, найденные в ваших аудиофайлах, хранятся только в закрытой " +
                "памяти приложения. Они не отправляются разработчику или третьим лицам и не включаются в резервное " +
                "копирование Android. Всё удаляется вместе с приложением или при очистке его данных в настройках Android."
        ),
        LegalSection(
            "Доступ к файлам",
            "Приложение читает только те аудиофайлы и папки, которые вы сами выбрали в системном окне выбора файлов " +
                "или открыли из другой программы через «Открыть в…» или «Поделиться». Ваши файлы не изменяются " +
                "и не отправляются в сеть."
        ),
        LegalSection(
            "Интернет и онлайн-радио",
            "Доступ в интернет нужен только для воспроизведения онлайн-радио и загрузки списков станций (.m3u, .m3u8) " +
                "по ссылкам, которые вы добавили сами или выбрали из встроенного списка. При подключении к потоку сервер " +
                "радиостанции получает ваш IP-адрес и технические сведения запроса — так работает любое онлайн-вещание; " +
                "правила обработки этих данных устанавливает владелец станции. Ссылки, начинающиеся с http://, " +
                "передаются без шифрования."
        ),
        LegalSection(
            "Разрешения",
            "Интернет, состояние сети и Wi-Fi — для онлайн-радио. Уведомления — для управления воспроизведением с экрана " +
                "блокировки и из шторки. Работа в фоне и удержание процессора — чтобы звук не прерывался при выключенном " +
                "экране. Приложение не запрашивает доступ к микрофону, камере, контактам и геолокации и не получает " +
                "полный доступ к памяти устройства."
        ),
        LegalSection(
            "Датчик движения",
            "Если включена функция «Продлевать постукиванием», то в последнюю минуту перед остановкой таймера сна " +
                "приложение использует акселерометр, чтобы распознать постукивание по устройству. Показания датчика " +
                "не записываются и не передаются."
        ),
        LegalSection(
            "Внешние ссылки",
            "Пункты «Написать разработчику» и «Поддержать проект» открывают Telegram и сайт в другом приложении или " +
                "браузере. Их политика конфиденциальности действует независимо от этой."
        ),
        LegalSection(
            "Дети",
            "Приложение не собирает сведения ни о ком, в том числе о детях."
        ),
        LegalSection(
            "Изменения",
            "При изменении политики обновлённый текст появится в этом разделе вместе с новой версией приложения."
        ),
        LegalSection(
            "Связь",
            "Вопросы о конфиденциальности можно задать через пункт «Написать разработчику» в разделе «О приложении»."
        )
    )

    const val LICENSES_TITLE = "Лицензии"

    val appLicense = LegalSection(
        "Лицензия приложения",
        "ABPlayer © 2026 Brandmauer. Приложение предоставляется «как есть», без каких-либо гарантий. " +
            "Все права на приложение, его название и значок принадлежат разработчику."
    )

    const val OSS_TITLE = "Компоненты с открытым кодом"
    const val OSS_INTRO = "В приложении использованы следующие компоненты. Все они распространяются по лицензии Apache License 2.0."

    /** Название компонента → правообладатель. */
    val components: List<Pair<String, String>> = listOf(
        "Jetpack Compose (UI, Foundation, Material 3)" to "© The Android Open Source Project",
        "AndroidX Core, Activity, DocumentFile" to "© The Android Open Source Project",
        "AndroidX Media3 (ExoPlayer, Session, HLS)" to "© The Android Open Source Project",
        "Sonic — изменение скорости и тона, в составе Media3" to "Apache License 2.0",
        "Kotlin и kotlinx.coroutines" to "© JetBrains s.r.o. и участники проекта Kotlin",
        "Coil" to "© Coil Contributors",
        "Guava" to "© The Guava Authors"
    )

    const val APACHE_SHOW = "Показать текст Apache License 2.0"
    const val APACHE_HIDE = "Скрыть текст Apache License 2.0"
    const val APACHE_NOTE = "Текст лицензии приведён на английском языке — это её официальная редакция."

    /** Английские переводы для словаря I18n. */
    val en: Map<String, String> = mapOf(
        PRIVACY_TITLE to "Privacy Policy",
        PRIVACY_DATE to "Effective October 3, 2026",
        "Коротко" to "In short",
        privacy[0].body to "ABPlayer works on your device. The developer does not create accounts and does not collect, sell or share your personal data. The app contains no ads, analytics or third-party trackers.",
        "Что хранится на устройстве" to "What is stored on your device",
        privacy[1].body to "Playlists, favorites, bookmarks, listening positions, A–B points, added radio stations, sound and interface settings, and cover art found in your audio files are stored only in the app's private storage. They are not sent to the developer or third parties and are not included in Android backups. Everything is removed together with the app or when you clear its data in Android settings.",
        "Доступ к файлам" to "File access",
        privacy[2].body to "The app reads only the audio files and folders that you chose yourself in the system file picker or opened from another app via \"Open with…\" or \"Share\". Your files are not modified and are not sent over the network.",
        "Интернет и онлайн-радио" to "Internet and online radio",
        privacy[3].body to "Internet access is needed only to play online radio and to download station lists (.m3u, .m3u8) from links that you added yourself or picked from the built-in list. When you connect to a stream, the station's server receives your IP address and technical request details — this is how any online broadcast works; the station owner sets the rules for handling that data. Links starting with http:// are sent unencrypted.",
        "Разрешения" to "Permissions",
        privacy[4].body to "Internet, network state and Wi-Fi — for online radio. Notifications — to control playback from the lock screen and the notification shade. Background operation and wake lock — so that sound does not stop when the screen is off. The app does not request access to the microphone, camera, contacts or location, and does not get full access to device storage.",
        "Датчик движения" to "Motion sensor",
        privacy[5].body to "If \"Extend by tapping\" is on, then during the last minute before the sleep timer stops, the app uses the accelerometer to detect a tap on the device. Sensor readings are not recorded or sent anywhere.",
        "Внешние ссылки" to "External links",
        privacy[6].body to "\"Contact the developer\" and \"Support the project\" open Telegram and a website in another app or a browser. Their privacy policies apply independently of this one.",
        "Дети" to "Children",
        privacy[7].body to "The app does not collect information about anyone, including children.",
        "Изменения" to "Changes",
        privacy[8].body to "If this policy changes, the updated text will appear in this section together with a new app version.",
        "Связь" to "Contact",
        privacy[9].body to "You can ask privacy questions via \"Contact the developer\" in the \"About\" section.",
        LICENSES_TITLE to "Licenses",
        "Лицензия приложения" to "App license",
        appLicense.body to "ABPlayer © 2026 Brandmauer. The app is provided \"as is\", without warranties of any kind. All rights to the app, its name and icon belong to the developer.",
        OSS_TITLE to "Open-source components",
        OSS_INTRO to "The app uses the components listed below. All of them are distributed under the Apache License 2.0.",
        "Sonic — изменение скорости и тона, в составе Media3" to "Sonic — speed and pitch change, part of Media3",
        "© JetBrains s.r.o. и участники проекта Kotlin" to "© JetBrains s.r.o. and Kotlin project contributors",
        APACHE_SHOW to "Show Apache License 2.0 text",
        APACHE_HIDE to "Hide Apache License 2.0 text",
        APACHE_NOTE to "The license text is given in English — this is its official version.",
        "Развёрнуто" to "Expanded",
        "Свёрнуто" to "Collapsed"
    )
}
