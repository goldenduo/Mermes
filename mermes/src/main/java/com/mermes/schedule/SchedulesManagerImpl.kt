package com.mermes.schedule

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mermes.common.log.MermesLog as Log
import com.mermes.utils.AgentRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SchedulesManagerImpl(private val context: Context) : SchedulesManager {
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("mermes_schedules", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SchedulesManagerImpl"
        private const val KEY_JOBS = "schedules_json"
    }

    init {
        // Initialize with default jobs if empty
        if (!prefs.contains(KEY_JOBS)) {
            val defaults = listOf(
                ScheduleJob(
                    id = "job_weather",
                    cronExpression = "0 8 * * *",
                    platform = "Feishu",
                    prompt = "", // Loaded dynamically via string resources
                    isActive = true,
                    lastRunTime = System.currentTimeMillis() - 86400000L,
                    nextRunTime = System.currentTimeMillis() + 36000000L,
                    lastError = null
                ),
                ScheduleJob(
                    id = "job_github",
                    cronExpression = "*/30 * * * *",
                    platform = "Telegram",
                    prompt = "", // Loaded dynamically via string resources
                    isActive = false,
                    lastRunTime = 0,
                    nextRunTime = System.currentTimeMillis() + 1800000L,
                    lastError = "Connection timed out"
                )
            )
            prefs.edit().putString(KEY_JOBS, gson.toJson(defaults)).apply()
        }
    }

    private fun getLocalJobs(): MutableList<ScheduleJob> {
        val json = prefs.getString(KEY_JOBS, null) ?: return mutableListOf()
        val type = object : TypeToken<List<ScheduleJob>>() {}.type
        val rawJobs: List<ScheduleJob> = gson.fromJson(json, type) ?: return mutableListOf()
        
        // Dynamically load localized prompts and errors on demand to support language hot swapping
        return rawJobs.map { job ->
            val localizedPrompt = when (job.id) {
                "job_weather" -> context.getString(com.mermes.R.string.job_weather_desc)
                "job_github" -> context.getString(com.mermes.R.string.job_github_desc)
                else -> job.prompt
            }
            val localizedError = job.lastError?.let { err ->
                if (err.contains("Connection timed out", ignoreCase = true) || err.contains("Timeout", ignoreCase = true)) {
                    context.getString(com.mermes.R.string.err_connection_timeout)
                } else {
                    err
                }
            }
            job.copy(prompt = localizedPrompt, lastError = localizedError)
        }.toMutableList()
    }

    private fun saveLocalJobs(jobs: List<ScheduleJob>) {
        prefs.edit().putString(KEY_JOBS, gson.toJson(jobs)).apply()
    }

    override suspend fun getSchedules(): List<ScheduleJob> = withContext(Dispatchers.IO) {
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("schedule", "list", "--json"))
        if (result.exitCode == 0) {
            try {
                val type = object : TypeToken<Map<String, List<ScheduleJob>>>() {}.type
                val response: Map<String, List<ScheduleJob>> = gson.fromJson(result.output.trim(), type)
                return@withContext response["schedules"] ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CLI schedules output: ${result.output}", e)
            }
        }
        // Fallback to mock storage
        return@withContext getLocalJobs()
    }

    override suspend fun toggleSchedule(id: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        val stateStr = if (active) "active" else "paused"
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("schedule", "toggle", id, "--state", stateStr))
        if (result.exitCode == 0) {
            return@withContext true
        }
        // Fallback mock update
        val jobs = getLocalJobs()
        val index = jobs.indexOfFirst { it.id == id }
        if (index >= 0) {
            jobs[index] = jobs[index].copy(
                isActive = active,
                nextRunTime = if (active) System.currentTimeMillis() + 3600000L else 0
            )
            saveLocalJobs(jobs)
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun triggerSchedule(id: String): Boolean = withContext(Dispatchers.IO) {
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("schedule", "trigger", id))
        if (result.exitCode == 0) {
            return@withContext true
        }
        // Fallback mock update
        val jobs = getLocalJobs()
        val index = jobs.indexOfFirst { it.id == id }
        if (index >= 0) {
            jobs[index] = jobs[index].copy(
                lastRunTime = System.currentTimeMillis(),
                lastError = null
            )
            saveLocalJobs(jobs)
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun createSchedule(
        cronExpression: String,
        platform: String,
        prompt: String
    ): ScheduleJob? = withContext(Dispatchers.IO) {
        val result = AgentRunner.executeLocalCommand(
            context, "hermes",
            arrayOf("schedule", "create", "--cron", cronExpression, "--platform", platform, "--prompt", prompt)
        )
        if (result.exitCode == 0) {
            try {
                val response = gson.fromJson(result.output.trim(), Map::class.java)
                val newId = response["id"] as? String ?: UUID.randomUUID().toString()
                return@withContext ScheduleJob(
                    id = newId,
                    cronExpression = cronExpression,
                    platform = platform,
                    prompt = prompt,
                    isActive = true,
                    lastRunTime = 0,
                    nextRunTime = System.currentTimeMillis() + 60000L
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse create job output", e)
            }
        }
        // Fallback mock creation
        val newJob = ScheduleJob(
            id = "job_" + UUID.randomUUID().toString().substring(0, 8),
            cronExpression = cronExpression,
            platform = platform,
            prompt = prompt,
            isActive = true,
            lastRunTime = 0,
            nextRunTime = System.currentTimeMillis() + 600000L
        )
        val jobs = getLocalJobs()
        jobs.add(newJob)
        saveLocalJobs(jobs)
        return@withContext newJob
    }

    override suspend fun deleteSchedule(id: String): Boolean = withContext(Dispatchers.IO) {
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("schedule", "delete", id))
        if (result.exitCode == 0) {
            return@withContext true
        }
        // Fallback mock deletion
        val jobs = getLocalJobs()
        val deleted = jobs.removeAll { it.id == id }
        saveLocalJobs(jobs)
        return@withContext deleted
    }
}
