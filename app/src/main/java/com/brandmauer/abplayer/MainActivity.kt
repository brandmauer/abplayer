package com.brandmauer.abplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.brandmauer.abplayer.ui.AppRoot
import com.brandmauer.abplayer.util.signed

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат не важен */ }

    private val hub get() = (application as ABPlayerApp).hub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Уведомление с управлением плеером (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { AppRoot(hub) }

        // Файл, открытый/отправленный из другой программы при холодном старте
        // (при восстановлении после смерти процесса интент уже был обработан).
        if (savedInstanceState == null) handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    /** «Открыть в…» (ACTION_VIEW) и «Поделиться» (ACTION_SEND/SEND_MULTIPLE): треки идут в плейлист Default. */
    private fun handleExternalIntent(intent: Intent?) {
        if (intent == null) return
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris += it }
            }
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { uris += it }
            }
            else -> return
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it }
        }
        if (uris.isEmpty()) return
        // Интент обработан — чтобы он не сработал повторно при возврате в приложение.
        setIntent(Intent(this, MainActivity::class.java))
        hub.onExternalUris(uris.toList(), play = intent.action == Intent.ACTION_VIEW)
    }

    /**
     * Пока включена «Volume fine-tune» и трек реально играет, физические кнопки громкости
     * управляют не системной громкостью, а тонкой подстройкой громкости плеера — с шагом 1%,
     * тем же ползунком/значением, что в шторке «Volume and Sound».
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (hub.data.sound.volumeFineEnabled && hub.isPlaying &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            val step = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
            val next = (hub.data.sound.volumeTrim + step).coerceIn(-100, 100)
            hub.setTrim(next)
            hub.toast("Громкость плеера: ${signed(next)}%")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (hub.data.sound.volumeFineEnabled && hub.isPlaying &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        hub.onUiShown()
    }

    override fun onStop() {
        super.onStop()
        hub.onUiHidden()
    }
}
