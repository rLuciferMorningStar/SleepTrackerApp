/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob = Job()

    // For cancelling coroutines
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    // Defining scope for coroutines to run in
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    // Using coroutine to initialize a live data variable from the database
    private var tonight = MutableLiveData<SleepNight?>()

    // Getting all nights from database
    private val nights = database.getAllNights()

    // To display buttons when needed
    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }

    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }

    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    private var _showSnackBarEvent = MutableLiveData<Boolean>()
    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackBarEvent

    fun doneShowingSnackBar() {
        _showSnackBarEvent.value = false
    }

    // To navigate to Sleep Quality Fragment
    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
    // Public reference for above variable
    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    // To reset navigation variable
    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    // Format nights data for display
    val nightsString = Transformations.map(nights) {nights ->
        formatNights(nights, application.resources)
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli){
                night = null
            }
            night
        }
    }

    // Local functions for insert, update, clear
    fun onStartTracking() {
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun insert(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(night)
        }
    }

    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }

    fun onClear() {
        uiScope.launch {
            // Clear database table
            clear()
            // Clear tonight since it is no longer in database
            tonight.value = null

            _showSnackBarEvent.value = true
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }


    // Handlers for Start, Stop and Clear buttons using coroutines to do the database work
}
