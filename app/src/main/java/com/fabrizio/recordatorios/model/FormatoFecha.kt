package com.fabrizio.recordatorios.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Las fechas límite se manejan como epoch millis (Long) a medianoche UTC.
// Long se guarda directo en Room y se puede ordenar en SQL; no necesita java.time (API 26+).
object FormatoFecha {

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    fun formatear(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .apply { timeZone = UTC }
            .format(millis)

    // mes de 1 a 12
    fun fechaUtc(anio: Int, mes: Int, dia: Int): Long =
        Calendar.getInstance(UTC).run {
            clear()
            set(anio, mes - 1, dia)
            timeInMillis
        }
}
