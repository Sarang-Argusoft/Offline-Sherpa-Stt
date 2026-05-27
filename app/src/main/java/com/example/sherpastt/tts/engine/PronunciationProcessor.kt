package com.example.sherpastt.tts.engine

import java.util.regex.Pattern

interface PronunciationProcessor {
    fun process(text: String): String
}

class MedicalPronunciationProcessor : PronunciationProcessor {

    // A mapping of medical terminology to phonetically simplified terms
    // which eSpeak and Kokoro can pronounce with high accuracy.
    private val dictionary = mapOf(
        "myocardial infarction" to "myo-cardial infarction",
        "cardiomyopathy" to "cardio-myopathy",
        "hydrochlorothiazide" to "hydro-chloro-thia-zide",
        "atorvastatin" to "a-tore-va-statin",
        "acetaminophen" to "a-seet-a-minophen",
        "electrocardiogram" to "electro-cardio-gram",
        "echocardiography" to "echo-cardiography",
        "hyperlipidemia" to "hyper-lipidemia",
        "arrhythmia" to "a-ryth-mia",
        "atherosclerosis" to "athero-sclerosis",
        "gastrointestinal" to "gastro-intestinal",
        "ibuprofen" to "eye-byoo-pro-fen",
        "metformin" to "met-formin"
    )

    override fun process(text: String): String {
        var processedText = text
        for ((medicalTerm, phoneticSpelling) in dictionary) {
            // Match whole words or phrases case-insensitively using word boundaries (\b)
            val pattern = "\\b${Pattern.quote(medicalTerm)}\\b"
            processedText = processedText.replace(pattern.toRegex(RegexOption.IGNORE_CASE), phoneticSpelling)
        }
        return processedText
    }
}
