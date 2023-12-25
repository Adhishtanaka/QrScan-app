package Adhishtanaka.qrscan

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class QRCodeDatabaseHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "QRDatabase"
        const val TABLE_QR = "QRTable"
        const val KEY_ID = "id"
        const val KEY_Details = "details"
        const val KEY_Tag = "tag"
        const val KEY_DateTime = "datetime"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_QR_TABLE = ("CREATE TABLE $TABLE_QR (" +
                "$KEY_ID INTEGER PRIMARY KEY," +
                "$KEY_Details TEXT," +
                "$KEY_Tag TEXT," +
                "$KEY_DateTime TEXT" +
                ")")
        db?.execSQL(CREATE_QR_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_QR")
        onCreate(db)
    }

    fun deleteQRCodeEntry(qrCodeId: Long): Boolean {
        val db = this.writableDatabase
        return db.delete(TABLE_QR, "$KEY_ID = ?", arrayOf(qrCodeId.toString())) > 0
    }
}
