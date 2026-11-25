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
            source = "Z-Anatomy (Gauthier Kervyn & Marcin Zieliński) — عبر DrMuratAltun",
            license = "CC BY-SA 4.0",
            licenseBadgeColorHex = 0xFFFFCF6B,
            usageNote = "النماذج المدمجة السبعة: الهيكل العظمي (iskelet)، المفاصل والأربطة (eklem)، العضلات (kas)، الجهاز الدوري (dolasim)، الأعصاب (sinir)، اللمفاوي (lenf)، والأعضاء الداخلية (ic-organlar). ملفات GLB مفصولة عن الشيفرة احترامًا لشرط ShareAlike.",
            citationOrUrl = "https://github.com/LluisV/Z-Anatomy · https://github.com/DrMuratAltun/anatomi-simulatoru",
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
        // Lymphatic nodes & lymphoid organs (check before thyroid/parotid/etc.)
        KeywordRule(listOf("lymph", "lenf", "nodes", " node", "nodus", "thymus", "tonsil", "spleen", "cisterna", "thoracic duct"), "العقد والأعضاء اللمفاوية", AnatomySystem.ORGANS),
        // Ear ossicles, teeth, laryngeal/costal cartilages
        KeywordRule(listOf("incus", "malleus", "stapes"), "عظيمات السمع", AnatomySystem.BONES),
        KeywordRule(listOf("incisor", "canine", "premolar", "molar", "tooth", "teeth"), "الأسنان", AnatomySystem.BONES),
        KeywordRule(listOf("thyroid cartilage", "cricoid", "arytenoid", "corniculate"), "غضاريف الحنجرة", AnatomySystem.BONES),
        KeywordRule(listOf("costal cartilage"), "الغضاريف الضلعية", AnatomySystem.BONES),
        // Joints, ligaments, capsules, menisci, intervertebral discs (before nervous "nucleus" or vascular "capsule")
        KeywordRule(listOf("ligament", "meniscus", "labrum", "articular capsule", "articular disc", "interpubic disc", "nucleus pulposus", "anulus fibrosus", "symphysis", "interosseous membrane", "obturator membrane", "oblique cord", "zona orbicularis", "fat pad", "frenula", "cartilage"), "المفاصل والأربطة والغضاريف", AnatomySystem.BONES),
        // Nervous system (brain ventricles checked before heart ventricle; sympathetic trunk before vascular trunk)
        KeywordRule(listOf("brain", "encephal", "cerebr", "cerebell", "cortex", "pons", "medulla", "thalamus", "hypothalam", "hippocamp", "amygdala", "corpus callosum", "adenohypophysis", "neurohypophysis", "pineal", "lateral ventricle", "third ventricle", "fourth ventricle", "olive", "colliculus", "peduncle", "lemniscus", "internal capsule", "external capsule", "striatum", "putamen", "globus pallidus", "caudate", "substantia nigra", "fornix", "commissure", "septum pellucidum", "gyrus", "sulcus", "frontal lobe", "parietal lobe", "temporal lobe", "occipital lobe", "limbic", "insula", "vermis", "flocculus", "meninges", "dura mater", "pia mater", "arachnoid"), "الدماغ والجهاز العصبي المركزي", AnatomySystem.ORGANS),
        KeywordRule(listOf("nerve", "nervus", "plexus", "sinir", "gangli", "spinal cord", "central canal", "cauda equina", "filum terminale", "sympathetic", "parasympathetic", "chorda tympani", "chiasm", "optic nerve", "optic tract", "olfactory", "oculomotor", "trochlear", "trigeminal", "abducens", "vestibulocochlear", "glossopharyngeal", "hypoglossal", "nucleus", "fasciculus", "decussation", "brachial", "sciatic", "vagus", "cranial", "tract", "ramus", "rami", "root"), "الأعصاب والعقد العصبية", AnatomySystem.ORGANS),
        // Circulatory / Cardiovascular
        KeywordRule(listOf("heart", "cor ", "cardiac", "ventricle", "atrium", "myocard", "pericard", "biv"), "القلب", AnatomySystem.ORGANS),
        KeywordRule(listOf("aorta", "aortic", "artery", "arteries", "vein", "veins", "vena ", "carotid", "jugular", "coronary", "iliac", "subclavian", "femoral", "pulmonary trunk", "palmar arch", "plantar arch", "venous arch", "arterial arch", "branches", "branch", "vascul", "dolasim", "damar", "sinus", "trunk"), "الأوعية الدموية", AnatomySystem.ORGANS),
        // Respiratory
        KeywordRule(listOf("lung", "pulmo", "bronch", "trachea", "pleura", "epiglottis", "larynx", "nasal cavity"), "الجهاز التنفسي", AnatomySystem.ORGANS),
        // Digestive & Hepatobiliary
        KeywordRule(listOf("liver", "hepar", "hepatic", "gallbladder", "bile duct"), "الكبد والمرارة", AnatomySystem.ORGANS),
        KeywordRule(listOf("stomach", "gaster", "gastric", "oesophag", "esophag", "pharynx", "tongue", "palate", "uvula", "gingiva", "parotid", "submandibular", "sublingual"), "المعدة والمريء والفم", AnatomySystem.ORGANS),
        KeywordRule(listOf("pancreas", "pancreatic"), "البنكرياس", AnatomySystem.ORGANS),
        KeywordRule(listOf("intestin", "colon", "duodenum", "jejunum", "ileum", "appendix", "omentum", "mesocolon", "meso-appendix", "taenia", "bowel", "rectum"), "الأمعاء والصفاق", AnatomySystem.ORGANS),
        // Urinary & Endocrine & Reproductive
        KeywordRule(listOf("kidney", "renal", "nephr", "ureter", "urethra", "urinary bladder"), "الجهاز البولي والكلى", AnatomySystem.ORGANS),
        KeywordRule(listOf("thyroid", "parathyroid", "suprarenal", "adrenal"), "الغدد الصماء", AnatomySystem.ORGANS),
        KeywordRule(listOf("prostate", "testis", "epididymis", "seminal", "ductus deferens", "ejaculatory", "penis", "ovary", "uterus"), "الجهاز التناسلي", AnatomySystem.ORGANS),
        // Skeletal
        KeywordRule(listOf("skull", "crani", "mandib", "maxilla", "frontal", "temporal", "parietal", "occipital", "sphenoid", "ethmoid", "zygomatic", "lacrimal", "palatine", "vomer", "hyoid", "nasal bone", "concha"), "الجمجمة والفك", AnatomySystem.BONES),
        KeywordRule(listOf("spine", "verteb", "atlas", "axis", "sacrum", "coccyx", "cervical", "thoracic", "lumbar"), "العمود الفقري", AnatomySystem.BONES),
        KeywordRule(listOf("rib", "costa", "sternum", "manubrium", "xiphoid", "clavic", "scapula"), "القفص الصدري والكتف", AnatomySystem.BONES),
        KeywordRule(listOf("pelvis", "ilium", "ischium", "pubis", "hip bone"), "عظام الحوض", AnatomySystem.BONES),
        KeywordRule(listOf("humerus", "radius", "ulna", "scaphoid", "lunate", "triquetrum", "pisiform", "trapezium", "trapezoid", "capitate", "hamate", "carpal", "metacarpal", "phalanx", "phalan"), "عظام الطرف العلوي واليد", AnatomySystem.BONES),
        KeywordRule(listOf("femur", "tibia", "fibula", "patella", "calcaneus", "talus", "navicular", "cuboid", "cuneiform", "tarsal", "metatarsal", "sesamoid"), "عظام الطرف السفلي والقدم", AnatomySystem.BONES),
        KeywordRule(listOf("bone", "skeleton", "iskelet"), "الهيكل العظمي", AnatomySystem.BONES),
        // Muscular (including deep/facial/pharyngeal/pelvic muscles, bursae & aponeuroses)
        KeywordRule(listOf("musc", "pectoral", "bicep", "tricep", "deltoid", "rectus", "oblique", "transversus", "glute", "quadricep", "vastus", "sartorius", "gracilis", "adductor", "abductor", "hamstring", "gastroc", "soleus", "tibialis", "trapezius", "latissimus", "rhomboid", "serratus", "sternocleidomastoid", "masseter", "temporalis", "orbicularis", "diaphragm", "intercostal", "flexor", "extensor", "pronator", "supinator", "tendon", "tendinous", "aponeurosis", "fascia", "retinaculum", "linea alba", "constrictor", "platysma", "levator", "depressor", "sphincter", "tensor", "corrugator", "procerus", "nasalis", "buccinator", "risorius", "mentalis", "zygomaticus", "genioglossus", "hyoglossus", "styloglossus", "mylohyoid", "geniohyoid", "omohyoid", "sternohyoid", "thyrohyoid", "sternothyroid", "scalene", "longus", "splenius", "semispinalis", "spinalis", "longissimus", "iliocostalis", "multifidus", "rotatores", "quadratus", "psoas", "iliacus", "piriformis", "obturator", "gemellus", "pectineus", "popliteus", "plantaris", "peroneus", "fibularis", "lumbrical", "interossei", "opponens", "palmaris", "brachioradialis", "anconeus", "coracobrachialis", "teres", "infraspinatus", "supraspinatus", "subscapularis", "cremaster", "dartos", "bulbospongiosus", "ischiocavernosus", "perineal", "superior tarsus", "inferior tarsus", "bursa", "kas"), "العضلات والأوتار", AnatomySystem.MUSCLES),
    )

    fun mapNode(entity: Int, rawName: String?): MappedGlbNode? {
        val clean = rawName?.trim().orEmpty()
        if (clean.isEmpty() || clean.startsWith("?")) return null
        val lower = clean.lowercase()
        // Ignore generic root/camera/empty/mesh helper nodes
        if (lower in setOf("root", "scene", "armature", "camera", "light", "mesh", "circle")) return null

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
