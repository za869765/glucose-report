package com.za869765.glucose

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {
    private lateinit var etName: EditText
    private lateinit var etGlucose: EditText
    private lateinit var tvTime: TextView
    private lateinit var tvStrip: TextView
    private lateinit var ivPreview: ImageView

    private var time: Calendar = Calendar.getInstance()
    private var timeAdjusted = false
    private lateinit var strip: StripInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etName = findViewById(R.id.etName)
        etGlucose = findViewById(R.id.etGlucose)
        tvTime = findViewById(R.id.tvTime)
        tvStrip = findViewById(R.id.tvStrip)
        ivPreview = findViewById(R.id.ivPreview)
        strip = StripInfo.load(this)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        }
        etName.addTextChangedListener(watcher)
        etGlucose.addTextChangedListener(watcher)
        tvTime.setOnClickListener { pickTime() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener { editStrip() }
        findViewById<TextView>(R.id.btnSend).setOnClickListener { send() }
        findViewById<TextView>(R.id.tvSub).text = "指尖血糖報告  v${Updater.versionName(this)}"
        refresh()
        handleInstallIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallIntent(intent)
    }

    private fun handleInstallIntent(intent: Intent?) {
        if (intent?.action == Updater.ACTION_INSTALL_STATUS) Updater.onInstallStatus(this, intent)
    }

    override fun onResume() {
        super.onResume()
        // 沒手動調過時間就跟著現在時間
        if (!timeAdjusted) {
            time = Calendar.getInstance()
            refresh()
        }
        Updater.check(this)
    }

    private fun rocTime(c: Calendar) = "%d/%02d/%02d %02d:%02d".format(
        c.get(Calendar.YEAR) - 1911, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
    )

    private fun data() = ReportData(
        name = etName.text.toString().trim(),
        glucose = etGlucose.text.toString().trim().ifEmpty { "—" },
        time = rocTime(time),
        strip = strip,
    )

    private fun refresh() {
        tvTime.text = rocTime(time)
        etGlucose.setTextColor(ReportRenderer.valueColor(etGlucose.text.toString()))
        tvStrip.text = "試片：${strip.brand}\n批號 ${strip.lot}／效期 ${strip.expiry}"
        ivPreview.setImageBitmap(ReportRenderer.render(data()))
    }

    private fun pickTime() {
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, hh, mm ->
                time = Calendar.getInstance().apply { set(y, m, d, hh, mm, 0) }
                timeAdjusted = true
                refresh()
            }, time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), true).show()
        }, time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun editStrip() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        fun field(label: String, value: String) = EditText(this).also {
            it.hint = label
            it.setText(value)
            it.inputType = InputType.TYPE_CLASS_TEXT
            box.addView(TextView(this).apply { text = label })
            box.addView(it)
        }
        val fBrand = field("試片廠牌", strip.brand)
        val fLot = field("製造批號", strip.lot)
        val fExp = field("保存期限（民國，例 117/08/02）", strip.expiry)
        val fLic = field("許可證號", strip.license)
        AlertDialog.Builder(this)
            .setTitle("試片設定")
            .setView(box)
            .setPositiveButton("儲存") { _, _ ->
                strip = StripInfo(
                    fBrand.text.toString().trim(), fLot.text.toString().trim(),
                    fExp.text.toString().trim(), fLic.text.toString().trim(),
                )
                strip.save(this)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun send() {
        val d = data()
        if (d.name.isEmpty()) { etName.error = "請輸入姓名"; etName.requestFocus(); return }
        if (etGlucose.text.isBlank()) { etGlucose.error = "請輸入血糖值"; etGlucose.requestFocus(); return }

        val uri = saveToGallery(ReportRenderer.render(d), d)
        if (uri == null) {
            Toast.makeText(this, "存檔失敗", Toast.LENGTH_LONG).show()
            return
        }
        val msg = "【臺南市佳里區衛生所 檢驗報告】\n姓名：${d.name}\n檢驗時間：${d.time}\n" +
            "指尖血糖：${d.glucose} mg/dL（參考值 70-100 mg/dL）"
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, msg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent(share).setPackage("jp.naver.line.android"))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent.createChooser(share, "傳送報告"))
        }
        Toast.makeText(this, "已存入相簿「佳里血糖」", Toast.LENGTH_SHORT).show()

        // 下一位重新輸入
        etName.text.clear()
        etGlucose.text.clear()
        etName.error = null
        etGlucose.error = null
        timeAdjusted = false
        time = Calendar.getInstance()
        refresh()
    }

    private fun saveToGallery(bmp: Bitmap, d: ReportData): Uri? {
        val stamp = d.time.replace("/", "").replace(" ", "_").replace(":", "")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "血糖_${d.name}_$stamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/佳里血糖")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }
}
