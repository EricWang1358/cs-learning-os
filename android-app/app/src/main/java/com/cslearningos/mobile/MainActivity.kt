package com.cslearningos.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val sharedPackageUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPackageUri.value = intent?.sharedStreamUri()
        setContent {
            com.cslearningos.mobile.ui.LearningOsApp(
                sharedPackageUri = sharedPackageUri.value,
                onSharedPackageConsumed = { sharedPackageUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedStreamUri()?.let { uri ->
            sharedPackageUri.value = uri
        }
    }

    private fun Intent.sharedStreamUri(): Uri? =
        when (action) {
            Intent.ACTION_SEND ->
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
}
