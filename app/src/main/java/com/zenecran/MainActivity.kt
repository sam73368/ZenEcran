package com.zenecran

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zenecran.data.Store
import com.zenecran.ui.MainViewModel
import com.zenecran.ui.Z
import com.zenecran.ui.ZenApp
import com.zenecran.ui.ZenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Z.apply(Store.config.value)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ZenTheme {
                val vm: MainViewModel = viewModel()
                ZenApp(vm)
            }
        }
    }
}
