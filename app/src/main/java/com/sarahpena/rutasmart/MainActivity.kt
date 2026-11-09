package com.sarahpena.rutasmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sarahpena.rutasmart.ui.navegacion.RutaSmartRaiz
import com.sarahpena.rutasmart.ui.tema.RutaSmartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RutaSmartTheme { RutaSmartRaiz() } }
    }
}
