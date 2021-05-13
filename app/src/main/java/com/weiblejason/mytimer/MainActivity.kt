package com.weiblejason.mytimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.TextureView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.weiblejason.mytimer.databinding.ActivityMainBinding
import com.weiblejason.mytimer.util.PrefUtil
import me.zhanghai.android.materialprogressbar.MaterialProgressBar
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        fun setAlarm(context: Context, nowSeconds: Long, secondsRemaining: Long): Long {
            val wakeUpTime = (nowSeconds + secondsRemaining) * 1000
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeUpTime, pendingIntent)
            PrefUtil.setAlarmSetTime(nowSeconds, context)
            return wakeUpTime
        }

        fun removeAlarm(context: Context) {
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            PrefUtil.setAlarmSetTime(0, context)
        }


        val nowSeconds: Long
            get() = Calendar.getInstance().timeInMillis / 1000
    }

    enum class TimerState{
        Stopped, Paused, Running
    }

    private lateinit var timer: CountDownTimer
    private var timerLengthSeconds = 0L
    private var timerState = TimerState.Stopped
    private var secondsRemaining = 0L

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        supportActionBar?.setIcon(R.drawable.ic_timer)
        supportActionBar?.title = "     Pomodoro Timer"

        val fabStart = findViewById<FloatingActionButton>(R.id.fabStart)
        val fabPause = findViewById<FloatingActionButton>(R.id.fabPause)
        val fabStop = findViewById<FloatingActionButton>(R.id.fabStop)

        fabStart.setOnClickListener { y ->
            startTimer()
            timerState = TimerState.Running
            updateButtons()
        }

        fabPause.setOnClickListener { y ->
            timer.cancel()
            timerState = TimerState.Paused
            updateButtons()
        }

        fabStop.setOnClickListener { y ->
            timer.cancel()
            onTimerFinished()

        }
    }

    override  fun onResume() {
        super.onResume()

        initTimer()

        removeAlarm(this)
        // TODO: hide notification
    }

    override fun onPause() {
        super.onPause()

        if (timerState == TimerState.Running){
            timer.cancel()
            val wakeUpTime = setAlarm(this, nowSeconds, secondsRemaining)
            // TODO: show notification
        }
        else if(timerState ==TimerState.Paused){
            // TODO: show notification
        }

        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds, this)
        PrefUtil.setSecondsRemaining(secondsRemaining, this)
        PrefUtil.setTimerState(timerState, this)




    }

    private fun initTimer(){
        timerState = PrefUtil.getTimerState(this)

        if (timerState == TimerState.Stopped) {
            setNewTimerLength()
        } else {
            setPreviousTimerLength()
        }

        secondsRemaining = if (timerState == TimerState.Running || timerState == TimerState.Paused)
            PrefUtil.getSecondsRemaining(this)
        else
            timerLengthSeconds

        val alarmSetTime = PrefUtil.getAlarmSetTime(this)
        if (alarmSetTime > 0)
            secondsRemaining -= nowSeconds - alarmSetTime

        if (secondsRemaining <= 0)
            onTimerFinished()
        else if (timerState == TimerState.Running) {
            startTimer()
        }

        updateButtons()
        updateCountdownUI()

    }

    private fun onTimerFinished(){
        timerState = TimerState.Stopped

        setNewTimerLength()

        val progressCountdown = findViewById<MaterialProgressBar>(R.id.progress_countdown)
        progressCountdown.progress = 0

        PrefUtil.setSecondsRemaining(timerLengthSeconds, this)
        secondsRemaining = timerLengthSeconds

        updateButtons()
        updateCountdownUI()

    }

    private fun startTimer() {
        timerState = TimerState.Running

        timer = object : CountDownTimer(secondsRemaining * 1000, 1000){
            override fun onFinish() = onTimerFinished()

            override fun onTick(millisUntilFinished: Long){
                secondsRemaining = millisUntilFinished / 1000
                updateCountdownUI()
            }
        }.start()
    }

    private fun setNewTimerLength() {
        val lengthInMinutes = PrefUtil.getTimerLength(this)
        timerLengthSeconds = (lengthInMinutes * 60L)
        val progressCountdown = findViewById<MaterialProgressBar>(R.id.progress_countdown)
        progressCountdown.max = timerLengthSeconds.toInt()
    }

    private fun setPreviousTimerLength(){
        timerLengthSeconds = PrefUtil.getPreviousTimerLengthSeconds(this)
        val progressCountdown = findViewById<MaterialProgressBar>(R.id.progress_countdown)
        progressCountdown.max = timerLengthSeconds.toInt()
    }

    private fun updateCountdownUI(){
        val minutesUntilFinisehd = secondsRemaining / 60
        val secondsInMinutesUntilFinished = secondsRemaining - minutesUntilFinisehd * 60
        val secondsStr = secondsInMinutesUntilFinished.toString()
        val tvCountdown = findViewById<TextView>(R.id.textview_first)
        tvCountdown.text = "$minutesUntilFinisehd:${
            if (secondsStr.length == 2) secondsStr
            else "0" + secondsStr}"
        val progressCountdown = findViewById<MaterialProgressBar>(R.id.progress_countdown)
        progressCountdown.max = timerLengthSeconds.toInt()
        progressCountdown.progress = (timerLengthSeconds - secondsRemaining).toInt()
    }

    private fun updateButtons() {
        when (timerState){
            TimerState.Running -> {
                val fabStart = findViewById<FloatingActionButton>(R.id.fabStart)
                val fabPause = findViewById<FloatingActionButton>(R.id.fabPause)
                val fabStop = findViewById<FloatingActionButton>(R.id.fabStop)
                fabStart.isEnabled = false
                fabPause.isEnabled = true
                fabStop.isEnabled = true

            }
            TimerState.Stopped -> {
                val fabStart = findViewById<FloatingActionButton>(R.id.fabStart)
                val fabPause = findViewById<FloatingActionButton>(R.id.fabPause)
                val fabStop = findViewById<FloatingActionButton>(R.id.fabStop)
                fabStart.isEnabled = true
                fabPause.isEnabled = true
                fabStop.isEnabled = false
            }
            TimerState.Paused -> {
                val fabStart = findViewById<FloatingActionButton>(R.id.fabStart)
                val fabPause = findViewById<FloatingActionButton>(R.id.fabPause)
                val fabStop = findViewById<FloatingActionButton>(R.id.fabStop)
                fabStart.isEnabled = true
                fabPause.isEnabled = false
                fabStop.isEnabled = true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }



    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}