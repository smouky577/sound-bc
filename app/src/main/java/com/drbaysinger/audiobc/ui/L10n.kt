package com.drbaysinger.audiobc.ui

class L10n(val bulgarian: Boolean) {
    private fun t(bg: String, en: String) = if (bulgarian) bg else en

    // Main
    val record = t("Запис", "Record")
    fun stop(time: String, shots: Int) = t("■ Стоп · $time · $shots изстр.", "■ Stop · $time · $shots shots")
    val pick = t("Избери файлове", "Pick files")
    val v0Unit = "m/s"
    val sharedV0 = t("Еднаква V0 за всички", "Same V0 for every file")
    val ht = "HT3006"
    val tapToImport = t("Докосни за връзка", "Tap to connect")
    val importing = t("Свързване…", "Connecting…")
    fun imported(n: Int) = t("$n изстр. импортирани", "$n shots imported")
    val tapToDisconnect = t("Докосни за прекъсване", "Tap to disconnect")
    val range = t("До гонга (m)", "Range to gong (m)")
    val saved = t("запазено", "saved")
    val slug = t("Куршум (по избор)", "Slug (optional)")
    val caliberWeight = t("калибър mm · тегло gr", "caliber mm · weight gr")
    val conditions = t("Условия", "Conditions")
    val edit = t("Редакция", "Edit")
    val done = t("Готово", "Done")
    val useWeather = t("Текущо време", "Current weather")
    val weatherBusy = t("Вземане на времето…", "Getting weather…")
    val temperature = t("Температура (°C)", "Temperature (°C)")
    val pressure = t("Налягане (hPa)", "Pressure (hPa)")
    val humidity = t("Влажност (%)", "Humidity (%)")
    val microphone = t("Дуло → микрофон (m)", "Muzzle → mic distance (m)")
    val threshold = t("Праг k (празно = Auto)", "Threshold k (empty = Auto)")
    val auto = "Auto"
    val legendBaro = t("Барометър", "Barometer")
    val legendMeteo = "Open-Meteo"
    val legendManual = t("Ръчно / запазено", "Manual / saved")
    val manual = t("ръчно", "manual")
    val waveform = t("Вълна", "Waveform")
    val spectrogram = t("Спектрограма", "Spectrogram")
    val analyze = t("Анализирай", "Analyze")
    val noPreview = t("Запиши или избери файлове", "Record or pick files")
    val live = t("запис на живо", "live")

    // Results
    val results = t("Резултати", "Results")
    val history = t("История", "History")
    val sdShort = "SD"
    val dragHint = t("плъзни маркерите", "drag markers to fix")
    val tapGong = t("докосни за GONG", "tap to place GONG")
    val tapShot = t("докосни за SHOT", "tap to place SHOT")
    val showSpectrogram = t("Спектрограма", "Show spectrogram")
    val showWaveform = t("Вълна", "Show waveform")
    val shot = t("Изстрел", "Shot")
    val outOfRange = t("извън обхват", "out of range")
    val unitMs = t("м/с", "m/s")
    val unitM = t("м", "m")
    val distanceHint = t("Разстояние", "Range")
    val soundLabel = t("звук", "sound")
    val densityLabel = t("плътност", "density")
    val unitMm = t("мм", "mm")
    val unitGr = t("гр", "g")
    val caliberHint = t("калибър", "caliber")
    val slugHint = t("слъг", "slug")
    val slugTitle = t("Слъг", "Slug")
    val shotCol = t("№", "Shot")
    val dt = "Δt (s)"
    val noGongFix = t("няма гонг · поправи", "no gong · fix")
    val setV0 = t("въведи V0", "set V0")
    val exportExcel = t("Експорт Excel", "Export Excel")
    val exported = t("Excel файлът е записан.", "Excel file saved.")
    val historyEmpty = t("Няма запазени сесии.", "No saved sessions yet.")
    val delete = t("Изтрий", "Delete")
    val deleteConfirm = t("Да изтрия ли тази сесия?", "Delete this session?")
    val cancel = t("Отказ", "Cancel")
    val ok = "OK"
    val editV0 = t("V0 за изстрел", "V0 for shot")
    val energyMuzzle = t("E дуло", "E muzzle")
    val energyGong = t("E гонг", "E gong")
    val sectional = t("Сеч. плътн.", "Sect. dens.")
    val formFactor = t("Форм-фактор i", "Form factor i")
    val cdMuzzle = "Cd"
    val loading = t("Зареждане…", "Loading…")
    fun sessionLine(shots: Int, valid: Int, d: String) =
        t("$valid/$shots изстр. · $d m", "$valid/$shots shots · $d m")

    // Messages
    val needInput = t("Запиши сесия или избери файлове.", "Record a session or pick files first.")
    val noShotsFound = t("Не са открити изстрели.", "No shots were found.")
    val micFailed = t("Микрофонът не може да се стартира.", "Could not start the microphone.")
    val micDenied = t("Няма разрешение за микрофона.", "Microphone permission denied.")
    val chronoNoShots = t("HT3006 е свързан, но няма изстрели.", "HT3006 connected but has no shots.")
    val chronoNotFound = t(
        "HT3006 не е намерен. Включи Wi‑Fi на хронографа (мрежа HT-X3000).",
        "HT3006 not found. Turn on the chronograph Wi‑Fi (network HT-X3000).",
    )
    val weatherFailed = t("Няма барометър и няма връзка с Open-Meteo.", "No barometer and Open-Meteo is unreachable.")
    val noLocation = t("без GPS", "no GPS")
    val weatherPartial = t("Само налягане от барометъра (няма местоположение/интернет).", "Barometer pressure only (no location / internet).")
    val stopping = t("Спиране на записа…", "Stopping recording…")
    fun shotOf(i: Int, n: Int) = t("Изстрел $i от $n", "Shot $i of $n")
    fun fileOf(index: Int, total: Int, name: String) = t("Файл $index от $total: $name", "File $index of $total: $name")
    val checkValues = t("Провери въведените стойности.", "Check the entered values.")
    val needV0 = t("Въведи V0 или импортирай от HT3006.", "Enter V0 or import it from HT3006.")
    val noGong = t("няма гонг", "no gong")
    val status = t("статус", "status")
    val file = t("Файл", "File")
    val summary = t("Обобщение", "Summary")
    val mean = t("Средно", "Mean")
    val dash = "—"
}

fun formatNumber(value: Double, digits: Int, bulgarian: Boolean): String {
    val text = String.format(java.util.Locale.US, "%.${digits}f", value)
    return if (bulgarian) text.replace('.', ',') else text
}
