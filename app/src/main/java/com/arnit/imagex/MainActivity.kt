package com.arnit.imagex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.navigation.ImageXNavHost
import com.arnit.imagex.ui.theme.ImageXTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AppViewModel(application) }
                }
            )
            val settings by vm.appSettings.collectAsState()
            ImageXTheme(themeMode = settings.theme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImageXNavHost(vm = vm, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
