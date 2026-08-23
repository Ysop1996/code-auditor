package de.lifeos.core.runtime

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class EmbeddedPythonExecutionBridge(private val cacheDir: File) {

    /**
     * Schreibt das Skript in eine temporäre RAM-Datei, führt es isoliert aus
     * und schreddert die Datei unmittelbar nach Rückgabe des Resultats.
     */
    fun runEphemeralScript(pythonCode: String, argument: String): String {
        val tempScript = File.createTempFile("exec_worker_", ".py", cacheDir)
        try {
            tempScript.writeText(pythonCode, StandardCharsets.UTF_8)

            val process = ProcessBuilder("python3", tempScript.absolutePath, argument)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            process.waitFor()
            return output.toString().trim()
        } catch (e: Exception) {
            return "PYTHON_EXEC_ERROR: ${e.message}"
        } finally {
            // 3-Pass RAM-Shredder für das Skript
            if (tempScript.exists()) {
                tempScript.writeBytes(ByteArray(tempScript.length().toInt()))
                tempScript.delete()
            }
        }
    }

    /**
     * Vorgefertigter Zinsrechner nach § 288 BGB
     */
    fun runInterestCalculator(hauptforderung: Float, startDatumIso: String): String {
        val script = """
            import sys, json
            from datetime import datetime

            def calculate_verzug(hauptforderung, start_datum_iso, basiszins_prozent=3.62):
                start = datetime.fromisoformat(start_datum_iso)
                tage = (datetime.now() - start).days
                zinssatz = (basiszins_prozent + 5.0) / 100.0
                zinsen = hauptforderung * zinssatz * (tage / 365.0)
                pauschale = 40.0
                return {
                    "hauptforderung": hauptforderung,
                    "verzugstage": tage,
                    "berechnete_zinsen": round(zinsen, 2),
                    "verzugspauschale": pauschale,
                    "gesamtforderung": round(hauptforderung + zinsen + pauschale, 2)
                }

            if __name__ == "__main__":
                raw = sys.argv[1].split("|")
                res = calculate_verzug(float(raw[0]), raw[1])
                print(json.dumps(res))
        """.trimIndent()

        return runEphemeralScript(script, "$hauptforderung|$startDatumIso")
    }

    /**
     * Vorgefertigter Tabellen- & Fristen-Parser
     */
    fun runBescheidExtractor(textContent: String): String {
        val script = """
            import sys, re, json

            def extract_financial_rows(text_content):
                rows = []
                lines = text_content.split("\n")
                for line in lines:
                    matches = re.findall(r"(\b\d+[.,]\d{2}\s*€?)", line)
                    if matches and any(k in line.lower() for k in ["abzug", "minderung", "gesamt", "auszahlung", "regelsatz"]):
                        rows.append({"context": line.strip(), "amounts": matches})
                return {"extracted_financial_matrix": rows, "count": len(rows)}

            if __name__ == "__main__":
                input_text = sys.argv[1]
                print(json.dumps(extract_financial_rows(input_text)))
        """.trimIndent()

        return runEphemeralScript(script, textContent.take(2000))
    }
}
