package com.wjp.wdada.model.dto.question;

import lombok.Data;

import java.util.List;


@Data
public class Question {
    private String title;
    private List<Option> options;

    public static class Option {
        private String key;
        private String value;

        // Getters and Setters
    }

    // Getters and Setters
}
