package com.limersvp.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limersvp.R
import com.limersvp.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Component(val name: Int?, val title: String, val license: String, val files: List<String>)

private val components = listOf(
    Component(R.string.licenses_app, "", "GPL-3.0-or-later", listOf("LimeRSVP-GPL-3.0.txt")),
    Component(R.string.licenses_app_terms, "", "GPLv3 §7", listOf("LimeRSVP-additional-terms.txt")),
    Component(null, "Atkinson Hyperlegible Next", "OFL-1.1", listOf("AtkinsonHyperlegibleNext-OFL.txt")),
    Component(null, "jsoup", "MIT", listOf("jsoup-MIT.txt")),
    Component(null, "PdfBox-Android", "Apache-2.0", listOf("PdfBox-Android-NOTICE.txt", "Apache-2.0.txt")),
    Component(R.string.licenses_jetpack, "", "Apache-2.0", listOf("Apache-2.0.txt")),
)

/** Every part of the app with its license; a tap shows the full text. */
@Composable
fun Licenses(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf<Int?>(null) }
    LazyColumn(modifier.padding(vertical = 18.dp)) {
        item {
            Text(
                stringResource(R.string.licenses_title), color = p.text, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
        }
        items(components.size) { i ->
            val comp = components[i]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, null) { open = if (open == i) null else i }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(comp.name?.let { stringResource(it) } ?: comp.title, color = p.text, fontSize = 16.sp)
                Text(comp.license, color = p.dim, fontSize = 13.sp)
                AnimatedVisibility(open == i, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    LicenseText(comp.files)
                }
            }
        }
    }
}

@Composable
private fun LicenseText(files: List<String>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var text by remember(files) { mutableStateOf("") }
    LaunchedEffect(files) {
        text = withContext(Dispatchers.IO) {
            files.joinToString("\n\n") { f ->
                runCatching { context.assets.open("licenses/$f").bufferedReader().use { it.readText() } }.getOrDefault("")
            }
        }
    }
    Text(
        text, color = p.dim, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 10.dp),
    )
}
