package com.ieltsprep.generation;

import java.util.List;

public record Verdict(boolean pass, List<String> reasons, String notes) {

    public static Verdict pass(String notes) {
        return new Verdict(true, List.of(), notes);
    }

    public static Verdict fail(List<String> reasons, String notes) {
        return new Verdict(false, reasons, notes);
    }
}
