package com.example.data

import com.example.model.AnatomyPart
import com.example.model.BodyRegion
import com.example.model.Pt
import com.example.model.RenderContextData
import kotlin.math.hypot

object AnatomyCatalog {

    private fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)
    private fun mid(a: Pt?, b: Pt?): Pt? = if (a != null && b != null) Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f) else null
    private fun lerp(a: Pt, b: Pt, t: Float): Pt = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun genderSide(masculine: Boolean, right: Boolean): String =
        if (masculine) (if (right) "الأيمن" else "الأيسر") else (if (right) "اليمنى" else "اليسرى")

    private fun torsoPart(
        a: Float,
        b: Float,
        radiusFrac: Float,
        depthFrac: Float = 0f,
    ): (RenderContextData) -> Pair<Pt, Float>? = { ctx ->
        ctx.torso?.let { tf ->
            tf.project(a, b, depthFrac) to (tf.projectedWidth() * radiusFrac)
        }
    }

    private fun refWidth(ctx: RenderContextData): Float =
        ctx.torso?.wf ?: ctx.shoulderWidth.takeIf { it > 0f } ?: (ctx.headSize * 1.6f)

    val parts: List<AnatomyPart> = buildList {
        // Head & Face
        add(AnatomyPart("الجمجمة", "Skull", "تتكوّن الجمجمة من 22 عظمة (8 للقحف و14 للوجه).", false, BodyRegion.HEAD, 1f) { ctx ->
            ctx.headCenter?.let { it to ctx.headSize * 0.45f }
        })
        add(AnatomyPart("الجبهة", "Frontal bone", "العظم الجبهي يحمي الجزء الأمامي من الدماغ.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.forehead to ctx.headSize * 0.14f }
        })
        add(AnatomyPart("العين اليمنى", "Right eye", "تحرّك كل عين ست عضلات خارجية.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.rightEye to ctx.headSize * 0.07f }
        })
        add(AnatomyPart("العين اليسرى", "Left eye", "تحرّك كل عين ست عضلات خارجية.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.leftEye to ctx.headSize * 0.07f }
        })
        add(AnatomyPart("الأنف", "Nose", "الجزء العلوي من الأنف عظمي والسفلي غضروفي.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.noseTip to ctx.headSize * 0.08f }
        })
        add(AnatomyPart("الخد الأيمن", "Zygomatic bone", "عظم الوجنة هو ما يشكّل بروز الخد.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.rightCheek to ctx.headSize * 0.09f }
        })
        add(AnatomyPart("الخد الأيسر", "Zygomatic bone", "عظم الوجنة هو ما يشكّل بروز الخد.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.leftCheek to ctx.headSize * 0.09f }
        })
        add(AnatomyPart("الفم", "Mouth", "في فم البالغ 32 سنًّا.", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.mouthCenter to ctx.headSize * 0.10f }
        })
        add(AnatomyPart("الذقن والفك السفلي", "Mandible", "الفك السفلي هو العظم المتحرك الوحيد في الجمجمة (عدا عظيمات الأذن).", false, BodyRegion.HEAD) { ctx ->
            ctx.face?.let { it.chin to ctx.headSize * 0.12f }
        })
        add(AnatomyPart("الأذن اليمنى", "Right ear", "في الأذن الوسطى أصغر عظام الجسم.", false, BodyRegion.HEAD) { ctx ->
            ctx.pv(8)?.let { it to (ctx.headSize.takeIf { h -> h > 0f } ?: (ctx.shoulderWidth * 0.5f).takeIf { w -> w > 0f } ?: 30f) * 0.10f }
        })
        add(AnatomyPart("الأذن اليسرى", "Left ear", "في الأذن الوسطى أصغر عظام الجسم.", false, BodyRegion.HEAD) { ctx ->
            ctx.pv(7)?.let { it to (ctx.headSize.takeIf { h -> h > 0f } ?: (ctx.shoulderWidth * 0.5f).takeIf { w -> w > 0f } ?: 30f) * 0.10f }
        })

        // Neck
        add(AnatomyPart("الرقبة", "Neck", "في الرقبة 7 فقرات عنقية.", true, BodyRegion.NECK, 2f) { ctx ->
            val a = ctx.pv(0) ?: ctx.headCenter
            val m = mid(ctx.pv(11), ctx.pv(12))
            if (a != null && m != null) lerp(a, m, 0.6f) to (refWidth(ctx) * 0.17f) else null
        })

        // Chest & Torso
        add(AnatomyPart("القفص الصدري", "Rib cage", "القفص الصدري فيه 12 زوجًا من الأضلاع.", true, BodyRegion.CHEST, 3f, locator = torsoPart(0.5f, 0.22f, 0.33f)))
        add(AnatomyPart("عظم القص", "Sternum", "تتصل غضاريف الأضلاع العلوية بعظم القص في الأمام.", true, BodyRegion.CHEST, 0f, "f", torsoPart(0.5f, 0.20f, 0.06f, 0.27f)))
        add(AnatomyPart("القلب", "Heart", "القلب بحجم قبضة اليد تقريبًا ويميل قليلًا إلى اليسار.", true, BodyRegion.CHEST, 4f, locator = torsoPart(0.62f, 0.26f, 0.10f, 0.08f)))
        add(AnatomyPart("الرئة اليمنى", "Right lung", "الرئة اليمنى فيها ثلاثة فصوص.", true, BodyRegion.CHEST, 5f, locator = torsoPart(0.30f, 0.20f, 0.17f, -0.02f)))
        add(AnatomyPart("الرئة اليسرى", "Left lung", "الرئة اليسرى فيها فصّان.", true, BodyRegion.CHEST, 6f, locator = torsoPart(0.72f, 0.20f, 0.15f, -0.02f)))
        add(AnatomyPart("الحجاب الحاجز", "Diaphragm", "الحجاب الحاجز عضلة قبّة تفصل الصدر عن البطن وتساعد على التنفس.", true, BodyRegion.CHEST, locator = torsoPart(0.50f, 0.33f, 0.30f)))
        add(AnatomyPart("العضلة الصدرية الكبرى", "Pectoralis major", "تقرّب العضلة الصدرية الكبرى الذراع من الجسم وتدوّرها إلى الداخل.", true, BodyRegion.CHEST, 0f, "f", torsoPart(0.50f, 0.14f, 0.30f, 0.25f)))

        // Abdomen
        add(AnatomyPart("الكبد", "Liver", "الكبد أكبر عضو داخلي في الجسم.", true, BodyRegion.ABDOMEN, 7f, locator = torsoPart(0.30f, 0.46f, 0.17f, 0.03f)))
        add(AnatomyPart("المعدة", "Stomach", "تهضم المعدة الطعام بمساعدة الحمض والإنزيمات.", true, BodyRegion.ABDOMEN, 8f, locator = torsoPart(0.68f, 0.46f, 0.12f, 0.05f)))
        add(AnatomyPart("الطحال", "Spleen", "يقع الطحال في أعلى يسار البطن ويشارك في تصفية الدم.", true, BodyRegion.ABDOMEN, locator = torsoPart(0.82f, 0.42f, 0.07f, -0.12f)))
        add(AnatomyPart("الأمعاء", "Intestines", "طول الأمعاء الدقيقة عدة أمتار.", true, BodyRegion.ABDOMEN, 10f, locator = torsoPart(0.50f, 0.74f, 0.25f, 0.05f)))
        add(AnatomyPart("العضلة المستقيمة البطنية", "Rectus abdominis", "العضلة المستقيمة البطنية تثني الجذع وهي التي تظهر كحزم مقسّمة عند الرياضيين.", true, BodyRegion.ABDOMEN, 0f, "f", torsoPart(0.50f, 0.55f, 0.14f, 0.27f)))

        // Back
        add(AnatomyPart("الكلية اليمنى", "Right kidney", "في الجسم كليتان تقعان خلف تجويف البطن على جانبي العمود الفقري.", true, BodyRegion.BACK, 8.6f, locator = torsoPart(0.36f, 0.50f, 0.06f, -0.17f)))
        add(AnatomyPart("الكلية اليسرى", "Left kidney", "في الجسم كليتان تقعان خلف تجويف البطن على جانبي العمود الفقري.", true, BodyRegion.BACK, 8.7f, locator = torsoPart(0.64f, 0.50f, 0.06f, -0.17f)))
        add(AnatomyPart("العمود الفقري", "Spine", "فيه 7 فقرات عنقية و12 صدرية و5 قطنية.", true, BodyRegion.BACK, 9f, locator = torsoPart(0.50f, 0.50f, 0.07f, -0.22f)))
        add(AnatomyPart("اللوح الأيمن", "Right scapula", "لوح الكتف عظم مثلثي مسطح في أعلى الظهر يتمفصل مع عظم العضد.", true, BodyRegion.BACK, 5.5f, "b", torsoPart(0.27f, 0.14f, 0.10f, -0.27f)))
        add(AnatomyPart("اللوح الأيسر", "Left scapula", "لوح الكتف عظم مثلثي مسطح في أعلى الظهر يتمفصل مع عظم العضد.", true, BodyRegion.BACK, 5.6f, "b", torsoPart(0.73f, 0.14f, 0.10f, -0.27f)))
        add(AnatomyPart("العضلة شبه المنحرفة", "Trapezius", "تمتد العضلة شبه المنحرفة من قاعدة الجمجمة إلى منتصف الظهر وتحرّك لوح الكتف.", true, BodyRegion.BACK, 4.5f, "b", torsoPart(0.50f, 0.14f, 0.25f, -0.25f)))
        add(AnatomyPart("العضلة الظهرية العريضة", "Latissimus dorsi", "العضلة الظهرية العريضة من أكبر عضلات الظهر وتسحب الذراع نحو الجسم.", true, BodyRegion.BACK, 6.5f, "b", torsoPart(0.50f, 0.45f, 0.30f, -0.25f)))

        // Pelvis
        add(AnatomyPart("العجز", "Sacrum", "العجز عظم مثلثي ينتج عن التحام خمس فقرات.", true, BodyRegion.PELVIS, locator = torsoPart(0.50f, 0.97f, 0.07f, -0.17f)))
        add(AnatomyPart("العضلة الألوية الكبرى", "Gluteus maximus", "العضلة الألوية الكبرى من أكبر عضلات الجسم.", true, BodyRegion.PELVIS, 6.6f, "b", torsoPart(0.50f, 1.08f, 0.30f, -0.20f)))
        add(AnatomyPart("المثانة", "Bladder", "تخزّن المثانة البول قبل خروجه.", true, BodyRegion.PELVIS, locator = torsoPart(0.50f, 0.97f, 0.08f, 0.06f)))
        add(AnatomyPart("الحوض", "Pelvis", "الحوض عظمتا ورك ترتبطان بالعجز.", true, BodyRegion.PELVIS, 11f) { ctx ->
            mid(ctx.pv(23), ctx.pv(24))?.let { it to (refWidth(ctx) * 0.25f) }
        })

        // Arms & Hands (Pose 12,14,16 right; 11,13,15 left; Pose hands: 18/20/22 right, 17/19/21 left)
        data class ArmSpec(val right: Boolean, val en: String, val sh: Int, val el: Int, val wr: Int, val pk: Int, val ix: Int, val th: Int, val priBase: Int)
        listOf(
            ArmSpec(true, "Right", 12, 14, 16, 18, 20, 22, 0),
            ArmSpec(false, "Left", 11, 13, 15, 17, 19, 21, 1),
        ).forEach { s ->
            val mS = genderSide(true, s.right)
            val fS = genderSide(false, s.right)
            add(AnatomyPart("الكتف $mS", "${s.en} shoulder", "الكتف من أكثر مفاصل الجسم حركة.", false, BodyRegion.SHOULDERS) { ctx ->
                ctx.pv(s.sh)?.let { it to (refWidth(ctx) * 0.15f) }
            })
            add(AnatomyPart("العضد $mS", "${s.en} humerus", "العضد أطول عظام الذراع.", false, BodyRegion.ARMS, 12f + s.priBase) { ctx ->
                mid(ctx.pv(s.sh), ctx.pv(s.el))?.let { it to (refWidth(ctx) * 0.13f) }
            })
            add(AnatomyPart("المرفق $mS", "${s.en} elbow", "المرفق يربط العضد بعظمتي الساعد: الكعبرة والزند.", false, BodyRegion.ARMS) { ctx ->
                ctx.pv(s.el)?.let { it to (refWidth(ctx) * 0.10f) }
            })
            add(AnatomyPart("الساعد $mS", "${s.en} forearm", "في الساعد عظمتان: الكعبرة والزند.", false, BodyRegion.FOREARMS, 14f + s.priBase) { ctx ->
                mid(ctx.pv(s.el), ctx.pv(s.wr))?.let { it to (refWidth(ctx) * 0.10f) }
            })
            add(AnatomyPart("المعصم $mS", "${s.en} wrist", "في المعصم 8 عظام صغيرة تُسمى الرسغ.", false, BodyRegion.FOREARMS) { ctx ->
                ctx.pv(s.wr)?.let { it to (refWidth(ctx) * 0.07f) }
            })
            add(AnatomyPart("كف اليد $fS", "${s.en} palm", "في اليد الواحدة 27 عظمة.", true, BodyRegion.HANDS, 16f + s.priBase) { ctx ->
                val wr = ctx.pv(s.wr)
                val ix = ctx.pv(s.ix)
                val pk = ctx.pv(s.pk)
                val palmCenter = mid(wr, mid(ix, pk) ?: ix ?: pk)
                if (palmCenter != null && wr != null) {
                    val handSpan = maxOf(dist(wr, palmCenter) * 1.4f, refWidth(ctx) * 0.08f)
                    palmCenter to handSpan
                } else null
            })
            add(AnatomyPart("الإبهام ${genderSide(true, s.right)}", "${s.en} thumb", "للإبهام سلاميتان فقط.", true, BodyRegion.HANDS) { ctx ->
                val p = ctx.pv(s.th) ?: return@AnatomyPart null
                p to (refWidth(ctx) * 0.05f)
            })
            add(AnatomyPart("السبابة ${genderSide(false, s.right)}", "${s.en} index finger", "في السبابة ثلاث سلاميات.", true, BodyRegion.HANDS) { ctx ->
                val p = ctx.pv(s.ix) ?: return@AnatomyPart null
                p to (refWidth(ctx) * 0.05f)
            })
            add(AnatomyPart("الخنصر ${genderSide(true, s.right)}", "${s.en} pinky finger", "الخنصر أصغر الأصابع.", true, BodyRegion.HANDS) { ctx ->
                val p = ctx.pv(s.pk) ?: return@AnatomyPart null
                p to (refWidth(ctx) * 0.05f)
            })
        }

        // Legs & Feet (24,26,28,30,32 right; 23,25,27,29,31 left)
        data class LegSpec(val right: Boolean, val en: String, val hp: Int, val kn: Int, val ak: Int, val hl: Int, val toe: Int, val priBase: Int)
        listOf(
            LegSpec(true, "Right", 24, 26, 28, 30, 32, 0),
            LegSpec(false, "Left", 23, 25, 27, 29, 31, 1),
        ).forEach { s ->
            val mS = genderSide(true, s.right)
            val fS = genderSide(false, s.right)
            add(AnatomyPart("مفصل الورك $mS", "${s.en} hip joint", "مفصل الورك من نوع الكرة والحُقّ.", false, BodyRegion.THIGHS) { ctx ->
                ctx.pv(s.hp)?.let { it to (refWidth(ctx) * 0.13f) }
            })
            add(AnatomyPart("الفخذ $mS", "${s.en} femur", "عظم الفخذ أطول وأقوى عظام الجسم.", false, BodyRegion.THIGHS, 18f + s.priBase) { ctx ->
                mid(ctx.pv(s.hp), ctx.pv(s.kn))?.let { it to (refWidth(ctx) * 0.20f) }
            })
            add(AnatomyPart("الركبة $fS", "${s.en} knee", "الرضفة عظمة صغيرة تحمي مفصل الركبة من الأمام.", false, BodyRegion.THIGHS, 20f + s.priBase) { ctx ->
                ctx.pv(s.kn)?.let { it to (refWidth(ctx) * 0.12f) }
            })
            add(AnatomyPart("الساق $fS", "${s.en} leg", "في الساق عظمتان: الظنبوب والشظية.", false, BodyRegion.SHINS, 22f + s.priBase) { ctx ->
                mid(ctx.pv(s.kn), ctx.pv(s.ak))?.let { it to (refWidth(ctx) * 0.14f) }
            })
            add(AnatomyPart("الكاحل $mS", "${s.en} ankle", "عظم الكاحل ينقل وزن الجسم من الساق إلى القدم.", false, BodyRegion.SHINS) { ctx ->
                ctx.pv(s.ak)?.let { it to (refWidth(ctx) * 0.09f) }
            })
            add(AnatomyPart("العقب $mS", "${s.en} heel", "عظم العقب أكبر عظام القدم.", false, BodyRegion.FEET) { ctx ->
                ctx.pv(s.hl)?.let { it to (refWidth(ctx) * 0.08f) }
            })
            add(AnatomyPart("مقدمة القدم $fS", "${s.en} forefoot", "في القدم الواحدة 26 عظمة.", true, BodyRegion.FEET) { ctx ->
                ctx.pv(s.toe)?.let { it to (refWidth(ctx) * 0.09f) }
            })
        }
    }

    fun normalizeArabic(input: String): String {
        return input
            .replace(Regex("[\\u064B-\\u065F\\u0640]"), "")
            .replace(Regex("[أإآ]"), "ا")
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace(Regex("(^|\\s)ال"), "$1")
            .trim()
            .lowercase()
    }
}
