package fr.nzosifou.agas

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.runtime.AgentRuntime
import fr.nzosifou.agas.ui.AgasApp
import fr.nzosifou.agas.ui.theme.AgasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgasLog.attach(this)
        AgentRuntime.init(this)
        // Interface Nocturne toujours sombre : icônes claires dans les barres système.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            AgasTheme {
                AgasApp()
            }
        }
    }
}
