package com.mybook.sanctify

import android.app.Application
import com.mybook.sanctify.data.AppDb

class App : Application() {
    val db: AppDb by lazy { AppDb.build(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}
