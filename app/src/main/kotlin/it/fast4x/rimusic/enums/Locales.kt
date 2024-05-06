package it.fast4x.rimusic.enums

enum class Languages {
    System,
    Afrikaans,
    Arabic,
    Bashkir,
    Catalan,
    Danish,
    English,
    Esperanto,
    ChineseSimplified,
    ChineseTraditional,
    Czech,
    Dutch,
    Finnish,
    French,
    German,
    Greek,
    Hebrew,
    Hindi,
    Hungarian,
    Italian,
    Indonesian,
    Japanese,
    Korean,
    Odia,
    Persian,
    Polish,
    PortugueseBrazilian,
    Portuguese,
    Romanian,
    //RomanianEmo,
    Russian,
    SerbianCyrillic,
    SerbianLatin,
    Sinhala,
    Spanish,
    Swedish,
    Telugu,
    Turkish,
    Ukrainian,
    Vietnamese;

    var code: String = "en"
        get() = when (this) {
            System -> "system"
            Afrikaans -> "af"
            Arabic -> "ar"
            Bashkir -> "ba"
            Catalan -> "ca"
            ChineseSimplified -> "zh-CN"
            ChineseTraditional -> "zh-TW"
            Danish -> "da"
            Dutch -> "nl"
            English -> "en"
            Esperanto -> "eo"
            Finnish -> "fi"
            Italian -> "it"
            Indonesian -> "in"
            Japanese -> "ja"
            Korean -> "ko"
            Czech -> "cs"
            German -> "de"
            Greek -> "el"
            Hebrew -> "he"
            Hindi -> "hi"
            Hungarian -> "hu"
            Spanish -> "es"
            French -> "fr"
            Odia -> "or"
            Persian -> "fa"
            Polish -> "pl"
            Portuguese -> "pt"
            PortugueseBrazilian -> "pt-BR"
            Romanian -> "ro"
            //RomanianEmo -> "ro-RO"
            Russian -> "ru"
            SerbianCyrillic -> "sr"
            SerbianLatin -> "sr-CS"
            Sinhala -> "si"
            Swedish -> "sv"
            Telugu -> "te"
            Turkish -> "tr"
            Ukrainian -> "uk"
            Vietnamese -> "vi"
        }


}