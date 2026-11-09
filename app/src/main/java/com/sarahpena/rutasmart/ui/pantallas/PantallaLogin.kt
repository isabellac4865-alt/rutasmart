package com.sarahpena.rutasmart.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarahpena.rutasmart.ui.SesionViewModel

@Composable
fun PantallaLogin(vm: SesionViewModel) {
    val ui by vm.ui.collectAsState()
    var registrando by rememberSaveable { mutableStateOf(false) }
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var confirmacion by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🧭", fontSize = 60.sp)
        Text("RutaSmart", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Tu asistente para elegir la mejor forma de llegar",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))

        if (!ui.firebaseDisponible) {
            Tarjeta {
                Text("🔌 Firebase aún no está conectado", fontWeight = FontWeight.Bold)
                Text(
                    "Para crear cuentas y guardar el historial en la nube, agrega el archivo google-services.json " +
                        "(ver README). Mientras tanto puedes usar la app como invitado: todo se guarda en el teléfono.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Tarjeta {
            Text(if (registrando) "Crear cuenta" else "Iniciar sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = correo, onValueChange = { correo = it; vm.limpiarMensajes() },
                label = { Text("Correo") }, singleLine = true, enabled = !ui.cargando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().testTag("campo_correo")
            )
            OutlinedTextField(
                value = clave, onValueChange = { clave = it; vm.limpiarMensajes() },
                label = { Text("Contraseña") }, singleLine = true, enabled = !ui.cargando,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("campo_clave")
            )
            if (registrando) {
                OutlinedTextField(
                    value = confirmacion, onValueChange = { confirmacion = it; vm.limpiarMensajes() },
                    label = { Text("Confirmar contraseña") }, singleLine = true, enabled = !ui.cargando,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            MensajeError(ui.error)
            ui.mensaje?.let { Text("✅ $it", color = MaterialTheme.colorScheme.tertiary) }
            Button(
                onClick = { if (registrando) vm.registrar(correo, clave, confirmacion) else vm.iniciarSesion(correo, clave) },
                enabled = !ui.cargando && ui.firebaseDisponible,
                modifier = Modifier.fillMaxWidth().testTag("btn_entrar")
            ) {
                if (ui.cargando) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                else Text(if (registrando) "Crear cuenta" else "Entrar")
            }
            if (!registrando) {
                TextButton(onClick = { vm.recuperarClave(correo) }, enabled = !ui.cargando && ui.firebaseDisponible) {
                    Text("¿Olvidaste tu contraseña?")
                }
            }
            TextButton(onClick = { registrando = !registrando; vm.limpiarMensajes() }, enabled = ui.firebaseDisponible) {
                Text(if (registrando) "Ya tengo cuenta" else "¿No tienes cuenta? Regístrate")
            }
        }

        OutlinedButton(
            onClick = { vm.entrarComoInvitado() },
            modifier = Modifier.fillMaxWidth().testTag("btn_invitado")
        ) { Text("Continuar como invitado") }
        Text(
            "Como invitado el historial queda solo en este teléfono.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
