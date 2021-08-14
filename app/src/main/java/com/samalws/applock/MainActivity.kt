package com.samalws.applock

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.opengl.Visibility
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
import android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import com.google.android.material.tabs.TabLayout
import java.util.*
import kotlin.concurrent.timerTask

private enum class DialogReason {
    NO_REASON,
    PASSWORD,
    SERVICE
}

class MainActivity : AppCompatActivity() {
    private val sharedPreferences : SharedPreferences
        get() = getSharedPreferences("prefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scheduleInitialChecks()

        listenTabChange()

        listenPasswordEnter()

        listenInitialGo()

        val timer = Timer()
        timer.scheduleAtFixedRate(timerTask { runOnUiThread {
            updateLockStatusText()
        }}, 0, 5000) // ms
        // TODO LATER might be able to get rid of the timer task

        timer.scheduleAtFixedRate(timerTask { runOnUiThread {
            maybeGenerateList()
        }}, 0, 10000) // ms
    }


    // INITIAL SETUP RELATED

    private var currentDialog: AlertDialog? = null
    private var currentDialogReason = DialogReason.NO_REASON
    private val dialogShowing: Boolean
        get() = currentDialog?.isShowing == true
    private fun scheduleInitialChecks() {
        val timer = Timer()
        timer.scheduleAtFixedRate(timerTask { runOnUiThread {
            if (!initialChecks())
                timer.cancel()
        }}, 0, 100) // ms
    }
    private fun initialChecks(): Boolean { // returns true if we should continue running the timer for checks
        if (dialogShowing) {
            // if we're showing the wrong dialog, close it and then show a new one below
            if (Util.getPasswordSet(sharedPreferences) && currentDialogReason == DialogReason.PASSWORD
             || serviceIsRunning                       && currentDialogReason == DialogReason.SERVICE)
                currentDialog?.cancel()

            // otherwise, just let the dialog be and wait until the user is done
            else
                return true
        }

        // now we know there's no dialog showing; let's see if we should put one up
        if (!Util.getPasswordSet(sharedPreferences)) {
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            input.imeOptions = EditorInfo.IME_ACTION_GO
            input.setHint(R.string.password_alert_hint)
            Util.listenForGo(input) { passwordAlertEnter(input) }

            currentDialog = Util.makeAlert(
                this,
                R.string.password_alert,
                R.string.password_alert_button,
                { passwordAlertEnter(input) },
                input)
            currentDialogReason = DialogReason.PASSWORD

            val margin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f, // 20dp
                resources.displayMetrics
            ).toInt()
            val marginParams = input.layoutParams as? ViewGroup.MarginLayoutParams
            marginParams?.setMargins(margin, 0, margin, 0)

            return true
        } else if (!serviceIsRunning) {
            currentDialog = Util.makeAlert(
                this,
                R.string.service_alert,
                R.string.service_alert_button,
                { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            currentDialogReason = DialogReason.SERVICE
            return true
        }

        return false
    }
    private fun passwordAlertEnter(input: EditText) {
        if (input.text.isNotEmpty()) {
            Util.setPassword(sharedPreferences, input.text.toString())
            Util.makeToast(this, R.string.password_set_success_toast)
            currentDialog?.cancel()
        } else
            Util.makeToast(this, R.string.no_blank_pass_toast)
    }


    // CHANGE PASS RELATED

    @Suppress("UNUSED_PARAMETER")
    // for button click you need to take a View
    fun changePassButton(view: View) { changePassButton() }
    private fun changePassButton() {
        val text1 = findViewById<EditText>(R.id.current_password    ).text
        val text2 = findViewById<EditText>(R.id.new_password        ).text
        val text3 = findViewById<EditText>(R.id.confirm_new_password).text
        val pass1 = text1.toString()
        val pass2 = text2.toString()
        val pass3 = text3.toString()

        if (!Util.passwordCorrect(sharedPreferences, pass1))
            Util.makeToast(this, R.string.current_password_wrong_toast)
        else if (pass2 != pass3)
            Util.makeToast(this, R.string.new_password_not_match_toast)
        else if (pass2.isEmpty())
            Util.makeToast(this, R.string.no_blank_pass_toast)
        else { // success
            Util.setPassword(sharedPreferences, pass2)
            Util.makeToast(this, R.string.password_change_success_toast)
            text1.clear()
            text2.clear()
            text3.clear()
        }
    }

    private fun listenPasswordEnter() {
        Util.listenForGo(findViewById(R.id.confirm_new_password)) { changePassButton() }
    }



    // TABS RELATED

    private fun tabToContent(tab: TabLayout.Tab?): View? =
        when (tab?.text) {
            getString(R.string.main_screen_tab_1) ->
                findViewById(R.id.tab_1_content)
            getString(R.string.main_screen_tab_2) ->
                findViewById(R.id.tab_2_content)
            else -> null
        }

    private fun listenTabChange() {
        findViewById<TabLayout>(R.id.tab_layout)
            .addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {
                tabToContent(tab)?.visibility = View.VISIBLE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tabToContent(tab)?.visibility = View.GONE
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // do nothing
            }
        })
    }



    // APP LIST RELATED

    private val appList
        get() = findViewById<LinearLayout>(R.id.app_list)
    private fun clearList() {
        appList.removeAllViews()
    }
    private fun addListItem(applicationInfo: ApplicationInfo) {
        // TODO LATER remove settings from the list too?
        if (applicationInfo.packageName == BuildConfig.APPLICATION_ID || applicationInfo.packageName == "com.android.settings")
            return

        val item = View.inflate(this, R.layout.app_list_item, null)

        val checkBox = item.findViewById<CheckBox>(R.id.app_list_item_checkbox)

        val pkgName = applicationInfo.packageName
        checkBox.isChecked = Util.getAppBlocked(sharedPreferences, pkgName)
        checkBox.text = Util.nameOfApp(this, applicationInfo)

        checkBox.setOnCheckedChangeListener { _, checked ->
            Util.setAppBlocked(sharedPreferences, pkgName, checked)
        }

        appList.addView(item)
    }
    private fun generateList() {
        clearList()
        for (app in Util.currentlyInstalledApps(this))
            addListItem(app)
    }

    private var currentlyListed = emptySet<String>()
    private fun maybeGenerateList() {
        val pkgList = Util.getAppPkgNameList(this)
        if (pkgList != currentlyListed) {
            generateList()
            currentlyListed = pkgList
        }
    }



    // SERVICE RELATED

    private val serviceIsRunning: Boolean
        get() {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            @Suppress("DEPRECATION")
            // still works, just only returns services running for this app (which is all we need)
            for (service in manager.getRunningServices(Int.MAX_VALUE))
                if (LockService::class.java.name == service.service.className)
                    return true
            return false
        }


    // LOCK STATUS RELATED

    private val lockStatus: Boolean
        get() = Util.getLockStatus(sharedPreferences)
    private fun toggleLock() {
        Util.setLockStatus(sharedPreferences, !lockStatus)
        updateLockStatusText()
    }

    @Suppress("UNUSED_PARAMETER")
    // for button click you need to take a View
    fun lockToggleButton(view: View) { lockToggleButton() }
    private fun lockToggleButton() {
        toggleLock()
    }

    private fun updateLockStatusText() {
        findViewById<TextView>(R.id.lock_status_text).text = getString(
            if (lockStatus) R.string.lock_text_on
            else R.string.lock_text_off
        )
        findViewById<Button>(R.id.lock_toggle_button).text = getString(
            if (lockStatus) R.string.lock_button_on
            else R.string.lock_button_off
        )
    }


    // INITIAL PASSWORD RELATED

    @Suppress("UNUSED_PARAMETER")
    fun initialSubmit(view: View) { initialSubmit() }
    private fun initialSubmit() {
        if (Util.passwordCorrect(sharedPreferences, findViewById<TextView>(R.id.initial_password).text.toString())) {
            findViewById<View>(R.id.initial_password_area).visibility = View.GONE
            findViewById<View>(R.id.main_area).visibility = View.VISIBLE
        } else {
            Util.makeToast(this, R.string.initial_password_wrong_toast)
        }
    }
    private fun listenInitialGo() {
        Util.listenForGo(findViewById(R.id.initial_password)) { initialSubmit() }
    }
}