@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sarahpena.rutasmart.domain.ia.AiConfig
import com.sarahpena.rutasmart.domain.ia.ContextoRuta
import com.sarahpena.rutasmart.domain.ia.MsgSource
import com.sarahpena.rutasmart.ui.AsistenteViewModel
import com.sarahpena.rutasmart.ui.Mensaje

private val SUGERENCIAS = listOf(
    "¿Por qué es la mejor opción?", "¿Cuál es la más económica?", "¿Y en hora pico?",
    "¿Cuál contamina menos?", "¿Por qué no el bus?", "Muéstrame todas"
)

@Composable
fun PantallaAsistente(vm: AsistenteViewModel, contexto: () -> ContextoRuta) {
    var texto by rememberSaveable { mutableStateOf("") }
    val lista = rememberLazyListState()
    LaunchedEffect(vm.mensajes.size, vm.escribiendo) {
        val total = vm.mensajes.size + if (vm.escribiendo) 1 else 0
        if (total > 0) lista.animateScrollToItem(total - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("aviso_ia")
        ) {
            Text(
                if (vm.iaConfigurada) "🤖 Rutina es un asistente con inteligencia artificial (${AiConfig.PROVIDER}). Puede equivocarse: los tiempos y tarifas son aproximados."
                else "⚙️ Rutina está en modo local sin IA (falta configurar GEMINI_API_KEY). Igual responde con los cálculos de la app.",
                style = MaterialTheme.typography.labelMedium
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            state = lista,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(vm.mensajes) { _, m -> Burbuja(m) }
            if (vm.escribiendo) item { Burbuja(Mensaje("Rutina está pensando…", MsgSource.LOCAL)) }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SUGERENCIAS.forEach { s -> AssistChip(onClick = { vm.enviar(s, contexto()) }, label = { Text(s) }) }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                placeholder = { Text("Pregúntale a Rutina…") },
                maxLines = 3,
                supportingText = if (texto.length > AiConfig.MAX_INPUT_CHARS - 50) {
                    { Text("${texto.length}/${AiConfig.MAX_INPUT_CHARS}") }
                } else null,
                modifier = Modifier.weight(1f).testTag("campo_chat")
            )
            Button(
                onClick = { vm.enviar(texto, contexto()); texto = "" },
                enabled = texto.isNotBlank() && !vm.escribiendo,
                modifier = Modifier.testTag("btn_enviar")
            ) { Text("Enviar") }
        }
    }
}

@Composable
private fun Burbuja(m: Mensaje) {
    val mio = m.delUsuario
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mio) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 310.dp)
                .background(
                    if (mio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = (if (mio) "Tú: " else if (m.fuente == MsgSource.AI) "Rutina, con IA: " else "Rutina: ") + m.texto
                }
        ) {
            if (!mio) {
                Text(
                    if (m.fuente == MsgSource.AI) "🤖 IA · ${AiConfig.PROVIDER}" else "⚙️ Respuesta local",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(m.texto, color = if (mio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
