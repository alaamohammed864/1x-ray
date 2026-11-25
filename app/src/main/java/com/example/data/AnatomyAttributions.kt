package com.example.data

import com.example.model.AnatomySystem

data class AnatomyAttribution(
    val source: String,
    val license: String,
    val licenseBadgeColorHex: Long,
    val usageNote: String,
    val citationOrUrl: String,
    val hasShareAlikeWarning: Boolean = false,
)

data class MappedGlbNode(
    val entity: Int,
    val rawName: String,
    val arabicName: String,
    val system: AnatomySystem,
)

object AnatomyAttributions {
    val entries: List<AnatomyAttribution> = listOf(
        AnatomyAttribution(
            source = "HuBMAP Human Reference Atlas (HRA)",
            license = "CC BY 4.0",
            licenseBadgeColorHex = 0xFF2ED573,
            usageNote = "أعضاء مرجعية عالية الدقة بمقياس العضو ومفهرسة بترميز UBERON لكلا الجنسين.",
            citationOrUrl = "https://humanatlas.io/3d-reference-library",
        ),
        AnatomyAttribution(
            source = "Visible Human Project — US NLM",
            license = "Public Domain",
            licenseBadgeColorHex = 0xFF5FD0FF,
            usageNote = "بيانات المتبرعين الأساسية المرجعية التي بُنيت عليها نماذج HRA.",
            citationOrUrl = "U.S. National Library of Medicine",
        ),
        AnatomyAttribution(
            source = "BodyParts3D — DBCLS",
            license = "CC BY 4.0 / CC BY-SA 2.1 JP",
            licenseBadgeColorHex = 0xFFFFCF6B,
            usageNote = "أطلس تشريحي مفهرس بـ FMA (متبرع TARO)؛ الإصدارات الأحدث CC BY 4.0 والأقدم CC BY-SA 2.1 JP.",
            citationOrUrl = "BodyParts3D, © DBCLS — https://dbarchive.biosciencedbc.jp/en/bodyparts3d/",
            hasShareAlikeWarning = true,
        ),
        AnatomyAttribution(
            source = "Z-Anatomy (Gauthier Kervyn & Marcin Zieliński)",
            license = "CC BY-SA 4.0",
            licenseBadgeColorHex = 0xFFFFCF6B,
            usageNote = "أطلس العضلات والعظام والأعصاب والأوعية والأحشاء؛ ملفات النماذج معزولة عن الشيفرة احترامًا لشرط ShareAlike.",
            citationOrUrl = "https://github.com/LluisV/Z-Anatomy",
            hasShareAlikeWarning = true,
        ),
        AnatomyAttribution(
            source = "AnatomyTOOL / Open3Dmodel — Leiden UMC",
            license = "CC BY-SA",
            licenseBadgeColorHex = 0xFFFFCF6B,
            usageNote = "مجسمات تشريحية تعليمية مراجعة من أساتذة التشريح بجامعات هولندا وبلجيكا.",
            citationOrUrl = "https://anatomytool.org/open3dmodel-about",
            hasShareAlikeWarning = true,
        ),
        AnatomyAttribution(
            source = "biv-me — University of Auckland",
            license = "Apache-2.0",
            licenseBadgeColorHex = 0xFF2ED573,
            usageNote = "نموذج القلب النابض بـ 25 طورًا قلبيًا (بيانات CARDIOHANCE بموافقة أخلاقية محلية).",
            citationOrUrl = "Cite: Dillon et al. 2026, Med Image Anal 114:104252 — https://github.com/UOA-Heart-Mechanics-Research/biv-me",
        ),
        AnatomyAttribution(
            source = "Healthy-Total-Body-CTs — TCIA",
            license = "CC BY 4.0",
            licenseBadgeColorHex = 0xFF2ED573,
            usageNote = "مجموعة مقاطع الجسم الأنثوي الكامل (Subject 003) بالمقاس الحقيقي.",
            citationOrUrl = "https://www.cancerimagingarchive.net/collection/healthy-total-body-cts/",
        ),
        AnatomyAttribution(
            source = "OpenEar — MED-EL / University of Bern",
            license = "CC BY 4.0",
            licenseBadgeColorHex = 0xFF2ED573,
            usageNote = "عظم الصدغ والأذن الداخلية بألوان تصويرية دقيقة من التقطيع المجهري.",
            citationOrUrl = "MED-EL / University of Bern (Zenodo)",
        ),
        AnatomyAttribution(
            source = "Arizona Eye Model — Schwiegerling",
            license = "Published Parameters",
            licenseBadgeColorHex = 0xFF5FD0FF,
            usageNote = "معادلات وأنصاف أقطار مخروطية منشورة للعين البشرية.",
            citationOrUrl = "https://doi.org/10.1364/JOSAA.14.002884",
        ),
        AnatomyAttribution(
            source = "Opening-Science / open-twin-xr",
            license = "MIT License",
            licenseBadgeColorHex = 0xFF5FD0FF,
            usageNote = "بنية الفحص الحر لملفات GLB وفصل التراخيص وعرض الطبقات والمصدر المفتوح.",
            citationOrUrl = "https://github.com/Opening-Science/open-twin-xr",
        ),
    )
}

