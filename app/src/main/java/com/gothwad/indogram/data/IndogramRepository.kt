package com.gothwad.indogram.data

import kotlinx.coroutines.flow.Flow

class IndogramRepository(private val indogramDao: IndogramDao) {

    val allOfflineDrafts: Flow<List<OfflineDraft>> = indogramDao.getAllOfflineDrafts()
    val allNotifications: Flow<List<NotificationItem>> = indogramDao.getAllNotifications()

    suspend fun saveOfflineDraft(content: String, recipient: String = "General") {
        indogramDao.insertOfflineDraft(OfflineDraft(content = content, recipient = recipient))
    }

    suspend fun deleteOfflineDraft(draft: OfflineDraft) {
        indogramDao.deleteOfflineDraft(draft)
    }

    suspend fun clearAllDrafts() {
        indogramDao.clearAllDrafts()
    }

    suspend fun saveNotification(title: String, message: String) {
        indogramDao.insertNotification(NotificationItem(title = title, message = message))
    }

    suspend fun markNotificationAsRead(id: Int) {
        indogramDao.markNotificationAsRead(id)
    }

    suspend fun deleteNotification(id: Int) {
        indogramDao.deleteNotification(id)
    }

    suspend fun clearAllNotifications() {
        indogramDao.clearAllNotifications()
    }
}
