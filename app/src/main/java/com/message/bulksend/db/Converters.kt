package com.message.bulksend.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.data.ContactStatus

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromContactStatusList(contactStatuses: List<ContactStatus>?): String? {
        return gson.toJson(contactStatuses)
    }

    @TypeConverter
    fun toContactStatusList(contactStatusesString: String?): List<ContactStatus>? {
        if (contactStatusesString == null) return null
        val type = object : TypeToken<List<ContactStatus>>() {}.type
        return gson.fromJson(contactStatusesString, type)
    }

    @TypeConverter
    fun fromContactEntityList(contacts: List<ContactEntity>?): String? {
        return gson.toJson(contacts)
    }

    @TypeConverter
    fun toContactEntityList(contactsString: String?): List<ContactEntity>? {
        if (contactsString == null) return null
        val type = object : TypeToken<List<ContactEntity>>() {}.type
        return gson.fromJson(contactsString, type)
    }
}
