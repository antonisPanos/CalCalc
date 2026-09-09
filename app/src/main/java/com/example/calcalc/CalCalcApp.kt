package com.example.calcalc

import android.app.Application

class CalCalcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
