package jp.familoc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SetupScreen() } }
    }

    @Composable
    private fun SetupScreen() {
        val refresh = remember { mutableStateOf(0) }
        refresh.value
        val foreground = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh.value++ }
        val token = remember {
            mutableStateOf("取得中").also { state ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener {
                    state.value = if (it.isSuccessful) it.result else "取得失敗"
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("FamiLoc Background Location PoC", style = MaterialTheme.typography.headlineSmall)
            Text("正確な位置情報: ${if (foreground) "許可済み" else "未許可"}")
            Text("常時の位置情報: ${if (background) "許可済み" else "未許可"}")
            Text("通知: ${if (notifications) "許可済み" else "未許可"}")
            Button(onClick = {
                val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                launcher.launch(permissions.toTypedArray())
            }) { Text("基本権限を許可") }
            Button(onClick = {
                if (Build.VERSION.SDK_INT == 29) launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }) { Text("常時の位置情報を設定") }
            Button(onClick = {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { runCatching {
                    Thread { Backend.uploadFcmToken(it) }.start()
                } }
            }) { Text("FCM token をテスト API に登録") }
            Text("FCM token: ${token.value}", style = MaterialTheme.typography.bodySmall)
            Text("API: ${if (BuildConfig.API_BASE_URL.startsWith("https://")) "設定済み" else "未設定"}")
        }
    }
}
