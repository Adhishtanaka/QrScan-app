package Adhishtanaka.qrscan

import QRCodeAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.regex.Pattern

class HistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: QRCodeDatabaseHelper
    private lateinit var database: SQLiteDatabase
    private lateinit var qrCodeDataList: MutableList<QRCodeData>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: QRCodeAdapter
    private lateinit var searchView: SearchView
    private lateinit var chipGroup: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)


        dbHelper = QRCodeDatabaseHelper(this)
        database = dbHelper.writableDatabase
        qrCodeDataList = mutableListOf()

        adapter = QRCodeAdapter(qrCodeDataList) { qrCodeData ->
            handleItemClick(qrCodeData)
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchView = findViewById(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filter(newText.toString())
                return true
            }
        })

        chipGroup = findViewById(R.id.chipGroup)

        val categoryOptions =
            listOf("All", "Text", "Website", "WiFi")


        for (category in categoryOptions) {
            val chip = Chip(this)
            chip.text = category
            chip.isCheckable = true
            chipGroup.addView(chip)


            chip.setOnClickListener {
                handleChipClick(chip)
            }
        }

        loadQRCodeData()
    }

    private fun loadQRCodeData() {
        val cursor = database.query(
            QRCodeDatabaseHelper.TABLE_QR,
            arrayOf(
                QRCodeDatabaseHelper.KEY_ID,
                QRCodeDatabaseHelper.KEY_Details,
                QRCodeDatabaseHelper.KEY_Tag,
                QRCodeDatabaseHelper.KEY_DateTime
            ),
            null,
            null,
            null,
            null,
            null
        )

        qrCodeDataList.clear()

        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(QRCodeDatabaseHelper.KEY_ID))
            val details = cursor.getString(cursor.getColumnIndexOrThrow(QRCodeDatabaseHelper.KEY_Details))
            val tag = cursor.getString(cursor.getColumnIndexOrThrow(QRCodeDatabaseHelper.KEY_Tag))
            val datetime = cursor.getString(cursor.getColumnIndexOrThrow(QRCodeDatabaseHelper.KEY_DateTime))

            if (tag.equals("WiFi", ignoreCase = true)) {
                val ssidPattern = Pattern.compile("S:([^;]+);")
                val passwordPattern = Pattern.compile("P:([^;]+);")
                val ssidMatcher = ssidPattern.matcher(details)
                val passwordMatcher = passwordPattern.matcher(details)

                val ssid: String
                val password: String

                if (ssidMatcher.find() && passwordMatcher.find()) {
                    ssid = ssidMatcher.group(1)
                    password = passwordMatcher.group(1)
                    val newdetail = "$ssid : $password"
                    val qrCodeData = QRCodeData(id, newdetail, tag, datetime)
                    qrCodeDataList.add(qrCodeData)
                }
            } else {

                val qrCodeData = QRCodeData(id, details, tag, datetime)
                qrCodeDataList.add(qrCodeData)
            }
        }

        qrCodeDataList.reverse()
        adapter.notifyDataSetChanged()
        cursor.close()

        if (qrCodeDataList.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("QrScan")
                .setMessage("No QR Code history found")
                .setNegativeButton("Ok") { dialog, which ->
            }
                .show()
        }


    }


    private fun filter(text: String) {
        val filteredList = ArrayList<QRCodeData>()
        for (qrCodeData in qrCodeDataList) {
            if (qrCodeData.details.toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(qrCodeData)
            }
        }

        adapter.filterList(filteredList)

        for (i in 0 until chipGroup.childCount) {
            val child = chipGroup.getChildAt(i)
            if (child is Chip) {
                if (child.text == "All") {
                    child.isChecked = true
                } else {
                    child.isChecked = false
                }
            }
        }
    }


    private fun handleChipClick(chip: Chip) {

        for (i in 0 until chipGroup.childCount) {
            val child = chipGroup.getChildAt(i)
            if (child is Chip) {
                child.isChecked = false
            }
        }


        chip.isChecked = true

        val selectedCategory = chip.text.toString()
        if (selectedCategory == "All") {
            adapter.filterList(qrCodeDataList)
        } else {
            val filteredList =
                qrCodeDataList.filter { it.tag.equals(selectedCategory, ignoreCase = true) }
            adapter.filterList(filteredList)
        }
    }

    private fun handleItemClick(qrCodeData: QRCodeData) {
        when (qrCodeData.tag) {
            "Website" -> {
                webdialog(qrCodeData)

            }

            "WiFi" -> {
                wifidialog(qrCodeData)
            }

            else -> {
                textdialog(qrCodeData)

            }
        }
    }

    private fun wifidialog(qrCodeData: QRCodeData) {
        val ssidPattern = Pattern.compile("([^:]+) :")
        val passwordPattern = Pattern.compile(": ([^\\s]+)")

        val ssidMatcher = ssidPattern.matcher(qrCodeData.details)
        val passwordMatcher = passwordPattern.matcher(qrCodeData.details)

        val ssid: String
        val password: String

        if (ssidMatcher.find() && passwordMatcher.find()) {
            ssid = ssidMatcher.group(1)
            password = passwordMatcher.group(1)
        } else {
            ssid = "SSID Not Found"
            password = "Password Not Found"
        }

        val options = arrayOf("Copy Password", "Share", "Delete", "Cancel")

        MaterialAlertDialogBuilder(this)
            .setTitle(ssid)
            .setItems(options) { dialog, which ->
                val selectedOption = options[which]
                when (selectedOption) {

                    "Copy Password" -> {
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Password", password)
                        clipboard.setPrimaryClip(clip)

                    }


                    "Share" -> {

                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "text/plain"
                        shareIntent.putExtra(Intent.EXTRA_TEXT, "Wi-Fi SSID: $ssid\nPassword: $password")


                        startActivity(Intent.createChooser(shareIntent, "Share Wi-Fi Credentials"))
                    }

                    "Delete" -> {
                        val deleted = dbHelper.deleteQRCodeEntry(qrCodeData.id)

                        if (deleted) {
                            qrCodeDataList.remove(qrCodeData)
                            adapter.notifyDataSetChanged()
                        }
                    }

                    "Cancel" -> {
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }


    private fun textdialog(qrCodeData: QRCodeData) {
        val options = arrayOf("Copy", "Share", "Delete", "Cancel")

        MaterialAlertDialogBuilder(this)
            .setTitle(qrCodeData.details)
            .setItems(options) { dialog, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "Copy" -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Text", qrCodeData.details)
                        clipboard.setPrimaryClip(clip)

                    }


                    "Share" -> {

                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "text/plain"
                        shareIntent.putExtra(Intent.EXTRA_TEXT, qrCodeData.details)

                        startActivity(Intent.createChooser(shareIntent, "Share Text"))
                    }

                    "Delete" -> {
                        val deleted = dbHelper.deleteQRCodeEntry(qrCodeData.id)

                        if (deleted) {
                            qrCodeDataList.remove(qrCodeData)
                            adapter.notifyDataSetChanged()
                        }
                    }

                    "Cancel" -> {
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }


    private fun webdialog(qrCodeData: QRCodeData) {
        val options = arrayOf("Open", "Copy", "Share", "Delete", "Cancel")

        MaterialAlertDialogBuilder(this)
            .setTitle(qrCodeData.details)
            .setItems(options) { dialog, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "Open" -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qrCodeData.details))
                        startActivity(intent)
                    }

                    "Copy" -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Text", qrCodeData.details)
                        clipboard.setPrimaryClip(clip)

                }


                    "Share" -> {

                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "text/plain"
                        shareIntent.putExtra(Intent.EXTRA_TEXT, qrCodeData.details)

                        startActivity(Intent.createChooser(shareIntent, "Share URL"))
                    }

                    "Delete" -> {
                        val deleted = dbHelper.deleteQRCodeEntry(qrCodeData.id)

                        if (deleted) {
                            qrCodeDataList.remove(qrCodeData)
                            adapter.notifyDataSetChanged()
                        }
                    }

                    "Cancel" -> {
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }
}



