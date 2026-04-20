package com.openclaw.tv.feature.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow

class HomeTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                replace(
                    android.R.id.content,
                    HomeFragment.newInstance(
                        platformBaseUrl = null,
                        enableRemoteConfig = false,
                    ),
                )
            }
        }
    }
}
