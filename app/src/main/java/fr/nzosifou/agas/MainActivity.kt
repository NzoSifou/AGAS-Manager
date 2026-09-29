package fr.nzosifou.agas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.ui.AgasScreen
import fr.nzosifou.agas.ui.theme.AgasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgasLog.attach(this)
        enableEdgeToEdge()
        setContent {
            AgasTheme {
                AgasScreen()
            }
        }
    }
}