object GlbNodeLabelMapper {

    private data class KeywordRule(
        val keywords: List<String>,
        val arabic: String,
        val system: AnatomySystem,
    )

    private val rules = listOf(
        KeywordRule(listOf("heart", "cor", "cardiac", "ventricle", "atrium", "myocard", "aorta", "biv"), "القلب والأوعية الكبرى", AnatomySystem.ORGANS),
        KeywordRule(listOf("lung", "pulmo", "bronch", "trachea"), "الرئتان والشعب الهوائية", AnatomySystem.ORGANS),
        KeywordRule(listOf("brain", "encephal", "cerebr", "cerebell", "cortex"), "الدماغ", AnatomySystem.ORGANS),
        KeywordRule(listOf("liver", "hepar", "hepatic", "gallbladder"), "الكبد والمرارة", AnatomySystem.ORGANS),
        KeywordRule(listOf("kidney", "renal", "nephr"), "الكليتان", AnatomySystem.ORGANS),
        KeywordRule(listOf("stomach", "gaster", "gastric", "esophag"), "المعدة والمريء", AnatomySystem.ORGANS),
        KeywordRule(listOf("intestin", "colon", "duodenum", "jejunum", "ileum", "bowel"), "الأمعاء", AnatomySystem.ORGANS),
        KeywordRule(listOf("spleen", "pancreas", "bladder"), "أعضاء البطن والحوض", AnatomySystem.ORGANS),
        KeywordRule(listOf("skull", "crani", "mandib", "maxilla", "frontal", "temporal", "parietal"), "الجمجمة والفك", AnatomySystem.BONES),
        KeywordRule(listOf("spine", "verteb", "sacrum", "coccyx", "cervical", "thoracic", "lumbar"), "العمود الفقري", AnatomySystem.BONES),
        KeywordRule(listOf("rib", "costa", "sternum", "clavic", "scapula"), "القفص الصدري والكتف", AnatomySystem.BONES),
        KeywordRule(listOf("pelvis", "ilium", "ischium", "pubis", "hip"), "الحوض", AnatomySystem.BONES),
        KeywordRule(listOf("humerus", "radius", "ulna", "carpal", "metacarpal", "phalan", "femur", "tibia", "fibula", "patella", "tarsal", "bone", "skeleton", "iskelet"), "الهيكل العظمي", AnatomySystem.BONES),
        KeywordRule(listOf("musc", "pectoral", "bicep", "tricep", "deltoid", "rectus", "glute", "quadricep", "hamstring", "gastroc", "trapezius", "latissimus", "tendon", "kas"), "العضلات والأوتار", AnatomySystem.MUSCLES),
        KeywordRule(listOf("lymph", "lenf", "nodus", "thymus", "tonsil", "cisterna", "thoracic_duct"), "العقد والأوعية اللمفاوية", AnatomySystem.ORGANS),
        KeywordRule(listOf("nerve", "nervus", "plexus", "sinir", "gangli", "medulla", "brachial", "sciatic", "vagus", "cranial"), "الأعصاب والعقد العصبية", AnatomySystem.ORGANS),
        KeywordRule(listOf("artery", "vein", "vascul", "dolasim", "damar"), "الأوعية الدموية", AnatomySystem.ORGANS),
    )

    fun mapNode(entity: Int, rawName: String?): MappedGlbNode? {
        val clean = rawName?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val lower = clean.lowercase()
        // Ignore generic root/camera/empty helper nodes
        if (lower in setOf("root", "scene", "armature", "camera", "light")) return null

        val matched = rules.firstOrNull { rule ->
            rule.keywords.any { kw -> lower.contains(kw) }
        }
        return MappedGlbNode(
            entity = entity,
            rawName = clean,
            arabicName = matched?.arabic ?: clean.replace('_', ' '),
            system = matched?.system ?: AnatomySystem.ALL,
        )
    }
}
