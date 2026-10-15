package com.za869765.glucose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

data class ReportData(
    val name: String,
    val glucose: String,
    val time: String,
    val strip: StripInfo,
)

/** 把報告畫成 1080px 寬的 PNG 圖，預覽與存檔共用同一張。 */
object ReportRenderer {
    private const val W = 1080
    private const val PAD = 56f

    private val NAVY = Color.parseColor("#1F3A5F")
    private val NAVY_LIGHT = Color.parseColor("#C9D6E8")
    private val GOLD = Color.parseColor("#B08D3C")
    private val TINT = Color.parseColor("#E3EAF3")
    private val GRID = Color.parseColor("#B8C6D8")
    private val INK = Color.parseColor("#1A2433")
    private val MUTED = Color.parseColor("#5A6B82")
    private val FOOT_BG = Color.parseColor("#F4F6F9")
    private val FOOT_LINE = Color.parseColor("#D5DDE8")
    private val LOW = Color.parseColor("#1565C0")
    private val HIGH = Color.parseColor("#C62828")
    private val LOW_BG = Color.parseColor("#E3F0FB")
    private val HIGH_BG = Color.parseColor("#FCE8E8")

    /** 全形數字也轉成數值。 */
    private fun parse(glucose: String): Int? =
        glucose.filter { it.isDigit() }.map { Character.getNumericValue(it) }.joinToString("").toIntOrNull()

    /** 低於 70 藍、高於 100 紅、正常黑。 */
    fun valueColor(glucose: String): Int {
        val v = parse(glucose) ?: return INK
        return when {
            v < 70 -> LOW
            v > 100 -> HIGH
            else -> INK
        }
    }

    private fun valueBg(glucose: String): Int? {
        val v = parse(glucose) ?: return null
        return when {
            v < 70 -> LOW_BG
            v > 100 -> HIGH_BG
            else -> null
        }
    }

    private val BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private fun text(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun fill(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

    fun render(d: ReportData): Bitmap {
        val headerH = 230f
        val goldH = 12f
        val labelP = text(40f, MUTED)
        val nameP = text(46f, INK, bold = true)
        val timeP = text(42f, INK)

        // 姓名與檢驗時間同一列，太長時改兩列
        val nameW = labelP.measureText("姓名　") + nameP.measureText(d.name)
        val timeW = labelP.measureText("檢驗時間　") + timeP.measureText(d.time)
        val oneRow = nameW + timeW + 60f <= W - PAD * 2
        val infoH = if (oneRow) 90f else 160f

        val tableHeadH = 92f
        val tableRowH = 140f
        val stripLines = d.strip.lines()
        val stripH = 40f + stripLines.size * 62f
        val footH = 84f
        val h = (headerH + goldH + 40f + infoH + tableHeadH + tableRowH + 30f + stripH + 30f + footH).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        // 抬頭
        c.drawRect(0f, 0f, W.toFloat(), headerH, fill(NAVY))
        c.drawText("臺南市佳里區衛生所", W / 2f, 118f,
            text(66f, Color.WHITE, bold = true, align = Paint.Align.CENTER).apply { letterSpacing = 0.12f })
        c.drawText("檢驗報告", W / 2f, 186f,
            text(40f, NAVY_LIGHT, align = Paint.Align.CENTER).apply { letterSpacing = 0.6f })
        c.drawRect(0f, headerH, W.toFloat(), headerH + goldH, fill(GOLD))

        // 姓名／檢驗時間
        var y = headerH + goldH + 40f
        val base = y + 54f
        c.drawText("姓名　", PAD, base, labelP)
        c.drawText(d.name, PAD + labelP.measureText("姓名　"), base, nameP)
        if (oneRow) {
            val tx = W - PAD - timeW
            c.drawText("檢驗時間　", tx, base, labelP)
            c.drawText(d.time, tx + labelP.measureText("檢驗時間　"), base, timeP)
        } else {
            c.drawText("檢驗時間　", PAD, base + 70f, labelP)
            c.drawText(d.time, PAD + labelP.measureText("檢驗時間　"), base + 70f, timeP)
        }
        y += infoH

        // 表格
        val left = PAD
        val right = W - PAD
        val cols = floatArrayOf(left, left + (right - left) * 0.34f, left + (right - left) * 0.67f, right)
        c.drawRect(left, y, right, y + tableHeadH, fill(TINT))
        val headP = text(40f, NAVY, bold = true, align = Paint.Align.CENTER)
        listOf("檢驗項目", "檢驗值", "參考值").forEachIndexed { i, s ->
            c.drawText(s, (cols[i] + cols[i + 1]) / 2, y + 60f, headP)
        }
        val rowTop = y + tableHeadH
        valueBg(d.glucose)?.let { c.drawRect(cols[1], rowTop, cols[2], rowTop + tableRowH, fill(it)) }
        val cellP = text(42f, INK, align = Paint.Align.CENTER)
        val valueP = text(58f, valueColor(d.glucose), bold = true, align = Paint.Align.CENTER)
        c.drawText("指尖血糖", (cols[0] + cols[1]) / 2, rowTop + 86f, cellP)
        c.drawText("${d.glucose} mg/dL", (cols[1] + cols[2]) / 2, rowTop + 92f, valueP)
        c.drawText("70-100 mg/dL", (cols[2] + cols[3]) / 2, rowTop + 86f, cellP)
        val grid = stroke(GRID, 3f)
        c.drawRect(RectF(left, y, right, rowTop + tableRowH), grid)
        c.drawLine(left, rowTop, right, rowTop, grid)
        for (i in 1..2) c.drawLine(cols[i], y, cols[i], rowTop + tableRowH, grid)
        y = rowTop + tableRowH + 30f

        // 試片資訊
        c.drawLine(left, y, right, y, stroke(GRID, 3f).apply { pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f) })
        val kLabel = text(36f, MUTED)
        val kValue = text(38f, INK)
        stripLines.forEachIndexed { i, (k, v) ->
            val by = y + 70f + i * 62f
            c.drawText(k, left, by, kLabel)
            c.drawText(v, left + 200f, by, kValue)
        }

        // 頁尾
        val footTop = (h - footH)
        c.drawRect(0f, footTop, W.toFloat(), h.toFloat(), fill(FOOT_BG))
        c.drawLine(0f, footTop, W.toFloat(), footTop, stroke(FOOT_LINE, 3f))
        c.drawText("臺南市佳里區衛生所 檢驗報告", W / 2f, footTop + 54f, text(32f, MUTED, align = Paint.Align.CENTER))

        // 外框
        c.drawRect(2f, 2f, W - 2f, h - 2f, stroke(NAVY, 4f))
        return bmp
    }
}
