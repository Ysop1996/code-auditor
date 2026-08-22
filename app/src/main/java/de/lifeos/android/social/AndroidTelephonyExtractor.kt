package de.lifeos.android.social

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import net.sqlcipher.database.SQLiteDatabase

class AndroidTelephonyExtractor(private val context: Context, private val vaultDb: SQLiteDatabase) {

    fun extractContacts() {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val contactId = it.getString(idIdx) ?: continue
                val name = it.getString(nameIdx) ?: "Unbekannt"
                val number = it.getString(numIdx)?.replace("\\s+".toRegex(), "") ?: ""

                vaultDb.execSQL(
                    "INSERT OR REPLACE INTO contacts (contact_id, display_name, primary_phone) VALUES (?, ?, ?)",
                    arrayOf(contactId, name, number)
                )
            }
        }
    }

    fun extractCallLogs(limit: Int = 200) {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            null, null, "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )

        cursor?.use {
            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

            while (it.moveToNext()) {
                val number = it.getString(numIdx)?.replace("\\s+".toRegex(), "") ?: "UNKNOWN"
                val epoch = it.getLong(dateIdx)
                val duration = it.getInt(durIdx)
                val type = it.getInt(typeIdx)

                val direction = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "INBOUND"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTBOUND"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    else -> "OTHER"
                }

                val eventId = "CALL_${epoch}_$number"
                val isUnresolved = if (direction == "MISSED") 1 else 0

                vaultDb.execSQL(
                    "INSERT OR IGNORE INTO communication_events VALUES (?, ?, 'CALL', ?, ?, ?, '', ?)",
                    arrayOf(eventId, number, direction, epoch, duration, isUnresolved)
                )
            }
        }
    }

    fun extractSms(limit: Int = 500) {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE,
                Telephony.Sms.BODY,
                Telephony.Sms.TYPE
            ),
            null, null, "${Telephony.Sms.DATE} DESC LIMIT $limit"
        )

        cursor?.use {
            val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val address = it.getString(addrIdx) ?: "UNKNOWN"
                val epoch = it.getLong(dateIdx)
                val body = it.getString(bodyIdx) ?: ""
                val type = it.getInt(typeIdx)
                val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "INBOUND" else "OUTBOUND"
                val eventId = "SMS_${epoch}_${address.hashCode()}"

                vaultDb.execSQL(
                    "INSERT OR IGNORE INTO communication_events VALUES (?, ?, 'SMS', ?, ?, 0, ?, 0)",
                    arrayOf(eventId, address, direction, epoch, body.take(500))
                )
            }
        }
    }
}
