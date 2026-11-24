package com.rail.coaahead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class TrainInfo(
    val direction: String, // "UP" or "DN"
    val trainName: String,
    val loco: String,
    val station: String,
    val status: String,
    val time: String,
    val remarks: String
)

data class CoaSheetData(
    val lastUpdated: String,
    val upTrains: List<TrainInfo>,
    val dnTrains: List<TrainInfo>
)

object CoaRepository {

    val UP_STATIONS = listOf(
        "NDL", "PNM", "KEF", "BEY", "BMH", "RGM", "MLK", "DHNE",
        "MYL", "PDL", "EDD", "TGL", "MKR", "MLGT", "GTL", "GTLW"
    )

    val DN_STATIONS = UP_STATIONS.reversed()

    private val STATION_SET = UP_STATIONS.toSet()

    private const val SHEET_CSV_URL =
        "https://docs.google.com/spreadsheets/d/12ETNJfbwcmNCY_HfpU6NaqHYD1R22yRmFDsaAaj7zBo/export?format=csv&gid=0"

    suspend fun fetchCoaData(): CoaSheetData = withContext(Dispatchers.IO) {
        val url = URL(SHEET_CSV_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        val csvText = conn.inputStream.bufferedReader().use { it.readText() }
        parseCsv(csvText)
    }

    private fun parseCsv(csvText: String): CoaSheetData {
        val rows = parseCsvRows(csvText)
        var lastUpdated = "Unknown"
        val upList = mutableListOf<TrainInfo>()
        val dnList = mutableListOf<TrainInfo>()

        for (row in rows) {
            if (row.size > 4 && row[3].contains("Last Updated", ignoreCase = true)) {
                val ts = row[4].trim()
                if (ts.isNotEmpty()) lastUpdated = ts
            }

            // UP TRAINS block: Col D(3)=Train Name, E(4)=LOCO, F(5)=Station, G(6)=Status, H(7)=Time, I(8)=Remarks
            if (row.size > 8) {
                val upTrain = row[3].trim()
                val upStation = row[5].trim().uppercase()
                if (upTrain.isNotEmpty() && !upTrain.equals("Train Name", true) && STATION_SET.contains(upStation)) {
                    upList.add(
                        TrainInfo(
                            direction = "UP",
                            trainName = upTrain,
                            loco = row[4].trim().ifEmpty { "—" },
                            station = upStation,
                            status = row[6].trim(),
                            time = row[7].trim(),
                            remarks = row[8].trim()
                        )
                    )
                }
            }

            // DN TRAINS block: Col P(15)=Train Name, Q(16)=LOCO, R(17)=Station, S(18)=Status, T(19)=Time, U(20)=Remarks
            if (row.size > 20) {
                val dnTrain = row[15].trim()
                val dnStation = row[17].trim().uppercase()
                if (dnTrain.isNotEmpty() && !dnTrain.equals("Train Name", true) && STATION_SET.contains(dnStation)) {
                    dnList.add(
                        TrainInfo(
                            direction = "DN",
                            trainName = dnTrain,
                            loco = row[16].trim().ifEmpty { "—" },
                            station = dnStation,
                            status = row[18].trim(),
                            time = row[19].trim(),
                            remarks = row[20].trim()
                        )
                    )
                }
            }
        }

        return CoaSheetData(lastUpdated, upList, dnList)
    }

    private fun parseCsvRows(input: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < input.length) {
            val c = input[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        result.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\r' -> { /* ignore CR */ }
                    else -> currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            result.add(currentRow.toList())
        }
        return result
    }
}
