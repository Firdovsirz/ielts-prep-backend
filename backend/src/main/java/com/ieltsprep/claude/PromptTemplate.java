package com.ieltsprep.claude;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A prompt loaded from {@code prompts/<name>.md}: YAML front matter (route, schema, cached context files)
 * followed by a {@code # System} and a {@code # User} section. {@code {{var}}} placeholders are substituted
 * at call time; keep the system section free of per-call variables so its prefix stays cacheable.
 */
public record PromptTemplate(
        String name,
        String description,
        ModelRoute route,
        String schema,
        List<String> context,
        Long maxTokens,
        String system,
        String user) {

    /** Variable names start with a letter, so gap markers such as {{7}} in prompt text are never substituted. */
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}");

    public String renderSystem(Map<String, ?> vars) {
        return render(system, vars);
    }

    public String renderUser(Map<String, ?> vars) {
        return render(user, vars);
    }

    /** Substitutes supplied variables; placeholders with no supplied value (e.g. the literal {{n}}) are left as written. */
    static String render(String template, Map<String, ?> vars) {
        Matcher m = VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            boolean supplied = vars != null && vars.containsKey(m.group(1));
            Object value = supplied ? vars.get(m.group(1)) : null;
            m.appendReplacement(out, Matcher.quoteReplacement(supplied ? (value == null ? "" : String.valueOf(value)) : m.group()));
        }
        m.appendTail(out);
        return out.toString().trim();
    }
}
