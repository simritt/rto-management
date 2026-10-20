package com.rto.web.util;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component("ui")
public class UiHelper {
    public List<String> list(String... items) {
        return Arrays.asList(items);
    }
}
