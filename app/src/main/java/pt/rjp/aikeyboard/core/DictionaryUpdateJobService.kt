package pt.rjp.aikeyboard.core

import android.app.job.JobParameters
import android.app.job.JobService

class DictionaryUpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (!DictionaryUpdateManager.isAutoUpdateEnabled(this)) {
            jobFinished(params, false)
            return false
        }
        DictionaryUpdateManager.updateNowAsync(this) {
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
