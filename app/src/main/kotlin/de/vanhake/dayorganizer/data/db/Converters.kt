package de.vanhake.dayorganizer.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun datumAusText(text: String?): LocalDate? = text?.let(LocalDate::parse)
    @TypeConverter fun datumZuText(datum: LocalDate?): String? = datum?.toString()
    @TypeConverter fun zeitpunktAusText(text: String?): LocalDateTime? = text?.let(LocalDateTime::parse)
    @TypeConverter fun zeitpunktZuText(zeitpunkt: LocalDateTime?): String? = zeitpunkt?.toString()
}
