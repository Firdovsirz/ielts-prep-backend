package com.ieltsprep.marking;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps British and American spellings of the same word to one canonical (British) form so either is accepted.
 * Only regular, well-known alternations are handled; anything else — including genuine misspellings — is left
 * unchanged and therefore marked wrong, as in the real test.
 */
final class SpellingVariants {

    private SpellingVariants() {}

    /** American (or alternative) spelling → British canonical form. */
    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("color", "colour"), Map.entry("favor", "favour"), Map.entry("favorite", "favourite"),
            Map.entry("honor", "honour"), Map.entry("labor", "labour"), Map.entry("neighbor", "neighbour"),
            Map.entry("neighborhood", "neighbourhood"), Map.entry("behavior", "behaviour"), Map.entry("humor", "humour"),
            Map.entry("harbor", "harbour"), Map.entry("rumor", "rumour"), Map.entry("flavor", "flavour"),
            Map.entry("vapor", "vapour"), Map.entry("odor", "odour"), Map.entry("vigor", "vigour"),
            Map.entry("endeavor", "endeavour"), Map.entry("armor", "armour"), Map.entry("savior", "saviour"),
            Map.entry("center", "centre"), Map.entry("theater", "theatre"), Map.entry("meter", "metre"),
            Map.entry("kilometer", "kilometre"), Map.entry("centimeter", "centimetre"), Map.entry("millimeter", "millimetre"),
            Map.entry("liter", "litre"), Map.entry("fiber", "fibre"), Map.entry("somber", "sombre"),
            Map.entry("meager", "meagre"), Map.entry("caliber", "calibre"), Map.entry("luster", "lustre"),
            Map.entry("program", "programme"), Map.entry("catalog", "catalogue"), Map.entry("dialog", "dialogue"),
            Map.entry("analog", "analogue"), Map.entry("gray", "grey"), Map.entry("tire", "tyre"),
            Map.entry("jewelry", "jewellery"), Map.entry("aluminum", "aluminium"), Map.entry("plow", "plough"),
            Map.entry("mold", "mould"), Map.entry("molding", "moulding"), Map.entry("smolder", "smoulder"),
            Map.entry("pajamas", "pyjamas"), Map.entry("defense", "defence"), Map.entry("offense", "offence"),
            Map.entry("license", "licence"), Map.entry("pretense", "pretence"), Map.entry("practise", "practice"),
            Map.entry("enroll", "enrol"), Map.entry("enrollment", "enrolment"), Map.entry("fulfill", "fulfil"),
            Map.entry("fulfillment", "fulfilment"), Map.entry("skillful", "skilful"), Map.entry("installment", "instalment"),
            Map.entry("willful", "wilful"), Map.entry("cozy", "cosy"), Map.entry("mustache", "moustache"),
            Map.entry("maneuver", "manoeuvre"), Map.entry("esthetic", "aesthetic"), Map.entry("pediatric", "paediatric"),
            Map.entry("encyclopedia", "encyclopaedia"), Map.entry("archeology", "archaeology"),
            Map.entry("archeological", "archaeological"), Map.entry("fetus", "foetus"), Map.entry("estrogen", "oestrogen"),
            Map.entry("anemia", "anaemia"), Map.entry("anesthetic", "anaesthetic"), Map.entry("diarrhea", "diarrhoea"),
            Map.entry("hemoglobin", "haemoglobin"), Map.entry("leukemia", "leukaemia"), Map.entry("orthopedic", "orthopaedic"),
            Map.entry("airplane", "aeroplane"), Map.entry("yogurt", "yoghurt"), Map.entry("donut", "doughnut"),
            Map.entry("sulfur", "sulphur"), Map.entry("math", "maths"), Map.entry("mom", "mum"), Map.entry("gage", "gauge"), Map.entry("traveler", "traveller"), Map.entry("jeweler", "jeweller"), Map.entry("counselor", "counsellor"),
            Map.entry("woolen", "woollen"), Map.entry("carburetor", "carburettor"), Map.entry("judgment", "judgement"),
            Map.entry("acknowledgment", "acknowledgement"), Map.entry("ageing", "aging"), Map.entry("aging", "aging"),
            Map.entry("sceptical", "skeptical"), Map.entry("skeptical", "skeptical"), Map.entry("skeptic", "skeptic"),
            Map.entry("sceptic", "skeptic"));

    private static final Pattern IZE = Pattern.compile("^([a-z]{3,})iz(e|es|ed|ing|ation|ations|er|ers)$");
    private static final Pattern YZE = Pattern.compile("^([a-z]{2,})yz(e|es|ed|ing|er|ers)$");
    /** Verbs whose final l doubles in British English only (travelled/traveled). */
    private static final java.util.Set<String> DOUBLE_L_STEMS = java.util.Set.of("travel", "cancel", "model", "label",
            "level", "signal", "fuel", "total", "dial", "marvel", "jewel", "counsel", "channel", "tunnel", "panel",
            "equal", "quarrel", "rival", "shovel", "pedal", "duel", "funnel", "libel", "yodel");
    private static final Pattern DOUBLE_L = Pattern.compile("^([a-z]+?)(l{1,2})(ed|ing|er|ers)$");
    private static final Pattern PLURAL = Pattern.compile("^(.+?)(s|es)$");

    /** Canonical British spelling of one lower-case token. */
    static String canonical(String token) {
        if (token.length() < 4) {
            return token;
        }
        String direct = WORDS.get(token);
        if (direct != null) {
            return direct;
        }
        // plural/possessive forms of listed words: colors → colours, centers → centres
        Matcher plural = PLURAL.matcher(token);
        if (plural.matches() && WORDS.containsKey(plural.group(1))) {
            return WORDS.get(plural.group(1)) + plural.group(2);
        }
        Matcher m = IZE.matcher(token);
        if (m.matches()) {
            return m.group(1) + "is" + m.group(2);
        }
        m = YZE.matcher(token);
        if (m.matches()) {
            return m.group(1) + "ys" + m.group(2);
        }
        // traveled/travelled, canceling/cancelling → the doubled-l (British) form
        m = DOUBLE_L.matcher(token);
        if (m.matches() && DOUBLE_L_STEMS.contains(m.group(1) + "l")) {
            return m.group(1) + "ll" + m.group(3);
        }
        return token;
    }
}
