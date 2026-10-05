package com.brandmauer.abplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brandmauer.abplayer.Hub
import com.brandmauer.abplayer.R
import com.brandmauer.abplayer.i18n.LegalSection
import com.brandmauer.abplayer.i18n.LegalText

/** Заголовок шторки: для TalkBack помечается как заголовок. */
@Composable
private fun LegalTitle(text: String, bottom: Int = 8) {
    val c = LocalColors.current
    Txt(
        text, 16f, FontWeight.ExtraBold, c.text,
        modifier = Modifier.fillMaxWidth().padding(bottom = bottom.dp).semantics { heading() },
        textAlign = TextAlign.Center
    )
}

/** Раздел текста: акцентный заголовок и абзац. */
@Composable
private fun LegalBlock(section: LegalSection) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Txt(
            section.title, 13f, FontWeight.Bold, c.accent2,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
        )
        Txt(section.body, 13f, color = c.textDim, lineHeight = 19f)
    }
}

/** «Политика конфиденциальности»: открывается из «О приложении». */
@Composable
fun PrivacySheet(hub: Hub) {
    val c = LocalColors.current
    SheetHandle()
    LegalTitle(LegalText.PRIVACY_TITLE, bottom = 2)
    Txt(
        LegalText.PRIVACY_DATE, 12f, color = c.textFaint,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = TextAlign.Center
    )
    Column(Modifier.fillMaxWidth()) {
        LegalText.privacy.forEach { LegalBlock(it) }
    }
    PrimaryButton("Закрыть", { hub.closeSheet() }, Modifier.padding(top = 18.dp), secondary = true)
}

/** «Лицензии»: лицензия приложения, список компонентов с открытым кодом и полный текст Apache License 2.0. */
@Composable
fun LicensesSheet(hub: Hub) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var showText by remember { mutableStateOf(false) }
    // полный текст лицензии лежит в res/raw; читаем один раз
    val apacheText = remember {
        runCatching {
            ctx.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    SheetHandle()
    LegalTitle(LegalText.LICENSES_TITLE)
    Column(Modifier.fillMaxWidth()) {
        LegalBlock(LegalText.appLicense)
        LegalBlock(LegalSection(LegalText.OSS_TITLE, LegalText.OSS_INTRO))
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            LegalText.components.forEachIndexed { i, (name, owner) ->
                Column(Modifier.fillMaxWidth().a11yRow().padding(vertical = 9.dp)) {
                    Txt(name, 13f, FontWeight.SemiBold, c.text)
                    Txt(owner, 12f, color = c.textFaint, modifier = Modifier.padding(top = 2.dp))
                }
                if (i < LegalText.components.lastIndex) Divider1()
            }
        }
        if (apacheText.isNotEmpty()) {
            Pressable(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                pressed = c.surface2,
                onClick = { showText = !showText },
                stateText = if (showText) "Развёрнуто" else "Свёрнуто"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Txt(
                        if (showText) LegalText.APACHE_HIDE else LegalText.APACHE_SHOW,
                        13f, FontWeight.Bold, c.accent2, modifier = Modifier.weight(1f)
                    )
                }
            }
            if (showText) {
                Txt(
                    LegalText.APACHE_NOTE, 12f, color = c.textFaint,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // текст лицензии показываем как есть, без перевода
                Text(
                    text = apacheText.trim(),
                    color = c.textDim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    PrimaryButton("Закрыть", { hub.closeSheet() }, Modifier.padding(top = 18.dp), secondary = true)
}
