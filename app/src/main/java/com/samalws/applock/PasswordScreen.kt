package com.samalws.applock

import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import kotlin.collections.HashMap


class PasswordScreen(private val context: Context, private val sharedPreferences: SharedPreferences) {
    private val view = View.inflate(context, R.layout.password_screen, null)
    private val params = WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
    private val winMgr = context.getSystemService(Service.WINDOW_SERVICE) as WindowManager

    init {
        Util.listenForGo(view.findViewById(R.id.password)) { onEnter() }
        view.findViewById<Button>(R.id.submit).setOnClickListener { onEnter() }
        view.findViewById<Button>(R.id.cancel).setOnClickListener {
            Util.goToHomeScreen(context)
            hide()
        }
    }

    private var passwordEntered
        get() = view.findViewById<EditText>(R.id.password).text.toString()
        set(value) {
            view.findViewById<EditText>(R.id.password).setText(value)
        }
    private val passwordCorrect
        get() = Util.passwordCorrect(sharedPreferences, passwordEntered)

    private fun onEnter() {
        if (passwordCorrect) {
            unlockApp(currentId,
                view.findViewById<CheckBox>(R.id.persistent_check).isChecked,
                view.findViewById<CheckBox>(R.id.unlock_all_check).isChecked)
            hide()
        } else
            view.findViewById<EditText>(R.id.password).setTextColor(Color.RED)
    }

    private var unlockedDict = HashMap<String, Boolean>()
    private var persistentUnlockedDict = HashMap<String, Boolean>()
    private var allUnlocked = false
    private var allUnlockedPersistent = false
    private fun appUnlocked(id: String) : Boolean =
        unlockedDict[id] == true
     || persistentUnlockedDict[id] == true
     || allUnlocked
     || allUnlockedPersistent
    private fun unlockApp(id: String, persistent: Boolean, all: Boolean) {
        if (all && persistent)
            allUnlockedPersistent = true
        else if (all && !persistent)
            allUnlocked = true
        else if (!all && persistent)
            persistentUnlockedDict[id] = true
        else if (!all && !persistent)
            unlockedDict[id] = true
    }
    fun resetUnlocks(includingPersistent: Boolean = false) {
        unlockedDict = HashMap()
        allUnlocked = false
        if (includingPersistent) {
            persistentUnlockedDict = HashMap()
            allUnlockedPersistent = false
        }
    }

    private var currentLockNum = Util.getLockNum(sharedPreferences)
    private fun checkLockNum() {
        val lockNum = Util.getLockNum(sharedPreferences)
        if (lockNum > currentLockNum) {
            resetUnlocks(true)
            currentLockNum = lockNum
        }
    }

    private var currentId = ""
    fun showForId(id: String) {
        checkLockNum()

        currentId = id
        if (appUnlocked(id))
            hide()
        else
            show()
    }

    private var viewShown = false
    private fun show() {
        view.requestFocus()
        if (viewShown) return
        viewShown = true
        passwordEntered = ""

        view.findViewById<EditText>(R.id.password).setTextColor(Color.BLACK)

        winMgr.addView(view, params)
    }
    fun hide() {
        if (!viewShown) return
        viewShown = false
        winMgr.removeView(view)
    }
}