package com.drbaysinger.audiobc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column

private const val ABOUT_EN = "Donate to Dr. Baysinger. He promises to spend it on hookers and beer."
private const val ABOUT_BG = "Дарете на Dr. Baysinger. Той обещава да ги изхарчи за курви и бело."
private const val REVOLUT = "revolut.me/georgi09qc"
private val BTC = listOf(
    "bc1qr84akpfry7sjs6759wwq722fyert9apcarpst6",
    "bc1p6hp5q20u9t9upsjt7425wgqdrs0cr8wnnlf9atu24z92pzsnq85q62uc8e",
)

@Composable
fun AboutDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface,
        titleContentColor = Text,
        textContentColor = Text,
        confirmButton = {
            TextButton(onClick = onClose) { Text("OK", color = Accent) }
        },
        title = { Text("About", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(ABOUT_EN)
                Text(ABOUT_BG, modifier = Modifier.padding(top = 12.dp))
                Text("Revolut", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                CopyLine(REVOLUT, context)
                Text("BTC address", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                BTC.forEach { CopyLine(it, context) }
            }
        },
    )
}

@Composable
private fun CopyLine(value: String, context: Context) {
    Text(
        value,
        color = Accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("address", value))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 6.dp),
    )
}
