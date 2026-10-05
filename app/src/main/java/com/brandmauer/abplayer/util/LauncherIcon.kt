package com.brandmauer.abplayer.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Значок приложения в лаунчере повторяет тему приложения: тёмная тема — тёмный значок, светлая — светлый.
 * Реализовано двумя activity-alias в манифесте (.LauncherDark / .LauncherLight), включён всегда один.
 *
 * Меняем значок не сразу, а когда приложение уходит с экрана (MainActivity.onStop): переключение алиасов
 * на некоторых лаунчерах на секунду «мигает» ярлыком, и лучше, чтобы это происходило не на глазах у человека.
 * Если состояние уже нужное — ничего не делаем (никаких лишних вызовов PackageManager).
 */
object LauncherIcon {
    /** Желаемая тема значка; пишется из AppRoot при каждой смене темы. */
    @Volatile var wantLight: Boolean = false

    fun apply(context: Context) {
        val pm = context.packageManager
        val dark = ComponentName(context, "com.brandmauer.abplayer.LauncherDark")
        val light = ComponentName(context, "com.brandmauer.abplayer.LauncherLight")
        val lightOn = wantLight
        fun on(c: ComponentName) = pm.getComponentEnabledSetting(c).let {
            it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (it == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && c == dark)
        }
        if (on(light) == lightOn && on(dark) != lightOn) return
        try {
            // сначала включаем нужный, потом выключаем второй — чтобы ярлык приложения не исчезал совсем
            pm.setComponentEnabledSetting(
                if (lightOn) light else dark,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                if (lightOn) dark else light,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
            // значок — украшение; при любой ошибке остаётся прежний
        }
    }
}
